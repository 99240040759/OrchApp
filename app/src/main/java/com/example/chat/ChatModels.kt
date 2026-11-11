package com.example.chat

enum class Role { User, Assistant }

enum class ToolStatus { Running, Done, Error }

sealed interface ChatItem {
    val id: String
}

data class TextItem(
    override val id: String,
    val text: String
) : ChatItem

data class ReasoningItem(
    override val id: String,
    val text: String,
    val active: Boolean
) : ChatItem

data class ToolItem(
    override val id: String,
    val callId: String,
    val display: ToolDisplay,
    val output: String?,
    val status: ToolStatus
) : ChatItem

data class UserAttachment(
    val name: String,
    val path: String? = null,
    val dataUrl: String? = null
)

data class ChatMessage(
    val id: String,
    val role: Role,
    val text: String = "",
    val attachments: List<UserAttachment> = emptyList(),
    val items: List<ChatItem> = emptyList(),
    val streaming: Boolean = false,
    val error: String? = null,
    val cancelled: Boolean = false
)

data class Repo(
    val workspace: String,
    val repoUrl: String
) {
    val name: String
        get() = repoUrl.substringAfter("://").substringAfter('/')

    companion object {
        private const val PREFIX = "cloud://"

        fun fromWorkspacePath(path: String?): Repo? {
            if (path.isNullOrBlank() || !path.startsWith(PREFIX)) return null
            return Repo(workspace = path, repoUrl = "https://" + path.removePrefix(PREFIX))
        }
    }
}
