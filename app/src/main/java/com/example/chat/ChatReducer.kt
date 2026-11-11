package com.example.chat

import com.example.data.StoredRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class ChatReducer(private val idPrefix: String = "c") {

    private enum class Kind { Thought, Text, Other }

    private val list = ArrayList<ChatMessage>()
    private val kinds = HashMap<Int, Kind>()
    private var counter = 0

    var contextTokens = 0L
        private set

    val messages: List<ChatMessage> get() = list.toList()

    val hasUserMessage: Boolean get() = list.any { it.role == Role.User }

    val streaming: Boolean get() = list.lastOrNull()?.let { it.role == Role.Assistant && it.streaming } == true

    private fun newId() = "$idPrefix${counter++}"

    fun clear() {
        list.clear()
        kinds.clear()
        contextTokens = 0
    }

    fun setContextTokens(tokens: Long) {
        contextTokens = tokens
    }

    fun load(records: List<StoredRecord>) {
        list.clear()
        kinds.clear()
        records.sortedBy { it.seq }.forEach { applyRecord(it.record) }
    }

    fun prepend(records: List<StoredRecord>) {
        val older = ArrayList(ChatReducer("p${counter++}-").apply { load(records) }.messages)
        val tail = older.lastOrNull()
        val head = list.firstOrNull()
        if (tail != null && head != null && tail.role == Role.Assistant && head.role == Role.Assistant) {
            older.removeAt(older.lastIndex)
            list[0] = head.copy(items = tail.items + head.items)
        }
        list.addAll(0, older)
    }

    fun beginTurn(text: String, attachments: List<UserAttachment>) {
        list += ChatMessage(newId(), Role.User, text = text, attachments = attachments)
        list += ChatMessage(newId(), Role.Assistant, streaming = true)
        kinds.clear()
    }

    fun beginFollow() {
        dropTrailingAssistant()
        list += ChatMessage(newId(), Role.Assistant, streaming = true)
        kinds.clear()
    }

    fun fail(message: String) {
        settle(error = message)
    }

    fun onFrame(type: String, data: JsonObject) {
        when (type) {
            "step.start" -> stepStart(data)
            "step.delta" -> stepDelta(data)
            "step.stop" -> stepStop(data)
            "interaction.status_update" -> if (data.str("status") == "cancelled") settle(cancelled = true)
            "error" -> settle(error = errorMessage(data))
            "orch.resync" -> resync()
            "done" -> settle()
        }
    }

    private fun dropTrailingAssistant() {
        if (list.lastOrNull()?.role == Role.Assistant) list.removeAt(list.lastIndex)
    }

    private fun resync() {
        kinds.clear()
        updateStreaming { it.copy(items = emptyList(), error = null) }
    }

    private fun errorMessage(data: JsonObject): String {
        val error = data["error"] as? JsonObject
        return error?.str("message") ?: data.str("message") ?: "The agent stopped unexpectedly."
    }

    private fun updateStreaming(transform: (ChatMessage) -> ChatMessage) {
        val index = list.lastIndex
        val last = list.getOrNull(index) ?: return
        if (last.role == Role.Assistant && last.streaming) list[index] = transform(last)
    }

    private fun mutateItems(transform: (List<ChatItem>) -> List<ChatItem>) {
        updateStreaming { it.copy(items = transform(it.items)) }
    }

    private fun settle(cancelled: Boolean = false, error: String? = null) {
        val failed = cancelled || error != null
        updateStreaming { message ->
            message.copy(
                streaming = false,
                cancelled = cancelled,
                error = error,
                items = message.items.mapNotNull { item -> settleItem(item, failed) }
            )
        }
        kinds.clear()
    }

    private fun settleItem(item: ChatItem, failed: Boolean): ChatItem? = when {
        item is ReasoningItem && item.text.isBlank() -> null
        item is ReasoningItem && item.active -> item.copy(active = false)
        item is ToolItem && item.status == ToolStatus.Running ->
            item.copy(status = if (failed) ToolStatus.Error else ToolStatus.Done)
        else -> item
    }

    private fun stepStart(data: JsonObject) {
        val index = data["index"].asInt() ?: return
        val step = data["step"] as? JsonObject ?: return
        when (step.str("type")) {
            "thought" -> {
                kinds[index] = Kind.Thought
                mutateItems { it + ReasoningItem(newId(), "", active = true) }
            }
            "model_output" -> kinds[index] = Kind.Text
            else -> {
                kinds[index] = Kind.Other
                val tool = toolFromStep(step)
                if (tool != null) {
                    mutateItems { it + tool }
                } else {
                    resultFromStep(step)?.let { (callId, output, isError) ->
                        mutateItems { applyResult(it, callId, output, isError) }
                    }
                }
            }
        }
    }

    private fun stepDelta(data: JsonObject) {
        val index = data["index"].asInt() ?: return
        val text = deltaText(data["delta"])
        if (text.isEmpty()) return
        when (kinds[index]) {
            Kind.Thought -> mutateItems { items ->
                val last = items.lastOrNull()
                if (last is ReasoningItem && last.active) items.dropLast(1) + last.copy(text = last.text + text)
                else items + ReasoningItem(newId(), text, active = true)
            }
            Kind.Text -> mutateItems { items ->
                val last = items.lastOrNull()
                if (last is TextItem) items.dropLast(1) + last.copy(text = last.text + text)
                else items + TextItem(newId(), text)
            }
            else -> Unit
        }
    }

    private fun stepStop(data: JsonObject) {
        val index = data["index"].asInt() ?: return
        ((data["step_usage"] as? JsonObject)?.get("total_input_tokens")).asLong()?.let { contextTokens = it }
        val kind = kinds.remove(index)
        if (kind != Kind.Thought) return
        mutateItems { items ->
            val position = items.indexOfLast { it is ReasoningItem && it.active }
            if (position < 0) return@mutateItems items
            val item = items[position] as ReasoningItem
            val closed = item.copy(active = false)
            if (item.text.isBlank()) items.filterIndexed { i, _ -> i != position }
            else items.mapIndexed { i, entry -> if (i == position) closed else entry }
        }
    }

    private fun applyRecord(record: JsonObject) {
        when (record.str("kind")) {
            "user_input" -> list += userMessage(record["content"] as? JsonArray)
            "step" -> applyStepRecord(record)
            "interaction" -> applyInteractionRecord(record)
        }
    }

    private fun ensureAssistant(): Int {
        if (list.lastOrNull()?.role != Role.Assistant) list += ChatMessage(newId(), Role.Assistant)
        return list.lastIndex
    }

    private fun applyStepRecord(record: JsonObject) {
        val step = record["step"] as? JsonObject ?: return
        val deltas = record["deltas"] as? JsonArray ?: JsonArray(emptyList())
        val text = deltas.joinToString("") { deltaText(it) }
        val index = ensureAssistant()
        val message = list[index]
        val items = when (step.str("type")) {
            "thought" ->
                if (text.isBlank()) message.items
                else message.items + ReasoningItem(newId(), text, active = false)
            "model_output" ->
                if (text.isBlank()) message.items else message.items + TextItem(newId(), text)
            else -> {
                val tool = toolFromStep(step)?.copy(status = ToolStatus.Done)
                if (tool != null) {
                    message.items + tool
                } else {
                    val result = resultFromStep(step)
                    if (result != null) applyResult(message.items, result.first, result.second, result.third)
                    else message.items
                }
            }
        }
        list[index] = message.copy(items = items)
    }

    private fun applyInteractionRecord(record: JsonObject) {
        val data = record["data"] as? JsonObject ?: return
        when (record.str("event_type")) {
            "error" -> {
                val index = ensureAssistant()
                list[index] = list[index].copy(error = errorMessage(data))
            }
            "interaction.status_update" -> if (data.str("status") == "cancelled") {
                val index = ensureAssistant()
                list[index] = list[index].copy(cancelled = true)
            }
        }
    }

    private fun userMessage(content: JsonArray?): ChatMessage {
        val parts = content?.mapNotNull { it as? JsonObject }.orEmpty()
        val raw = parts.filter { it.str("type") == "text" }.mapNotNull { it.str("text") }.joinToString("\n\n")
        val (text, noted) = splitNotes(raw)
        val images = parts.filter { it.str("type") == "image" }.mapIndexed { i, part ->
            val mime = part.str("mime_type") ?: "image/png"
            UserAttachment(
                name = "image${i + 1}.${mime.substringAfter('/')}",
                dataUrl = part.str("data")?.let { "data:$mime;base64,$it" }
            )
        }
        return ChatMessage(newId(), Role.User, text = text, attachments = noted + images)
    }

    private fun splitNotes(raw: String): Pair<String, List<UserAttachment>> {
        val lines = raw.lines().toMutableList()
        val attachments = ArrayList<UserAttachment>()
        while (lines.isNotEmpty()) {
            val line = lines.last().trim()
            if (line.isEmpty()) {
                lines.removeAt(lines.lastIndex)
                continue
            }
            if (!(line.startsWith("[") && line.endsWith("]"))) break
            lines.removeAt(lines.lastIndex)
            UPLOAD_NOTE.find(line)?.let { match ->
                val path = match.groupValues[1].trimEnd('.')
                val binary = path.endsWith(".b64")
                val clean = path.removeSuffix(".b64")
                attachments.add(0, UserAttachment(name = clean.substringAfterLast('/'), path = if (binary) null else path))
            }
        }
        return lines.joinToString("\n").trim() to attachments
    }

    private fun toolFromStep(step: JsonObject): ToolItem? {
        val callId = step.str("id").orEmpty()
        val arguments = step["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val (name, args) = when (step.str("type")) {
            "function_call" -> (step.str("name") ?: "tool") to arguments
            "code_execution_call" -> EXECUTE_BASH to JsonObject(
                listOfNotNull(arguments["code"]?.let { "command" to it }).toMap()
            )
            "google_search_call" -> "search" to arguments
            "url_context_call" -> "browse" to arguments
            else -> return null
        }
        return ToolItem(newId(), callId, toolDisplay(name, args), null, ToolStatus.Running)
    }

    private fun resultFromStep(step: JsonObject): Triple<String, String?, Boolean>? {
        val callId = step.str("call_id").orEmpty()
        val isError = (step["is_error"] as? JsonPrimitive)?.booleanOrNull == true
        return when (step.str("type")) {
            "code_execution_result" -> Triple(callId, resultText(step["result"]), isError)
            "function_result", "google_search_result", "url_context_result" -> Triple(callId, null, isError)
            else -> null
        }
    }

    private fun applyResult(items: List<ChatItem>, callId: String, output: String?, isError: Boolean): List<ChatItem> {
        val position = items.indexOfLast { it is ToolItem && it.callId == callId }
        if (position < 0) return items
        val tool = items[position] as ToolItem
        val updated = tool.copy(output = output ?: tool.output, status = if (isError) ToolStatus.Error else ToolStatus.Done)
        return items.mapIndexed { i, item -> if (i == position) updated else item }
    }

    private fun resultText(result: JsonElement?): String? = when (result) {
        null, JsonNull -> null
        is JsonPrimitive -> result.content
        else -> result.toString()
    }

    private fun deltaText(delta: JsonElement?): String {
        val obj = delta as? JsonObject ?: return ""
        (obj["content"] as? JsonObject)?.str("text")?.let { return it }
        val type = obj.str("type")
        return if (type == null || type == "text") obj.str("text").orEmpty() else ""
    }

    private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

    private fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull

    private companion object {
        val UPLOAD_NOTE = Regex("""available at (/\S+)""")
    }
}
