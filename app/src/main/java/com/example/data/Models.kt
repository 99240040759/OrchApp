package com.example.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Immutable
enum class ThinkingEffort(val wire: String, val label: String, val shortLabel: String) {
    Low("low", "Low", "Low"),
    Medium("medium", "Medium", "Med"),
    High("high", "High", "High");

    companion object {
        fun fromWire(value: String?): ThinkingEffort =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: Medium
    }
}

@Serializable
@Immutable
data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String = "",
    val contextWindow: Long = 0,
    val maxTokens: Long = 0,
    val capabilities: List<String> = emptyList(),
    val badge: String? = null
) {
    val supportsThinking: Boolean get() = "thinking" in capabilities
}

@Serializable
@Immutable
data class Budget(
    val costUsd: Double = 0.0,
    val limitUsd: Double = 0.0,
    val remaining: Double = 0.0,
    val period: String = "",
    val resetsOn: String = "",
    val allowed: Boolean = true
)

@Serializable
@Immutable
data class SessionSummary(
    val id: String,
    val title: String? = null,
    val workspacePath: String? = null,
    val updatedAt: Long = 0,
    val contextTokens: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val running: Boolean = false
)

@Serializable
data class WorkspaceInit(val repoUrl: String)

@Serializable
@Immutable
data class Workspace(
    val workspace: String,
    val repoUrl: String,
    val repoDir: String = ""
)

@Serializable
data class ChatRequest(
    val sessionId: String,
    val prompt: String,
    val model: String,
    val workspace: String,
    val repoUrl: String? = null,
    val activeConnectors: List<String> = emptyList(),
    val thinkingEffort: String = ThinkingEffort.Medium.wire
)

@Serializable
data class CancelRequest(val sessionId: String)

@Serializable
enum class ToolIcon {
    @SerialName("file") File,
    @SerialName("folder") Folder,
    @SerialName("terminal") Terminal,
    @SerialName("search") Search,
    @SerialName("globe") Globe,
    @SerialName("book") Book,
    @SerialName("cpu") Cpu,
    @SerialName("zapOff") ZapOff
}

@Serializable
@Immutable
data class ToolDisplay(
    val label: String = "",
    val activeLabel: String = "",
    val filename: String? = null,
    val fullPath: String? = null,
    val lineRange: String? = null,
    val addedLines: Int? = null,
    val removedLines: Int? = null,
    val targetText: String? = null,
    val icon: ToolIcon = ToolIcon.File,
    val opensArtifact: Boolean = false,
    val connector: String? = null
)

@Serializable
data class HistoryPage(
    val records: List<JsonObject> = emptyList(),
    val hasMore: Boolean = false,
    val earliestSeq: Long? = null
)

@Serializable
@Immutable
data class FileEntry(
    val path: String,
    val name: String
)

@Serializable
@Immutable
data class TreeEntry(
    val name: String,
    val path: String,
    val isDir: Boolean = false
)

@Serializable
data class DirListing(
    val entries: List<TreeEntry> = emptyList(),
    val truncated: Boolean = false
)

@Serializable
data class FileContent(
    val path: String = "",
    val content: String,
    val truncated: Boolean = false
)

@Serializable
data class TranscribeRequest(
    val audio: String,
    val format: String
)

@Serializable
data class TranscribeResponse(val text: String)

@Serializable
@Immutable
data class ConnectorAccount(
    val id: String,
    val toolkit: String,
    val status: String = ""
)

@Serializable
data class ConnectorAccountsResponse(
    val accounts: List<ConnectorAccount> = emptyList()
)

@Serializable
data class ConnectorConnectRequest(val toolkit: String)

@Serializable
data class ConnectorConnectResponse(val redirectUrl: String)

@Immutable
data class ConnectorDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: String
)

object ConnectorCatalog {
    val all = listOf(
        ConnectorDefinition(
            id = "googledrive",
            name = "Google Drive",
            description = "Search, read, and organize files, folders, and spreadsheets in Google Drive.",
            category = "Cloud Storage"
        ),
        ConnectorDefinition(
            id = "gmail",
            name = "Gmail",
            description = "Search and read email, and create, send, and reply to messages in Gmail.",
            category = "Communication"
        ),
        ConnectorDefinition(
            id = "github",
            name = "GitHub",
            description = "Browse repositories and code, manage issues and pull requests, and commit changes.",
            category = "Dev Tools"
        ),
        ConnectorDefinition(
            id = "notion",
            name = "Notion",
            description = "Search, read, and edit pages and databases in Notion workspaces.",
            category = "Productivity"
        ),
        ConnectorDefinition(
            id = "slack",
            name = "Slack",
            description = "Search and read channel history, and send and schedule messages in Slack.",
            category = "Communication"
        ),
        ConnectorDefinition(
            id = "jira",
            name = "Jira",
            description = "Search, create, and update issues, comments, and workflow transitions in Jira Cloud.",
            category = "Dev Tools"
        )
    )
}
