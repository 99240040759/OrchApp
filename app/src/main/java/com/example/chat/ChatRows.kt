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
    val durationSeconds: Long?,
    val stopped: Boolean,
    val live: Boolean
) : ChatRow

@Immutable
data class TextRow(
    override val key: String,
    val text: String,
    val live: Boolean = false
) : ChatRow

@Immutable
data class CompactionRow(
    override val key: String,
    val originalMessageCount: Int
) : ChatRow

@Immutable
data class PendingRow(
    override val key: String,
    val label: String = "Thinking"
) : ChatRow

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
    var index = 0
    while (index < items.size) {
        when (val item = items[index]) {
            is WorkGroupItem -> {
                if (item.items.isNotEmpty() || item.active) {
                    rows += WorkRow(
                        key = "${message.id}:w:${item.id}",
                        items = item.items,
                        durationSeconds = item.durationSeconds,
                        stopped = item.stopped,
                        live = message.streaming && item.active
                    )
                }
                index++
            }
            is CompactionItem -> {
                rows += CompactionRow(
                    key = "${message.id}:k:${item.id}",
                    originalMessageCount = item.originalMessageCount
                )
                index++
            }
            is TextItem -> {
                var end = index + 1
                while (end < items.size && items[end] is TextItem) end++
                val run = items.subList(index, end).filterIsInstance<TextItem>()
                val text = run.joinToString("\n\n") { it.text.trim('\n') }
                val live = message.streaming && (end == items.size || run.last().active)
                if (text.isNotBlank()) {
                    rows += TextRow("${message.id}:t:${run.first().id}", text, live)
                }
                index = end
            }
            else -> index++
        }
    }
    val note = message.statusNote
    if (message.streaming) {
        if (!note.isNullOrBlank()) {
            rows += PendingRow("${message.id}:n", note)
        } else if (rows.isEmpty()) {
            rows += PendingRow("${message.id}:p", "Thinking")
        }
    }
    message.error?.let { rows += NoticeRow("${message.id}:e", it, isError = true) }
    if (message.cancelled) rows += NoticeRow("${message.id}:x", "Stopped", isError = false)
    if (!message.streaming) {
        val copy = rows.filterIsInstance<TextRow>().joinToString("\n\n") { it.text.trim() }
        if (copy.isNotBlank()) rows += CopyRow("${message.id}:c", copy)
    }
    return rows
}
