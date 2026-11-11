package com.example.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
@Serializable
value class SessionId(val value: String) {
    companion object {
        private val VALID_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

        @OptIn(ExperimentalUuidApi::class)
        fun generate(): SessionId = SessionId(Uuid.random().toString())

        fun isValid(raw: String): Boolean = VALID_REGEX.matches(raw)
    }
}

@Serializable
data class ModelInfo(
    val id: String = "",
    val name: String = "",
    val provider: String = "",
    val contextWindow: Long = 0L,
    val maxTokens: Long = 0L,
    val capabilities: List<String> = emptyList(),
    val badge: String? = null
)

@Serializable
data class BudgetStatus(
    val costUsd: Double = 0.0,
    val limitUsd: Double = 0.0,
    val period: String = "",
    val resetsOn: String = "",
    val allowed: Boolean = true
)

@Serializable
data class SessionSummary(
    val id: String,
    val title: String? = null,
    val workspacePath: String? = null,
    val updatedAt: Long = 0L,
    val contextTokens: Long = 0L,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cachedTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val running: Boolean = false
)

@Serializable
data class WorkspaceInitRequest(
    val repoUrl: String
)

@Serializable
data class WorkspaceInfo(
    val workspace: String = "",
    val repoUrl: String = "",
    val repoDir: String = "",
    val environmentId: String? = null
)

@Serializable
data class AttachmentIn(
    val name: String,
    val dataBase64: String
)

@Serializable
data class ChatRequest(
    val sessionId: String,
    val prompt: String,
    val model: String,
    val workspace: String? = null,
    val repoUrl: String? = null,
    val attachments: List<AttachmentIn> = emptyList()
)

@Serializable
data class CancelRequest(
    val sessionId: String
)

@Serializable
data class StoredRecord(
    val seq: Long,
    val turn: Long,
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
    val path: String,
    val content: String,
    val truncated: Boolean = false
)

@Serializable
data class FileMetadata(
    val name: String,
    val sizeBytes: Long = 0L,
    val extension: String = "",
    val mime: String = "application/octet-stream",
    val modified: String? = null
)

@Serializable
data class ImagePreviewResponse(
    val dataUrl: String
)

@Serializable
data class TranscribeRequest(
    val audio: String,
    val format: String = "m4a"
)

@Serializable
data class TranscribeResponse(
    val text: String
)

enum class StepKind(val wireName: String, val label: String) {
    THOUGHT("thought", "Thought"),
    MODEL_OUTPUT("model_output", "Response"),
    FUNCTION_CALL("function_call", "Tool Call"),
    FUNCTION_RESULT("function_result", "Tool Result"),
    CODE_EXECUTION("code_execution", "Executed"),
    GOOGLE_SEARCH("google_search", "Searched Web"),
    URL_CONTEXT("url_context", "Browsed"),
    SYSTEM_NOTICE("system_notice", "System");

    companion object {
        fun fromWire(raw: String): StepKind {
            val lower = raw.trim().lowercase()
            return when {
                lower.startsWith("thought") || lower.startsWith("thinking") || lower.startsWith("reasoning") -> THOUGHT
                lower.startsWith("google_search") || lower.startsWith("web_search") || lower == "search" -> GOOGLE_SEARCH
                lower.startsWith("url_context") || lower.startsWith("browse") || lower.startsWith("fetch_url") -> URL_CONTEXT
                lower.startsWith("code_execution") || lower.startsWith("bash") || lower.startsWith("shell") -> CODE_EXECUTION
                lower.startsWith("function_call") || lower.startsWith("tool_call") || lower.startsWith("call") -> FUNCTION_CALL
                lower.startsWith("function_result") || lower.startsWith("tool_result") || lower.startsWith("result") -> FUNCTION_RESULT
                lower.startsWith("system") -> SYSTEM_NOTICE
                lower.startsWith("model_output") || lower == "text" || lower == "output" || lower == "response" -> MODEL_OUTPUT
                else -> entries.firstOrNull { lower.startsWith(it.wireName) } ?: MODEL_OUTPUT
            }
        }
    }
}

data class AntigravityStep(
    val index: Int,
    val kind: StepKind,
    val rawType: String,
    val title: String,
    val content: String = "",
    val toolName: String? = null,
    val toolArgsJson: String? = null,
    val isFinished: Boolean = false,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val thoughtTokens: Long = 0L
)

data class ChatTurn(
    val turnNumber: Long,
    val userPrompt: String,
    val attachments: List<String> = emptyList(),
    val steps: List<AntigravityStep> = emptyList(),
    val statusNote: String? = null,
    val errorCode: String? = null,
    val isStreaming: Boolean = false
)

sealed interface SseStreamEvent {
    val eventId: ULong?

    data class StepStart(
        override val eventId: ULong?,
        val index: Int,
        val step: JsonObject
    ) : SseStreamEvent

    data class StepDelta(
        override val eventId: ULong?,
        val index: Int,
        val delta: JsonElement
    ) : SseStreamEvent

    data class StepStop(
        override val eventId: ULong?,
        val index: Int,
        val inputTokens: Long,
        val outputTokens: Long,
        val thoughtTokens: Long
    ) : SseStreamEvent

    data class InteractionCreated(
        override val eventId: ULong?,
        val interactionId: String,
        val environmentId: String
    ) : SseStreamEvent

    data class InteractionStatusUpdate(
        override val eventId: ULong?,
        val status: String
    ) : SseStreamEvent

    data class InteractionCompleted(
        override val eventId: ULong?,
        val inputTokens: Long,
        val outputTokens: Long,
        val thoughtTokens: Long
    ) : SseStreamEvent

    data class OrchResync(
        override val eventId: ULong?,
        val reason: String
    ) : SseStreamEvent

    data class StreamError(
        override val eventId: ULong?,
        val code: String,
        val message: String
    ) : SseStreamEvent

    data object Done : SseStreamEvent {
        override val eventId: ULong? = null
    }

    data object KeepAlive : SseStreamEvent {
        override val eventId: ULong? = null
    }
}

enum class ArtifactFormat(val label: String, val badgeColorHex: Long) {
    PDF("PDF", 0xFFF2643D),
    DOCX("DOCX", 0xFFF5A623),
    XLSX("XLSX", 0xFFF5A623),
    PPTX("PPTX", 0xFFF2643D),
    IMAGE("Image", 0xFFF5A623),
    MARKDOWN("Markdown", 0xFFF2EFE9),
    CODE_TEXT("File", 0xFFA8A29A),
    BINARY("Binary", 0xFFA8A29A);

    companion object {
        fun resolve(extension: String, mime: String): ArtifactFormat {
            val ext = extension.lowercase().trimStart('.')
            val m = mime.lowercase()
            return when {
                ext == "pdf" || m == "application/pdf" -> PDF
                ext == "docx" || m.contains("wordprocessingml") -> DOCX
                ext == "xlsx" || ext == "csv" || m.contains("spreadsheetml") -> XLSX
                ext == "pptx" || m.contains("presentationml") -> PPTX
                m.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp", "gif", "svg") -> IMAGE
                ext in setOf("md", "markdown") -> MARKDOWN
                ext in setOf(
                    "rs", "kt", "kts", "java", "py", "ts", "tsx", "js", "json", "toml",
                    "yaml", "yml", "xml", "html", "css", "sh", "sql", "txt", "log", "gradle"
                ) || m.startsWith("text/") || m == "application/json" -> CODE_TEXT
                else -> entries.firstOrNull { it.name.equals(ext, ignoreCase = true) } ?: BINARY
            }
        }
    }
}

sealed interface DocxBlock {
    data class Heading(val text: String, val level: Int) : DocxBlock
    data class Paragraph(val text: String) : DocxBlock
    data class BulletItem(val text: String) : DocxBlock
    data class TableGrid(val rows: List<List<String>>) : DocxBlock
}

data class DocxDocument(
    val title: String,
    val blocks: List<DocxBlock>
)

data class XlsxSheet(
    val name: String,
    val rows: List<List<String>>
)

data class XlsxWorkbook(
    val sheets: List<XlsxSheet>
)

data class PptxSlide(
    val slideNumber: Int,
    val title: String,
    val bodyBullets: List<String>,
    val speakerNotes: String = ""
)

data class PptxDeck(
    val slides: List<PptxSlide>
)
