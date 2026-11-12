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

    private class Slot(val kind: Kind, var itemId: String? = null)

    private class ToolResult(val callId: String, val output: String?, val isError: Boolean)

    private val list = ArrayList<ChatMessage>()
    private val slots = HashMap<Int, Slot>()
    private val orphans = HashMap<String, ToolResult>()
    private var counter = 0
    private var replaying = false

    var contextTokens = 0L
        private set

    val messages: List<ChatMessage> get() = list.toList()

    val hasUserMessage: Boolean get() = list.any { it.role == Role.User }

    val streaming: Boolean get() = list.lastOrNull()?.let { it.role == Role.Assistant && it.streaming } == true

    private fun newId() = "$idPrefix${counter++}"

    fun clear() {
        resetContent()
        contextTokens = 0
    }

    private fun resetContent() {
        list.clear()
        slots.clear()
        orphans.clear()
        replaying = false
    }

    fun setContextTokens(tokens: Long) {
        contextTokens = tokens
    }

    fun load(records: List<StoredRecord>) {
        resetContent()
        records.sortedBy { it.seq }.forEach { applyRecord(it.record) }
        for (index in list.indices) {
            val message = list[index]
            if (message.role != Role.Assistant) continue
            val failed = message.cancelled || message.error != null
            list[index] = message.copy(items = message.items.map { settleItem(it, failed) })
        }
    }

    fun prepend(records: List<StoredRecord>) {
        val page = ChatReducer("p${counter++}-").apply { load(records) }
        val older = ArrayList(page.list)
        val tail = older.lastOrNull()
        val head = list.firstOrNull()
        if (tail != null && head != null && tail.role == Role.Assistant && head.role == Role.Assistant) {
            older.removeAt(older.lastIndex)
            list[0] = head.copy(items = tail.items + head.items)
        }
        list.addAll(0, older)
        resolveOrphans()
        orphans.putAll(page.orphans)
    }

    fun beginTurn(text: String, attachments: List<UserAttachment>) {
        list += ChatMessage(newId(), Role.User, text = text, attachments = attachments)
        list += ChatMessage(newId(), Role.Assistant, streaming = true)
        slots.clear()
        replaying = false
    }

    fun beginFollow() {
        val index = list.lastIndex
        val last = list.getOrNull(index)
        if (last != null && last.role == Role.Assistant) {
            list[index] = last.copy(streaming = true, cancelled = false, error = null)
            replaying = true
        } else {
            list += ChatMessage(newId(), Role.Assistant, streaming = true)
        }
        slots.clear()
    }

    fun fail(message: String) {
        settle(error = message)
    }

    fun onFrame(type: String, data: JsonObject) {
        if (replaying && type.startsWith("step.")) {
            replaying = false
            updateStreaming { it.copy(items = emptyList()) }
        }
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

    private fun resync() {
        slots.clear()
        replaying = false
        updateStreaming { it.copy(items = emptyList(), error = null) }
    }

    private fun errorMessage(data: JsonObject): String =
        (data["error"] as? JsonObject)?.str("message")
            ?: data.str("error")
            ?: data.str("message")
            ?: "The agent stopped unexpectedly."

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
        replaying = false
        updateStreaming { message ->
            message.copy(
                streaming = false,
                cancelled = cancelled,
                error = error,
                items = message.items.map { settleItem(it, failed) }
            )
        }
        slots.clear()
    }

    private fun settleItem(item: ChatItem, failed: Boolean): ChatItem = when (item) {
        is ReasoningItem if item.active -> item.copy(active = false)
        is ToolItem if item.status == ToolStatus.Running ->
            item.copy(status = if (failed) ToolStatus.Error else ToolStatus.Done)
        else -> item
    }

    private fun stepStart(data: JsonObject) {
        val index = data["index"].asInt() ?: return
        val step = data["step"] as? JsonObject ?: return
        when (step.str("type")) {
            "thought" -> slots[index] = Slot(Kind.Thought)
            "model_output" -> slots[index] = Slot(Kind.Text)
            else -> {
                slots[index] = Slot(Kind.Other)
                val tool = toolFromStep(step)
                if (tool != null) {
                    mutateItems { it + tool }
                } else {
                    resultFromStep(step)?.let { result -> mutateItems { applyResult(it, result) ?: it } }
                }
            }
        }
    }

    private fun stepDelta(data: JsonObject) {
        val slot = slots[data["index"].asInt() ?: return] ?: return
        val text = deltaText(data["delta"])
        if (text.isEmpty() || slot.kind == Kind.Other) return
        mutateItems { items ->
            val position = slot.itemId?.let { id -> items.indexOfLast { it.id == id } } ?: -1
            if (position < 0) {
                val id = newId()
                slot.itemId = id
                val created: ChatItem =
                    if (slot.kind == Kind.Thought) ReasoningItem(id, text, active = true) else TextItem(id, text)
                items + created
            } else {
                val grown: ChatItem = when (val item = items[position]) {
                    is ReasoningItem -> item.copy(text = item.text + text)
                    is TextItem -> item.copy(text = item.text + text)
                    else -> item
                }
                items.replaced(position, grown)
            }
        }
    }

    private fun stepStop(data: JsonObject) {
        val index = data["index"].asInt() ?: return
        ((data["step_usage"] as? JsonObject)?.get("total_input_tokens")).asLong()?.let { contextTokens = it }
        val slot = slots.remove(index) ?: return
        val id = slot.itemId ?: return
        if (slot.kind != Kind.Thought) return
        mutateItems { items ->
            val position = items.indexOfLast { it.id == id }
            val item = items.getOrNull(position) as? ReasoningItem
            if (item == null) items else items.replaced(position, item.copy(active = false))
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
                val tool = toolFromStep(step)
                val result = if (tool == null) resultFromStep(step) else null
                when {
                    tool != null -> message.items + tool
                    result != null -> applyResult(message.items, result)
                        ?: message.items.also { orphans[result.callId] = result }
                    else -> message.items
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

    private fun resultFromStep(step: JsonObject): ToolResult? {
        val callId = step.str("call_id").orEmpty()
        val isError = (step["is_error"] as? JsonPrimitive)?.booleanOrNull == true
        return when (step.str("type")) {
            "code_execution_result" -> ToolResult(callId, resultText(step["result"]), isError)
            "function_result", "google_search_result", "url_context_result" -> ToolResult(callId, null, isError)
            else -> null
        }
    }

    private fun applyResult(items: List<ChatItem>, result: ToolResult): List<ChatItem>? {
        val position = items.indexOfLast { it is ToolItem && it.callId == result.callId }
        if (position < 0) return null
        val tool = items[position] as ToolItem
        val status = if (result.isError) ToolStatus.Error else ToolStatus.Done
        return items.replaced(position, tool.copy(output = result.output ?: tool.output, status = status))
    }

    private fun resolveOrphans() {
        val pending = orphans.iterator()
        while (pending.hasNext()) {
            val result = pending.next().value
            val index = list.indexOfFirst { message -> message.items.any { it is ToolItem && it.callId == result.callId } }
            if (index < 0) continue
            val items = applyResult(list[index].items, result) ?: continue
            list[index] = list[index].copy(items = items)
            pending.remove()
        }
    }

    private fun List<ChatItem>.replaced(position: Int, item: ChatItem): List<ChatItem> =
        toMutableList().also { it[position] = item }

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
