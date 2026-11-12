package com.example.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
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
import com.example.data.AttachmentSource
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
import com.example.data.runCatchingSuspend
import com.example.data.toApiError
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.min

@Immutable
data class ChatUi(
    val sessionId: String,
    val rows: List<ChatRow> = emptyList(),
    val streaming: Boolean = false,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val repo: Repo? = null,
    val repoLocked: Boolean = false,
    val connecting: Boolean = false,
    val repoError: String? = null,
    val contextTokens: Long = 0
) {
    val canSend: Boolean get() = !loadFailed && (repo != null || repoLocked)
}

@Immutable
data class PendingAttachment(
    val uri: Uri,
    val name: String,
    val size: Long,
    val isImage: Boolean
)

@Immutable
data class FilesUi(
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

class OrchViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OrchClient()
    private val api = client.api
    private val stream = OrchStream(client, application.cacheDir)
    private val preferences = OrchPreferences(application)
    private val artifactLoader = ArtifactLoader(api, application.cacheDir)
    private val resolver = application.contentResolver
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

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val _saveResults = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val saveResults: SharedFlow<String> = _saveResults.asSharedFlow()

    private var streamJob: Job? = null
    private var titleJob: Job? = null
    private var filesJob: Job? = null
    private var historyJob: Job? = null
    private var artifactJob: Job? = null
    private var modelsJob: Job? = null
    private var publishJob: Job? = null
    private var earliestSeq: Long? = null
    private var hasMore = false
    private var loadingEarlier = false
    private var earlierFailed = false
    private var pagingGeneration = 0
    private val deleting = HashSet<String>()

    init {
        loadModels(reportFailure = true)
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private fun notice(message: String) {
        _notices.tryEmit(message)
    }

    private fun report(error: Throwable) {
        notice(error.toApiError().message)
    }

    private fun loadModels(reportFailure: Boolean) {
        if (modelsJob?.isActive == true) return
        modelsJob = viewModelScope.launch {
            val saved = preferences.modelId.first()
            runCatchingSuspend { api.models() }
                .onSuccess { list ->
                    _models.value = list
                    val current = _selectedModelId.value
                    _selectedModelId.value = list.firstOrNull { it.id == current }?.id
                        ?: list.firstOrNull { it.id == saved }?.id
                        ?: list.firstOrNull()?.id.orEmpty()
                }
                .onFailure { if (reportFailure) report(it) }
        }
    }

    fun selectModel(id: String) {
        _selectedModelId.value = id
        viewModelScope.launch { preferences.setModelId(id) }
    }

    private suspend fun loadSessions(): List<SessionSummary>? {
        val remote = runCatchingSuspend { api.sessions() }.getOrNull() ?: return null
        val ids = remote.mapTo(HashSet()) { it.id }
        val previous = _sessions.value.associateBy { it.id }
        val currentId = _chat.value.sessionId
        val local = previous[currentId]?.takeIf { it.id !in ids }
        val merged = remote
            .filter { it.id !in deleting }
            .map { summary ->
                if (summary.title.isNullOrBlank()) summary.copy(title = previous[summary.id]?.title) else summary
            }
        _sessions.value = (merged + listOfNotNull(local)).sortedByDescending { it.updatedAt }
        return remote
    }

    fun refreshBudget() {
        viewModelScope.launch { runCatchingSuspend { api.budget() }.onSuccess { _budget.value = it } }
    }

    fun onForeground() {
        refreshBudget()
        if (_models.value.isEmpty()) loadModels(reportFailure = false)
        viewModelScope.launch {
            val remote = loadSessions() ?: return@launch
            val current = _chat.value
            val running = remote.firstOrNull { it.id == current.sessionId }?.running == true
            if (running && streamJob?.isActive != true && !current.streaming && !current.loading) {
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
        val sameSession = id == _chat.value.sessionId
        if (!force && sameSession && _chat.value.rows.isNotEmpty()) return
        stopTracking()
        reducer.clear()
        resetPaging()
        if (!sameSession) _attachments.value = emptyList()
        val summary = _sessions.value.firstOrNull { it.id == id }
        reducer.setContextTokens(summary?.contextTokens ?: 0)
        _chat.value = ChatUi(
            sessionId = id,
            loading = true,
            repo = Repo.fromWorkspacePath(summary?.workspacePath),
            contextTokens = summary?.contextTokens ?: 0
        )
        historyJob = viewModelScope.launch {
            val history = async { runCatchingSuspend { api.history(id, HISTORY_LIMIT, null) } }
            val list = async { loadSessions() }
            val page = history.await().getOrElse {
                report(it)
                _chat.update { ui -> ui.copy(loading = false, loadFailed = true) }
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
        earlierFailed = false
        val generation = pagingGeneration
        publishNow()
        viewModelScope.launch {
            val result = runCatchingSuspend { api.history(id, HISTORY_LIMIT, before) }
            if (generation != pagingGeneration) return@launch
            result
                .onSuccess { page ->
                    reducer.prepend(page.records)
                    earliestSeq = page.earliestSeq
                    hasMore = page.hasMore
                }
                .onFailure {
                    earlierFailed = true
                    report(it)
                }
            loadingEarlier = false
            publishNow()
        }
    }

    fun deleteSession(id: String) {
        deleting += id
        _sessions.update { list -> list.filterNot { it.id == id } }
        if (_chat.value.sessionId == id) newChat()
        viewModelScope.launch {
            val result = runCatchingSuspend { api.deleteSession(id) }
            deleting -= id
            result.onFailure {
                report(it)
                loadSessions()
            }
        }
    }

    fun connectRepo(input: String) {
        val value = input.trim()
        val id = _chat.value.sessionId
        if (value.isEmpty() || _chat.value.repoLocked || _chat.value.connecting) return
        viewModelScope.launch {
            _chat.update { it.copy(connecting = true, repoError = null) }
            runCatchingSuspend { api.initWorkspace(WorkspaceInit(value)) }
                .onSuccess { workspace ->
                    _chat.update {
                        if (it.sessionId != id) it
                        else it.copy(connecting = false, repo = Repo(workspace.workspace, workspace.repoUrl))
                    }
                }
                .onFailure { error ->
                    _chat.update {
                        if (it.sessionId != id) it
                        else it.copy(connecting = false, repoError = error.toApiError().message)
                    }
                }
        }
    }

    fun clearRepoError() {
        _chat.update { if (it.repoError == null) it else it.copy(repoError = null) }
    }

    fun disconnectRepo() {
        if (_chat.value.repoLocked) return
        _chat.update { it.copy(repo = null, repoError = null) }
    }

    fun addAttachments(uris: List<Uri>) {
        viewModelScope.launch {
            val known = _attachments.value.mapTo(HashSet()) { it.uri }
            val fresh = uris.distinct().filter { it !in known }
            val described = withContext(Dispatchers.IO) { fresh.map(::describe) }
            val unreadable = described.count { it == null }
            if (unreadable > 0) {
                notice(if (unreadable == 1) "Couldn't read that file." else "Couldn't read $unreadable files.")
            }
            val current = _attachments.value
            val added = described.filterNotNull().filter { item -> current.none { it.uri == item.uri } }
            if (current.sumOf { it.size } + added.sumOf { it.size } > MAX_ATTACHMENT_BYTES) {
                notice("Attachments cannot exceed 18 MB.")
                return@launch
            }
            _attachments.value = current + added
        }
    }

    fun removeAttachment(uri: Uri) {
        _attachments.update { list -> list.filterNot { it.uri == uri } }
    }

    private fun describe(uri: Uri): PendingAttachment? = runCatching {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        if (size < 0) size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        PendingAttachment(uri, name, size.coerceAtLeast(0), resolver.getType(uri)?.startsWith("image/") == true)
    }.getOrNull()

    fun send(text: String): Boolean {
        val ui = _chat.value
        val prompt = text.trim()
        val pending = _attachments.value
        if (ui.streaming || ui.loading || !ui.canSend || (prompt.isEmpty() && pending.isEmpty())) return false
        val model = _models.value.firstOrNull { it.id == _selectedModelId.value }
        if (model == null) {
            loadModels(reportFailure = false)
            notice("Models are not loaded yet. Trying again, send once they appear.")
            return false
        }
        val id = ui.sessionId
        val sources = pending.map { item -> AttachmentSource(item.name) { resolver.openInputStream(item.uri) } }
        reducer.beginTurn(
            prompt,
            pending.map { UserAttachment(name = it.name, previewUri = if (it.isImage) it.uri.toString() else null) }
        )
        _attachments.value = emptyList()
        publishNow()
        runStream(id, canRetryFirst = false) { lastId, retry ->
            if (retry) {
                stream.resume(id, lastId)
            } else {
                stream.chat(
                    ChatRequest(
                        sessionId = id,
                        prompt = prompt,
                        model = model.id,
                        workspace = ui.repo?.workspace,
                        repoUrl = ui.repo?.repoUrl
                    ),
                    sources
                )
            }
        }
        return true
    }

    fun stop() {
        val id = _chat.value.sessionId
        viewModelScope.launch { runCatchingSuspend { api.cancel(CancelRequest(id)) }.onFailure(::report) }
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
            var reloaded = false
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
                    reloaded = recoverAfterLoss(sessionId, received, error?.message ?: "Connection lost.")
                    break
                }
                delay(min(1000L shl (failures - 1), 8000L))
                retry = true
            }
            if (reloaded) return@launch
            publishNow()
            markIdle(sessionId)
            refreshBudget()
            trackTitle(sessionId)
        }
    }

    private suspend fun recoverAfterLoss(sessionId: String, received: Boolean, message: String): Boolean {
        if (received) {
            val running = loadSessions()?.firstOrNull { it.id == sessionId }?.running
            if (running == false) {
                notice("Live updates were interrupted ($message). Showing the saved result.")
                openSession(sessionId, force = true)
                return true
            }
        }
        reducer.fail(message)
        return false
    }

    private fun markIdle(sessionId: String) {
        _sessions.update { list ->
            list.map { if (it.id == sessionId && it.running) it.copy(running = false) else it }
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
    }

    private fun trackTitle(sessionId: String) {
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            repeat(TITLE_ATTEMPTS) {
                val title = loadSessions()?.firstOrNull { it.id == sessionId }?.title
                if (!title.isNullOrBlank()) return@launch
                delay(TITLE_RETRY_MS)
            }
        }
    }

    private fun stopTracking() {
        streamJob?.cancel()
        titleJob?.cancel()
        historyJob?.cancel()
        filesJob?.cancel()
        artifactJob?.cancel()
        publishJob?.cancel()
        _files.value = FilesUi()
        _artifact.value = ArtifactState.Hidden
    }

    private fun resetPaging() {
        pagingGeneration++
        earliestSeq = null
        hasMore = false
        loadingEarlier = false
        earlierFailed = false
    }

    private fun publishSoon() {
        if (publishJob?.isActive == true) return
        publishJob = viewModelScope.launch {
            delay(PUBLISH_INTERVAL_MS)
            publish()
        }
    }

    private fun publishNow() {
        publishJob?.cancel()
        publish()
    }

    private fun publish() {
        val rows = buildList {
            if (hasMore) add(EarlierRow(loadingEarlier, earlierFailed))
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
            _files.update { it.copy(loading = true, error = null) }
            if (query.isNotBlank()) delay(250)
            runCatchingSuspend { api.files(id, query.ifBlank { null }, FILE_LIST_LIMIT) }
                .onSuccess { entries -> _files.update { it.copy(entries = entries, loading = false, error = null) } }
                .onFailure { error ->
                    _files.update { it.copy(entries = emptyList(), loading = false, error = error.toApiError().message) }
                }
        }
    }

    fun openFile(path: String) {
        val id = _chat.value.sessionId
        val name = path.trimEnd('/').substringAfterLast('/')
        artifactJob?.cancel()
        _artifact.value = ArtifactState.Loading(name, path)
        artifactJob = viewModelScope.launch {
            _artifact.value = try {
                artifactLoader.load(id, path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                ArtifactState.Failed(name, path, failure.toApiError().message)
            }
        }
    }

    fun closeArtifact() {
        artifactJob?.cancel()
        _artifact.value = ArtifactState.Hidden
    }

    fun saveArtifact(destination: Uri) {
        val (path, name) = when (val state = _artifact.value) {
            is ArtifactState.Ready -> state.path to state.metadata.name
            is ArtifactState.Failed -> state.path to state.name
            else -> return
        }
        if (_saving.value) return
        val id = _chat.value.sessionId
        _saving.value = true
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    val file = artifactLoader.download(id, path, name)
                    val output = resolver.openOutputStream(destination, "wt")
                        ?: throw ApiError(-1, "Unable to write the file.")
                    output.use { sink -> file.inputStream().use { it.copyTo(sink) } }
                }
                "Saved $name"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                failure.toApiError().message
            } finally {
                _saving.value = false
            }
            _saveResults.tryEmit(message)
        }
    }

    suspend fun transcribe(audioBase64: String, format: String): String? =
        runCatchingSuspend { api.transcribe(TranscribeRequest(audioBase64, format)).text.trim() }
            .onFailure(::report)
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    override fun onCleared() {
        artifactLoader.clear()
        client.http.dispatcher.cancelAll()
        client.http.connectionPool.evictAll()
        client.http.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val HISTORY_LIMIT = 200
        private const val PUBLISH_INTERVAL_MS = 80L
        private const val MAX_RETRIES = 6
        private const val TITLE_ATTEMPTS = 5
        private const val TITLE_RETRY_MS = 2000L
        private const val MAX_ATTACHMENT_BYTES = 18L * 1024 * 1024
        private const val FILE_LIST_LIMIT = 500

        fun factory(application: Application) = viewModelFactory {
            initializer { OrchViewModel(application) }
        }
    }
}
