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
    val text: String
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
data class EarlierRow(val loading: Boolean) : ChatRow {
    override val key: String = "earlier"
}

fun buildRows(messages: List<ChatMessage>): List<ChatRow> = messages.flatMap(::rowsOf)

private fun rowsOf(message: ChatMessage): List<ChatRow> {
    if (message.role == Role.User) return listOf(UserRow(message.id, message.text, message.attachments))

    val rows = ArrayList<ChatRow>()
    val items = message.items
    var i = 0
    while (i < items.size) {
        val start = i
        if (items[i] is TextItem) {
            val builder = StringBuilder()
            while (i < items.size && items[i] is TextItem) {
                if (builder.isNotEmpty() && builder.last() != '\n') builder.append('\n')
                builder.append((items[i] as TextItem).text)
                i++
            }
            if (builder.isNotBlank()) rows += TextRow("${message.id}:t${items[start].id}", builder.toString())
        } else {
            while (i < items.size && items[i] !is TextItem) i++
            val group = items.subList(start, i).toList()
            val trailing = i == items.size
            val live = message.streaming && trailing
            rows += WorkRow("${message.id}:w${group.first().id}", group, live)
        }
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
