package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.chat.ChatReducer
import com.example.chat.ChatRow
import com.example.chat.EarlierRow
import com.example.chat.Repo
import com.example.chat.UserAttachment
import com.example.chat.buildRows
import com.example.data.ApiError
import com.example.data.ArtifactLoader
import com.example.data.ArtifactState
import com.example.data.AttachmentPayload
import com.example.data.Budget
import com.example.data.CancelRequest
import com.example.data.ChatRequest
import com.example.data.FileEntry
import com.example.data.ModelInfo
import com.example.data.OrchClient
import com.example.data.OrchPreferences
import com.example.data.OrchStream
import com.example.data.SessionSummary
import com.example.data.SseFrame
import com.example.data.TranscribeRequest
import com.example.data.WorkspaceInit
import com.example.data.toApiError
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.min

data class ChatUi(
    val sessionId: String,
    val rows: List<ChatRow> = emptyList(),
    val streaming: Boolean = false,
    val loading: Boolean = false,
    val repo: Repo? = null,
    val repoLocked: Boolean = false,
    val connecting: Boolean = false,
    val repoError: String? = null,
    val contextTokens: Long = 0
) {
    val canSend: Boolean get() = repo != null || repoLocked
}

data class PendingAttachment(
    val uri: Uri,
    val name: String,
    val size: Long,
    val isImage: Boolean
)

data class FilesUi(
    val query: String = "",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false
)

class OrchViewModel(application: Application) : AndroidViewModel(application) {

    private val client = OrchClient()
    private val api = client.api
    private val stream = OrchStream(client)
    private val preferences = OrchPreferences(application)
    private val artifactLoader = ArtifactLoader(api, application.cacheDir)
    private val resolver = application.contentResolver
    private val firestore = Firebase.firestore
    private val reducer = ChatReducer()

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _selectedModelId = MutableStateFlow("")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _budget = MutableStateFlow<Budget?>(null)
    val budget: StateFlow<Budget?> = _budget.asStateFlow()

    private val _chat = MutableStateFlow(ChatUi(sessionId = newSessionId()))
    val chat: StateFlow<ChatUi> = _chat.asStateFlow()

    private val _attachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val attachments: StateFlow<List<PendingAttachment>> = _attachments.asStateFlow()

    private val _files = MutableStateFlow(FilesUi())
    val files: StateFlow<FilesUi> = _files.asStateFlow()

    private val _artifact = MutableStateFlow<ArtifactState>(ArtifactState.Hidden)
    val artifact: StateFlow<ArtifactState> = _artifact.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private var streamJob: Job? = null
    private var titleJob: Job? = null
    private var filesJob: Job? = null
    private var historyJob: Job? = null
    private var publishPending = false
    private var lastPublish = 0L
    private var earliestSeq: Long? = null
    private var hasMore = false
    private var loadingEarlier = false

    init {
        viewModelScope.launch {
            val saved = preferences.modelId.first()
            val list = runCatching { api.models() }.getOrElse { report(it); emptyList() }
            _models.value = list
            _selectedModelId.value = list.firstOrNull { it.id == saved }?.id ?: list.firstOrNull()?.id.orEmpty()
        }
        refreshSessions()
        refreshBudget()
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private fun report(error: Throwable) {
        _notices.tryEmit(error.toApiError().message)
    }

    fun selectModel(id: String) {
        _selectedModelId.value = id
        viewModelScope.launch { preferences.setModelId(id) }
    }

    private suspend fun loadSessions(): List<SessionSummary>? {
        val remote = runCatching { api.sessions() }.getOrNull() ?: return null
        val ids = remote.mapTo(HashSet()) { it.id }
        val currentId = _chat.value.sessionId
        val local = _sessions.value.firstOrNull { it.id == currentId && it.id !in ids }
        _sessions.value = (remote + listOfNotNull(local)).sortedByDescending { it.updatedAt }
        return remote
    }

    fun refreshSessions() {
        viewModelScope.launch { loadSessions() }
    }

    fun refreshBudget() {
        viewModelScope.launch { runCatching { api.budget() }.onSuccess { _budget.value = it } }
    }

    fun onForeground() {
        refreshBudget()
        viewModelScope.launch {
            val remote = loadSessions() ?: return@launch
            val current = _chat.value
            val running = remote.firstOrNull { it.id == current.sessionId }?.running == true
            if (running && streamJob?.isActive != true && !current.streaming) {
                openSession(current.sessionId, force = true)
            }
        }
    }

    fun newChat() {
        stopTracking()
        reducer.clear()
        _attachments.value = emptyList()
        resetPaging()
        _chat.value = ChatUi(sessionId = newSessionId())
    }

    fun openSession(id: String, force: Boolean = false) {
        if (!force && id == _chat.value.sessionId && _chat.value.rows.isNotEmpty()) return
        stopTracking()
        reducer.clear()
        resetPaging()
        _attachments.value = emptyList()
        val summary = _sessions.value.firstOrNull { it.id == id }
        reducer.setContextTokens(summary?.contextTokens ?: 0)
        _chat.value = ChatUi(
            sessionId = id,
            loading = true,
            repo = Repo.fromWorkspacePath(summary?.workspacePath),
            contextTokens = summary?.contextTokens ?: 0
        )
        watchTitle(id)
        historyJob = viewModelScope.launch {
            val history = async { runCatching { api.history(id, HISTORY_LIMIT, null) } }
            val list = async { loadSessions() }
            val page = history.await().getOrElse {
                report(it)
                _chat.update { ui -> ui.copy(loading = false) }
                return@launch
            }
            val running = list.await()?.firstOrNull { it.id == id }?.running ?: (summary?.running == true)
            reducer.load(page.records)
            earliestSeq = page.earliestSeq
            hasMore = page.hasMore
            _chat.update { it.copy(loading = false) }
            if (running) {
                reducer.beginFollow()
                publishNow()
                runStream(id) { lastId, _ -> stream.resume(id, lastId) }
            } else {
                publishNow()
            }
        }
    }

    fun loadEarlier() {
        val before = earliestSeq ?: return
        val id = _chat.value.sessionId
        if (loadingEarlier || reducer.streaming || !hasMore) return
        loadingEarlier = true
        publishNow()
        viewModelScope.launch {
            runCatching { api.history(id, HISTORY_LIMIT, before) }.onSuccess { page ->
                if (_chat.value.sessionId == id) {
                    reducer.prepend(page.records)
                    earliestSeq = page.earliestSeq
                    hasMore = page.hasMore
                }
            }.onFailure(::report)
            loadingEarlier = false
            publishNow()
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteSession(id) }.onSuccess {
                _sessions.update { list -> list.filterNot { it.id == id } }
                if (_chat.value.sessionId == id) newChat()
            }.onFailure(::report)
        }
    }

    fun connectRepo(input: String) {
        val value = input.trim()
        if (value.isEmpty() || _chat.value.repoLocked || _chat.value.connecting) return
        viewModelScope.launch {
            _chat.update { it.copy(connecting = true, repoError = null) }
            runCatching { api.initWorkspace(WorkspaceInit(value)) }
                .onSuccess { workspace ->
                    _chat.update { it.copy(connecting = false, repo = Repo(workspace.workspace, workspace.repoUrl)) }
                }
                .onFailure { error ->
                    _chat.update { it.copy(connecting = false, repoError = error.toApiError().message) }
                }
        }
    }

    fun disconnectRepo() {
        if (_chat.value.repoLocked) return
        _chat.update { it.copy(repo = null, repoError = null) }
    }

    fun addAttachments(uris: List<Uri>) {
        val added = uris.mapNotNull(::describe)
        val total = _attachments.value.sumOf { it.size } + added.sumOf { it.size }
        if (total > MAX_ATTACHMENT_BYTES) {
            _notices.tryEmit("Attachments cannot exceed 18 MB.")
            return
        }
        _attachments.update { current -> current + added.filter { new -> current.none { it.uri == new.uri } } }
    }

    fun removeAttachment(uri: Uri) {
        _attachments.update { list -> list.filterNot { it.uri == uri } }
    }

    private fun describe(uri: Uri): PendingAttachment? = runCatching {
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.let { name = it }
                size = cursor.getLong(1)
            }
        }
        PendingAttachment(uri, name, size, resolver.getType(uri)?.startsWith("image/") == true)
    }.getOrNull()

    fun send(text: String) {
        val ui = _chat.value
        val prompt = text.trim()
        val pending = _attachments.value
        if (ui.streaming || !ui.canSend || (prompt.isEmpty() && pending.isEmpty())) return
        val model = _models.value.firstOrNull { it.id == _selectedModelId.value }
        if (model == null) {
            _notices.tryEmit("Models are not loaded yet.")
            return
        }
        val id = ui.sessionId
        reducer.beginTurn(prompt, pending.map { UserAttachment(name = it.name) })
        _attachments.value = emptyList()
        publishNow()
        runStream(id, canRetryFirst = false) { lastId, retry ->
            if (retry) {
                stream.resume(id, lastId)
            } else {
                flow {
                    val payloads = withContext(Dispatchers.IO) { pending.map(::encode) }
                    val request = ChatRequest(
                        sessionId = id,
                        prompt = prompt,
                        model = model.id,
                        workspace = ui.repo?.workspace,
                        repoUrl = ui.repo?.repoUrl,
                        attachments = payloads
                    )
                    emitAll(stream.chat(request))
                }
            }
        }
    }

    private fun encode(attachment: PendingAttachment): AttachmentPayload {
        val bytes = resolver.openInputStream(attachment.uri)?.use { it.readBytes() }
            ?: throw ApiError(-1, null, "Unable to read ${attachment.name}.")
        return AttachmentPayload(attachment.name, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    fun stop() {
        val id = _chat.value.sessionId
        viewModelScope.launch { runCatching { api.cancel(CancelRequest(id)) }.onFailure(::report) }
    }

    private fun runStream(
        sessionId: String,
        canRetryFirst: Boolean = true,
        open: (lastId: Long, retry: Boolean) -> Flow<SseFrame>
    ) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            var lastId = 0L
            var failures = 0
            var retry = false
            var received = false
            var finished = false
            while (!finished) {
                val error: ApiError? = try {
                    open(lastId, retry).collect { frame ->
                        if (!received) onFirstFrame(sessionId)
                        received = true
                        failures = 0
                        when {
                            frame.type == "orch.resync" -> lastId = 0
                            frame.id != null && frame.id > lastId -> lastId = frame.id
                        }
                        reducer.onFrame(frame.type, frame.data)
                        if (frame.type == "done") finished = true
                        publishSoon()
                    }
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    failure.toApiError()
                }
                if (finished) break
                failures++
                val giveUp = (error != null && !error.retryable) ||
                    failures > MAX_RETRIES || (!received && !canRetryFirst)
                if (giveUp) {
                    reducer.fail(error?.message ?: "Connection lost.")
                    break
                }
                delay(min(1000L shl (failures - 1), 8000L))
                retry = true
            }
            publishNow()
            refreshSessions()
            refreshBudget()
        }
    }

    private fun onFirstFrame(sessionId: String) {
        val ui = _chat.value
        if (_sessions.value.none { it.id == sessionId }) {
            val placeholder = SessionSummary(
                id = sessionId,
                workspacePath = ui.repo?.workspace,
                updatedAt = System.currentTimeMillis(),
                running = true
            )
            _sessions.update { listOf(placeholder) + it }
        }
        if (titleJob?.isActive != true) watchTitle(sessionId)
    }

    private fun watchTitle(sessionId: String) {
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            firestore.collection("orch_cloud_sessions").document(sessionId).snapshots()
                .catch { }
                .collect { doc ->
                    val title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: return@collect
                    _sessions.update { list -> list.map { if (it.id == sessionId) it.copy(title = title) else it } }
                }
        }
    }

    private fun stopTracking() {
        streamJob?.cancel()
        titleJob?.cancel()
        historyJob?.cancel()
        filesJob?.cancel()
        publishPending = false
        _files.value = FilesUi()
        _artifact.value = ArtifactState.Hidden
    }

    private fun resetPaging() {
        earliestSeq = null
        hasMore = false
        loadingEarlier = false
    }

    private fun publishSoon() {
        if (publishPending) return
        publishPending = true
        viewModelScope.launch {
            delay((PUBLISH_INTERVAL_MS - (SystemClock.uptimeMillis() - lastPublish)).coerceAtLeast(0))
            publishNow()
        }
    }

    private fun publishNow() {
        publishPending = false
        lastPublish = SystemClock.uptimeMillis()
        val rows = buildList {
            if (hasMore) add(EarlierRow(loadingEarlier))
            addAll(buildRows(reducer.messages))
        }
        _chat.update {
            it.copy(
                rows = rows,
                streaming = reducer.streaming,
                repoLocked = reducer.hasUserMessage,
                contextTokens = reducer.contextTokens
            )
        }
    }

    fun loadFiles(query: String) {
        val id = _chat.value.sessionId
        filesJob?.cancel()
        filesJob = viewModelScope.launch {
            _files.update { it.copy(query = query, loading = true) }
            if (query.isNotBlank()) delay(250)
            runCatching { api.files(id, query.ifBlank { null }, FILE_LIST_LIMIT) }
                .onSuccess { entries -> _files.update { it.copy(entries = entries, loading = false) } }
                .onFailure { error ->
                    _files.update { it.copy(entries = emptyList(), loading = false) }
                    report(error)
                }
        }
    }

    fun openFile(path: String) {
        val id = _chat.value.sessionId
        val name = path.trimEnd('/').substringAfterLast('/')
        _artifact.value = ArtifactState.Loading(name, path)
        viewModelScope.launch {
            _artifact.value = runCatching { artifactLoader.load(id, path) }
                .getOrElse { ArtifactState.Failed(name, it.toApiError().message) }
        }
    }

    fun closeArtifact() {
        _artifact.value = ArtifactState.Hidden
    }

    suspend fun transcribe(audioBase64: String, format: String): String? =
        runCatching { api.transcribe(TranscribeRequest(audioBase64, format)).text.trim() }
            .onFailure(::report)
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    companion object {
        private const val HISTORY_LIMIT = 200
        private const val PUBLISH_INTERVAL_MS = 48L
        private const val MAX_RETRIES = 6
        private const val MAX_ATTACHMENT_BYTES = 18L * 1024 * 1024
        private const val FILE_LIST_LIMIT = 500

        val factory = viewModelFactory {
            initializer { OrchViewModel(this[APPLICATION_KEY] as Application) }
        }
    }
}
