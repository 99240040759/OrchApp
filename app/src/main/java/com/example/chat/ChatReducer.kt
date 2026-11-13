package com.example.chat

import com.example.data.OrchJson
import com.example.data.ToolDisplay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class ChatReducer(private val idPrefix: String = "c") {
    private val list = ArrayList<ChatMessage>()
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
        replaying = false
    }

    fun setContextTokens(tokens: Long) {
        contextTokens = tokens
    }

    fun load(records: List<JsonObject>) {
        resetContent()
        records
            .sortedBy { it.long("seq") ?: 0L }
            .forEach { applyRecord(it) }
        for (index in list.indices) {
            val message = list[index]
            if (message.role != Role.Assistant) continue
            val failed = message.cancelled || message.error != null
            list[index] = message.copy(items = message.items.map { settleItem(it, failed) })
        }
    }

    fun prepend(records: List<JsonObject>) {
        val page = ChatReducer("p${counter++}-").apply { load(records) }
        val older = ArrayList(page.list)
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
        replaying = false
    }

    fun beginFollow() {
        val index = list.lastIndex
        val last = list.getOrNull(index)
        if (last != null && last.role == Role.Assistant) {
            list[index] = last.copy(streaming = true, cancelled = false, error = null, statusNote = null)
            replaying = true
        } else {
            list += ChatMessage(newId(), Role.Assistant, streaming = true)
            replaying = false
        }
    }

    fun fail(message: String) {
        settle(error = message)
    }

    fun onFrame(type: String, data: JsonObject) {
        if (replaying && type in LIVE_STEP_EVENTS) {
            replaying = false
            updateStreaming { it.copy(items = emptyList(), error = null, statusNote = null) }
        }
        when (type) {
            "workStart" -> onWorkStart()
            "workStop" -> onWorkStop(data)
            "reasoningStart" -> onReasoningStart()
            "reasoningDelta" -> onReasoningDelta(data)
            "reasoningStop" -> onReasoningStop(data)
            "textDelta" -> onTextDelta(data)
            "textStop" -> onTextStop()
            "toolStart" -> onToolStart(data)
            "toolResult" -> onToolResult(data)
            "usage" -> data.long("contextTokens")?.let { contextTokens = it }
            "notice" -> onNotice(data)
            "compacted" -> onCompacted(data)
            "resync" -> resync()
            "cancelled" -> settle(cancelled = true)
            "error" -> settle(error = errorMessage(data))
            "done" -> settle()
        }
    }

    private fun resync() {
        replaying = true
    }

    private fun errorMessage(data: JsonObject): String =
        (data["error"] as? JsonObject)?.str("message")
            ?: data.str("message")
            ?: data.str("error")
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
                statusNote = null,
                cancelled = cancelled,
                error = error,
                items = message.items.map { settleItem(it, failed) }
            )
        }
    }

    private fun settleItem(item: ChatItem, failed: Boolean): ChatItem = when (item) {
        is WorkGroupItem -> item.copy(
            active = false,
            stopped = item.stopped || failed,
            items = item.items.map { settleItem(it, failed || item.stopped) }
        )
        is ReasoningItem -> if (item.active) item.copy(active = false) else item
        is TextItem -> if (item.active) item.copy(active = false) else item
        is ToolItem -> if (item.status == ToolStatus.Running) {
            item.copy(status = if (failed) ToolStatus.Error else ToolStatus.Done)
        } else {
            item
        }
        is CompactionItem -> item
    }

    private fun ensureActiveWork(items: List<ChatItem>): Pair<List<ChatItem>, WorkGroupItem> {
        val closed = closeActiveText(items)
        val last = closed.lastOrNull()
        return if (last is WorkGroupItem && last.active) {
            closed.dropLast(1) to last
        } else {
            closed to WorkGroupItem(id = newId(), active = true)
        }
    }

    private fun closeActiveText(items: List<ChatItem>): List<ChatItem> {
        val last = items.lastOrNull() as? TextItem ?: return items
        if (!last.active) return items
        return items.dropLast(1) + last.copy(active = false)
    }

    private fun closeActiveReasoning(workItems: List<ChatItem>, durationSeconds: Long? = null): List<ChatItem> {
        val index = workItems.indexOfLast { it is ReasoningItem && it.active }
        if (index < 0) return workItems
        val item = workItems[index] as ReasoningItem
        return workItems.replaced(
            index,
            item.copy(active = false, durationSeconds = durationSeconds ?: item.durationSeconds)
        )
    }

    private fun closeActiveWork(
        items: List<ChatItem>,
        durationSeconds: Long? = null,
        stopped: Boolean = false
    ): List<ChatItem> {
        val index = items.indexOfLast { it is WorkGroupItem && it.active }
        if (index < 0) return items
        val group = items[index] as WorkGroupItem
        val settledChildren = group.items.map { child ->
            when (child) {
                is ReasoningItem -> if (child.active) child.copy(active = false) else child
                is ToolItem -> if (child.status == ToolStatus.Running && stopped) {
                    child.copy(status = ToolStatus.Error)
                } else {
                    child
                }
                else -> child
            }
        }
        return items.replaced(
            index,
            group.copy(
                active = false,
                durationSeconds = durationSeconds ?: group.durationSeconds,
                stopped = stopped || group.stopped,
                items = settledChildren
            )
        )
    }

    private fun onWorkStart() {
        mutateItems { items ->
            val (prefix, group) = ensureActiveWork(items)
            prefix + group
        }
    }

    private fun onWorkStop(data: JsonObject) {
        val duration = data.long("durationSeconds")
        val stopped = data.bool("stopped") == true
        mutateItems { items ->
            closeActiveWork(items, durationSeconds = duration, stopped = stopped)
        }
    }

    private fun onReasoningStart() {
        mutateItems { items ->
            val (prefix, group) = ensureActiveWork(items)
            val closed = closeActiveReasoning(group.items)
            prefix + group.copy(items = closed + ReasoningItem(id = newId(), text = "", active = true))
        }
    }

    private fun onReasoningDelta(data: JsonObject) {
        val delta = data.str("text") ?: return
        if (delta.isEmpty()) return
        mutateItems { items ->
            val (prefix, group) = ensureActiveWork(items)
            val last = group.items.lastOrNull()
            val updated = if (last is ReasoningItem && last.active) {
                group.items.dropLast(1) + last.copy(text = last.text + delta)
            } else {
                val closed = closeActiveReasoning(group.items)
                closed + ReasoningItem(id = newId(), text = delta, active = true)
            }
            prefix + group.copy(items = updated)
        }
    }

    private fun onReasoningStop(data: JsonObject) {
        val duration = data.long("durationSeconds")
        mutateItems { items ->
            val index = items.indexOfLast { it is WorkGroupItem && it.active }
            if (index < 0) return@mutateItems items
            val group = items[index] as WorkGroupItem
            items.replaced(index, group.copy(items = closeActiveReasoning(group.items, duration)))
        }
    }

    private fun onToolStart(data: JsonObject) {
        val callId = data.str("id").orEmpty()
        val name = data.str("name").orEmpty()
        val display = data["display"]?.let {
            runCatching { OrchJson.decodeFromJsonElement<ToolDisplay>(it) }.getOrNull()
        } ?: ToolDisplay(label = name, activeLabel = name)
        val tool = ToolItem(
            id = newId(),
            callId = callId,
            display = display,
            output = null,
            status = ToolStatus.Running
        )
        mutateItems { items ->
            val (prefix, group) = ensureActiveWork(items)
            val closed = closeActiveReasoning(group.items)
            prefix + group.copy(items = closed + tool)
        }
    }

    private fun onToolResult(data: JsonObject) {
        val callId = data.str("callId").orEmpty()
        val isError = data.bool("isError") == true
        val output = data.str("output")
        val status = if (isError) ToolStatus.Error else ToolStatus.Done
        mutateItems { items ->
            items.map { item ->
                if (item is WorkGroupItem) {
                    val idx = item.items.indexOfLast { it is ToolItem && it.callId == callId }
                    if (idx >= 0) {
                        val tool = item.items[idx] as ToolItem
                        item.copy(
                            items = item.items.replaced(
                                idx,
                                tool.copy(output = output ?: tool.output, status = status)
                            )
                        )
                    } else {
                        item
                    }
                } else {
                    item
                }
            }
        }
    }

    private fun onTextDelta(data: JsonObject) {
        val delta = data.str("text") ?: return
        if (delta.isEmpty()) return
        mutateItems { items ->
            val closedWork = closeActiveWork(items)
            val last = closedWork.lastOrNull()
            if (last is TextItem && last.active) {
                closedWork.dropLast(1) + last.copy(text = last.text + delta)
            } else {
                val closedText = closeActiveText(closedWork)
                closedText + TextItem(id = newId(), text = delta, active = true)
            }
        }
    }

    private fun onTextStop() {
        mutateItems { items -> closeActiveText(items) }
    }

    private fun onNotice(data: JsonObject) {
        val raw = (data["message"] as? JsonPrimitive)?.contentOrNull?.trim()
        updateStreaming { it.copy(statusNote = raw?.takeIf { s -> s.isNotEmpty() }) }
    }

    private fun onCompacted(data: JsonObject) {
        data.long("contextTokens")?.let { contextTokens = it }
        val id = data.str("id") ?: newId()
        val count = data.int("originalMessageCount") ?: 0
        val ts = data.long("ts") ?: 0L
        updateStreaming { message ->
            val closed = closeActiveText(closeActiveWork(message.items))
            message.copy(
                statusNote = null,
                items = closed + CompactionItem(id = id, originalMessageCount = count, ts = ts)
            )
        }
    }

    private fun applyRecord(record: JsonObject) {
        when (record.str("kind")) {
            "user" -> list += userMessage(record["content"] as? JsonArray)
            "work" -> applyWorkRecord(record)
            "reasoning" -> applyReasoningRecord(record)
            "toolCall" -> applyToolCallRecord(record)
            "text" -> applyTextRecord(record)
            "compaction" -> applyCompactionRecord(record)
            "error" -> {
                val index = ensureAssistant()
                list[index] = list[index].copy(error = record.str("message") ?: "The agent stopped unexpectedly.")
            }
        }
    }

    private fun ensureAssistant(): Int {
        if (list.lastOrNull()?.role != Role.Assistant) list += ChatMessage(newId(), Role.Assistant)
        return list.lastIndex
    }

    private fun appendToWorkGroup(child: ChatItem) {
        val index = ensureAssistant()
        val message = list[index]
        val items = message.items
        val last = items.lastOrNull()
        val updated = if (last is WorkGroupItem) {
            items.dropLast(1) + last.copy(items = last.items + child)
        } else {
            items + WorkGroupItem(id = newId(), items = listOf(child))
        }
        list[index] = message.copy(items = updated)
    }

    private fun applyWorkRecord(record: JsonObject) {
        val index = ensureAssistant()
        val message = list[index]
        val group = WorkGroupItem(
            id = newId(),
            durationSeconds = record.long("durationSeconds"),
            stopped = record.bool("stopped") == true,
            active = false
        )
        list[index] = message.copy(items = message.items + group)
    }

    private fun applyReasoningRecord(record: JsonObject) {
        val text = record.str("text").orEmpty()
        if (text.isBlank()) return
        appendToWorkGroup(
            ReasoningItem(
                id = newId(),
                text = text,
                durationSeconds = record.long("durationSeconds"),
                active = false
            )
        )
    }

    private fun applyToolCallRecord(record: JsonObject) {
        val callId = record.str("id").orEmpty()
        val name = record.str("name").orEmpty()
        val display = record["display"]?.let {
            runCatching { OrchJson.decodeFromJsonElement<ToolDisplay>(it) }.getOrNull()
        } ?: ToolDisplay(label = name, activeLabel = name)
        val output = record.str("output")
        val isError = record.bool("isError") == true
        appendToWorkGroup(
            ToolItem(
                id = newId(),
                callId = callId,
                display = display,
                output = output,
                status = if (isError) ToolStatus.Error else ToolStatus.Done
            )
        )
    }

    private fun applyTextRecord(record: JsonObject) {
        val text = record.str("text").orEmpty()
        if (text.isBlank()) return
        val index = ensureAssistant()
        val message = list[index]
        list[index] = message.copy(items = message.items + TextItem(newId(), text, active = false))
    }

    private fun applyCompactionRecord(record: JsonObject) {
        val index = ensureAssistant()
        val message = list[index]
        val item = CompactionItem(
            id = record.str("id") ?: newId(),
            originalMessageCount = record.int("originalMessageCount") ?: 0,
            ts = record.long("ts") ?: 0L
        )
        list[index] = message.copy(items = message.items + item)
    }

    private fun userMessage(content: JsonArray?): ChatMessage {
        val parts = content?.mapNotNull { it as? JsonObject }.orEmpty()
        val texts = ArrayList<String>()
        val attachments = ArrayList<UserAttachment>()
        var imageCount = 0
        for (part in parts) {
            when (part.str("type")) {
                "text" -> part.str("text")?.takeIf { it.isNotBlank() }?.let { texts += it }
                "image" -> {
                    imageCount++
                    val mime = part.str("mimeType") ?: "image/png"
                    val ext = mime.substringAfter('/').ifEmpty { "png" }
                    val data = part.str("data")
                    val uri = part.str("uri")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    attachments += UserAttachment(
                        name = "image$imageCount.$ext",
                        dataUrl = data?.let { "data:$mime;base64,$it" },
                        previewUri = if (data == null) uri else null
                    )
                }
                "document" -> {
                    val path = part.str("path")
                    val name = part.str("name") ?: path?.substringAfterLast('/') ?: "file"
                    attachments += UserAttachment(name = name, path = path)
                }
            }
        }
        return ChatMessage(
            id = newId(),
            role = Role.User,
            text = texts.joinToString("\n\n").trim(),
            attachments = attachments
        )
    }

    private fun List<ChatItem>.replaced(position: Int, item: ChatItem): List<ChatItem> =
        toMutableList().also { it[position] = item }

    private companion object {
        val LIVE_STEP_EVENTS = setOf(
            "workStart",
            "workStop",
            "reasoningStart",
            "reasoningDelta",
            "reasoningStop",
            "textDelta",
            "textStop",
            "toolStart",
            "toolResult",
            "compacted"
        )
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotEmpty() }

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
