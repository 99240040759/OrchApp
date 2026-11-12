package com.example.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val contextWindow: Long = 0,
    val badge: String? = null
)

@Serializable
data class Budget(
    val costUsd: Double = 0.0,
    val limitUsd: Double = 0.0,
    val resetsOn: String = ""
)

@Serializable
data class SessionSummary(
    val id: String,
    val title: String? = null,
    val workspacePath: String? = null,
    val updatedAt: Long = 0,
    val contextTokens: Long = 0,
    val running: Boolean = false
)

@Serializable
data class WorkspaceInit(val repoUrl: String)

@Serializable
data class Workspace(
    val workspace: String,
    val repoUrl: String
)

@Serializable
data class ChatRequest(
    val sessionId: String,
    val prompt: String,
    val model: String,
    val workspace: String? = null,
    val repoUrl: String? = null
)

@Serializable
data class CancelRequest(val sessionId: String)

@Serializable
data class StoredRecord(
    val seq: Long,
    val record: JsonObject
)

@Serializable
data class HistoryPage(
    val records: List<StoredRecord> = emptyList(),
    val hasMore: Boolean = false,
    val earliestSeq: Long? = null
)

@Serializable
data class FileEntry(
    val path: String,
    val name: String
)

@Serializable
data class FileContent(
    val content: String,
    val truncated: Boolean = false
)

@Serializable
data class FileMetadata(
    val name: String,
    val sizeBytes: Long = 0,
    val extension: String = "",
    val mime: String = ""
)

@Serializable
data class TranscribeRequest(
    val audio: String,
    val format: String
)

@Serializable
data class TranscribeResponse(val text: String)
