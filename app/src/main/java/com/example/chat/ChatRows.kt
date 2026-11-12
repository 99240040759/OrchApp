package com.example.chat

import androidx.compose.runtime.Immutable

@Immutable
sealed interface ChatRow {
    val key: String
}

@Immutable
data class UserRow(
    override val key: String,
    val text: String,
    val attachments: List<UserAttachment>
) : ChatRow

@Immutable
data class WorkRow(
    override val key: String,
    val items: List<ChatItem>,
    val live: Boolean
) : ChatRow

@Immutable
data class TextRow(
    override val key: String,
    val text: String,
    val live: Boolean = false
) : ChatRow

@Immutable
data class PendingRow(override val key: String) : ChatRow

@Immutable
data class NoticeRow(
    override val key: String,
    val text: String,
    val isError: Boolean
) : ChatRow

@Immutable
data class CopyRow(
    override val key: String,
    val text: String
) : ChatRow

@Immutable
data class EarlierRow(val loading: Boolean, val failed: Boolean) : ChatRow {
    override val key: String = "earlier"
}

fun buildRows(messages: List<ChatMessage>): List<ChatRow> = messages.flatMap(::rowsOf)

private fun rowsOf(message: ChatMessage): List<ChatRow> {
    if (message.role == Role.User) return listOf(UserRow(message.id, message.text, message.attachments))

    val rows = ArrayList<ChatRow>()
    val items = message.items
    var start = 0
    while (start < items.size) {
        val isText = items[start] is TextItem
        var end = start + 1
        while (end < items.size && (items[end] is TextItem) == isText) end++
        val run = items.subList(start, end)
        val live = message.streaming && end == items.size
        if (isText) {
            val text = run.filterIsInstance<TextItem>().joinToString("\n\n") { it.text.trim('\n') }
            if (text.isNotBlank()) rows += TextRow("${message.id}:t${run.first().id}", text, live)
        } else {
            rows += WorkRow("${message.id}:w${run.first().id}", run.toList(), live)
        }
        start = end
    }
    if (message.streaming && rows.isEmpty()) rows += PendingRow("${message.id}:p")
    message.error?.let { rows += NoticeRow("${message.id}:e", it, isError = true) }
    if (message.cancelled) rows += NoticeRow("${message.id}:x", "Stopped", isError = false)
    if (!message.streaming) {
        val copy = rows.filterIsInstance<TextRow>().joinToString("\n\n") { it.text.trim() }
        if (copy.isNotBlank()) rows += CopyRow("${message.id}:c", copy)
    }
    return rows
}
