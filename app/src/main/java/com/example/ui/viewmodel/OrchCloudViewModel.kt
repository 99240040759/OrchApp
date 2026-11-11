package com.example.ui.viewmodel

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.R
import com.example.data.model.AntigravityStep
import com.example.data.model.ArtifactFormat
import com.example.data.model.AttachmentIn
import com.example.data.model.BudgetStatus
import com.example.data.model.ChatRequest
import com.example.data.model.ChatTurn
import com.example.data.model.DocxDocument
import com.example.data.model.FileEntry
import com.example.data.model.FileMetadata
import com.example.data.model.HistoryPage
import com.example.data.model.ModelInfo
import com.example.data.model.PptxDeck
import com.example.data.model.SessionId
import com.example.data.model.SessionSummary
import com.example.data.model.SseStreamEvent
import com.example.data.model.StepKind
import com.example.data.model.WorkspaceInfo
import com.example.data.model.XlsxWorkbook
import com.example.data.network.OrchCloudApiClient
import com.example.data.parser.OfficeAndArtifactParser
import com.example.data.preferences.OrchCloudPreferences
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

sealed interface ArtifactSheetState {
    data object Hidden : ArtifactSheetState
    data class Loading(val path: String, val name: String) : ArtifactSheetState
    data class ReadyPdf(
        val metadata: FileMetadata,
        val path: String,
        val pdfFile: File
    ) : ArtifactSheetState
    data class ReadyDocx(
        val metadata: FileMetadata,
        val path: String,
        val document: DocxDocument,
        val cacheFile: File
    ) : ArtifactSheetState
    data class ReadyXlsx(
        val metadata: FileMetadata,
        val path: String,
        val workbook: XlsxWorkbook,
        val cacheFile: File
    ) : ArtifactSheetState
    data class ReadyPptx(
        val metadata: FileMetadata,
        val path: String,
        val deck: PptxDeck,
        val cacheFile: File
    ) : ArtifactSheetState
    data class ReadyImage(
        val metadata: FileMetadata,
        val path: String,
        val dataUrl: String?,
        val localFile: File?
    ) : ArtifactSheetState
    data class ReadyText(
        val metadata: FileMetadata,
        val path: String,
        val content: String,
        val truncated: Boolean,
        val isMarkdown: Boolean,
        val cacheFile: File
    ) : ArtifactSheetState
    data class Error(
        val path: String,
        val name: String,
        val message: String
    ) : ArtifactSheetState
}

data class PendingAttachment(
    val name: String,
    val sizeBytes: Long,
    val dataBase64: String
)

class OrchCloudViewModel(
    private val preferences: OrchCloudPreferences,
    private val apiClient: OrchCloudApiClient,
    private val firestore: FirebaseFirestore,
    private val cacheDir: File
) : ViewModel() {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _selectedModelId = MutableStateFlow("")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _budgetStatus = MutableStateFlow(BudgetStatus())
    val budgetStatus: StateFlow<BudgetStatus> = _budgetStatus.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _activeWorkspace = MutableStateFlow(WorkspaceInfo())
    val activeWorkspace: StateFlow<WorkspaceInfo> = _activeWorkspace.asStateFlow()

    private val _isInitializingWorkspace = MutableStateFlow(false)
    val isInitializingWorkspace: StateFlow<Boolean> = _isInitializingWorkspace.asStateFlow()

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _lastEventId = MutableStateFlow<ULong>(0uL)
    val lastEventId: StateFlow<ULong> = _lastEventId.asStateFlow()

    private val _streamStatusBanner = MutableStateFlow<String?>(null)
    val streamStatusBanner: StateFlow<String?> = _streamStatusBanner.asStateFlow()

    private val _sandboxFiles = MutableStateFlow<List<FileEntry>>(emptyList())
    val sandboxFiles: StateFlow<List<FileEntry>> = _sandboxFiles.asStateFlow()

    private val _fileSearchQuery = MutableStateFlow("")
    val fileSearchQuery: StateFlow<String> = _fileSearchQuery.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()

    private val _artifactSheetState = MutableStateFlow<ArtifactSheetState>(ArtifactSheetState.Hidden)
    val artifactSheetState: StateFlow<ArtifactSheetState> = _artifactSheetState.asStateFlow()

    private val _isTranscribingVoice = MutableStateFlow(false)
    val isTranscribingVoice: StateFlow<Boolean> = _isTranscribingVoice.asStateFlow()

    private var activeStreamJob: Job? = null
    private var activeSessionStepsJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.defaultModelFlow.collect { modelId ->
                if (modelId.isNotBlank()) {
                    _selectedModelId.value = modelId
                }
            }
        }
        viewModelScope.launch {
            preferences.defaultRepoUrlFlow.collect { repo ->
                if (repo.isNotBlank() && _activeWorkspace.value.repoUrl.isBlank()) {
                    _activeWorkspace.update { it.copy(repoUrl = repo) }
                }
            }
        }
        syncFcmToken()
        observeFirestoreState()
        refreshServerState()
    }

    fun syncFcmToken() {
        val user = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
                ?: preferences.fcmTokenFlow.firstOrNull()
            if (!token.isNullOrBlank()) {
                preferences.setFcmToken(token)
                runCatching {
                    firestore.collection("users")
                        .document(user.uid)
                        .set(
                            mapOf(
                                "fcm_token" to token,
                                "fcmToken" to token,
                                "email" to (user.email ?: ""),
                                "updated_at" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }
            }
        }
    }

    private fun observeFirestoreState() {
        val uid = Firebase.auth.currentUser?.uid ?: return

        viewModelScope.launch {
            firestore.collection("orch_cloud_sessions")
                .whereEqualTo("user_id", uid)
                .snapshots()
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Firestore sessions listener error: ${e.message}")
                }
                .collect { snapshot ->
                    val firestoreSessions = snapshot.documents.mapNotNull { doc ->
                        parseSessionSummaryFromFirestore(doc)
                    }
                    if (firestoreSessions.isNotEmpty()) {
                        _sessions.update { existing ->
                            val byId = LinkedHashMap<String, SessionSummary>()
                            existing.forEach { byId[it.id] = it }
                            firestoreSessions.forEach { byId[it.id] = it }
                            byId.values.sortedByDescending { it.updatedAt }
                        }
                    }
                }
        }

        viewModelScope.launch {
            firestore.collection("budget")
                .document(uid)
                .snapshots()
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Firestore budget listener error: ${e.message}")
                }
                .collect { doc ->
                    if (doc.exists()) {
                        val cost = (doc.get("cost_usd") as? Number)?.toDouble()
                            ?: (doc.get("costUsd") as? Number)?.toDouble()
                            ?: _budgetStatus.value.costUsd
                        val limit = (doc.get("limit_usd") as? Number)?.toDouble()
                            ?: (doc.get("limitUsd") as? Number)?.toDouble()
                            ?: _budgetStatus.value.limitUsd
                        val period = doc.getString("period") ?: _budgetStatus.value.period
                        val resetsOn = doc.getString("resets_on")
                            ?: doc.getString("resetsOn")
                            ?: _budgetStatus.value.resetsOn
                        val allowed = doc.getBoolean("allowed") ?: (limit <= 0.0 || cost < limit)
                        _budgetStatus.value = BudgetStatus(
                            costUsd = cost,
                            limitUsd = limit,
                            period = period,
                            resetsOn = resetsOn,
                            allowed = allowed
                        )
                    }
                }
        }
    }

    private fun parseSessionSummaryFromFirestore(doc: DocumentSnapshot): SessionSummary? {
        val id = doc.id.takeIf { it.isNotBlank() } ?: return null
        val title = doc.getString("title")
        val workspacePath = doc.getString("workspace_path")
            ?: doc.getString("workspacePath")
            ?: doc.getString("workspace")
        val updatedRaw = doc.get("updated_at") ?: doc.get("updatedAt")
        val updatedAtMs = when (updatedRaw) {
            is Timestamp -> updatedRaw.toDate().time
            is Number -> updatedRaw.toLong()
            else -> 0L
        }
        val contextTokens = (doc.get("context_tokens") as? Number)?.toLong()
            ?: (doc.get("contextTokens") as? Number)?.toLong() ?: 0L
        val inputTokens = (doc.get("input_tokens") as? Number)?.toLong()
            ?: (doc.get("inputTokens") as? Number)?.toLong() ?: 0L
        val outputTokens = (doc.get("output_tokens") as? Number)?.toLong()
            ?: (doc.get("outputTokens") as? Number)?.toLong() ?: 0L
        val cachedTokens = (doc.get("cached_tokens") as? Number)?.toLong()
            ?: (doc.get("cachedTokens") as? Number)?.toLong() ?: 0L
        val reasoningTokens = (doc.get("reasoning_tokens") as? Number)?.toLong()
            ?: (doc.get("reasoningTokens") as? Number)?.toLong() ?: 0L
        val runStatus = doc.getString("run_status") ?: doc.getString("status")
        val runningBool = doc.getBoolean("running") ?: (runStatus == "running")

        return SessionSummary(
            id = id,
            title = title,
            workspacePath = workspacePath,
            updatedAt = updatedAtMs,
            contextTokens = contextTokens,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedTokens = cachedTokens,
            reasoningTokens = reasoningTokens,
            running = runningBool
        )
    }

    fun refreshServerState() {
        viewModelScope.launch {
            apiClient.fetchCloudModels().onSuccess { remoteModels ->
                _models.value = remoteModels
                val currentSelected = _selectedModelId.value
                if ((currentSelected.isBlank() || remoteModels.none { it.id == currentSelected }) && remoteModels.isNotEmpty()) {
                    val firstId = remoteModels.first().id
                    _selectedModelId.value = firstId
                    preferences.setDefaultModel(firstId)
                }
            }
            apiClient.fetchBudget().onSuccess { budget ->
                _budgetStatus.value = budget
            }
            apiClient.listSessions().onSuccess { list ->
                _sessions.value = list.sortedByDescending { it.updatedAt }
            }
        }
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        viewModelScope.launch {
            preferences.setDefaultModel(modelId)
        }
    }

    fun initializeWorkspaceRepo(repoInput: String, onResult: (WorkspaceInfo) -> Unit = {}) {
        val trimmed = repoInput.trim()
        if (trimmed.isEmpty()) return
        _isInitializingWorkspace.value = true
        _streamStatusBanner.value = "Connecting workspace..."
        viewModelScope.launch {
            apiClient.initWorkspace(trimmed)
                .onSuccess { resolved ->
                    _isInitializingWorkspace.value = false
                    _activeWorkspace.value = resolved
                    _streamStatusBanner.value = null
                    preferences.setDefaultRepoUrl(resolved.repoUrl.ifBlank { trimmed })
                    _activeSessionId.value?.let { refreshSandboxFiles(it) }
                    onResult(resolved)
                }
                .onFailure { err ->
                    _isInitializingWorkspace.value = false
                    _streamStatusBanner.value = err.localizedMessage ?: "Unable to initialize workspace."
                }
        }
    }

    fun startNewSession(repoInput: String? = null): String {
        activeStreamJob?.cancel()
        activeSessionStepsJob?.cancel()
        val newId = SessionId.generate().value
        _activeSessionId.value = newId
        _turns.value = emptyList()
        _lastEventId.value = 0uL
        _streamStatusBanner.value = null
        _sandboxFiles.value = emptyList()
        if (!repoInput.isNullOrBlank()) {
            initializeWorkspaceRepo(repoInput)
        }
        return newId
    }

    fun openSession(sessionId: String) {
        if (!SessionId.isValid(sessionId)) return
        activeStreamJob?.cancel()
        activeSessionStepsJob?.cancel()
        _activeSessionId.value = sessionId
        _turns.value = emptyList()
        _sandboxFiles.value = emptyList()
        _streamStatusBanner.value = null

        observeSessionStepsInFirestore(sessionId)

        viewModelScope.launch {
            val savedCursor = preferences.getLastEventId(sessionId)
            _lastEventId.value = savedCursor

            apiClient.fetchSessionWorkspace(sessionId).onSuccess { ws ->
                if (ws.repoUrl.isNotBlank() || ws.workspace.isNotBlank()) {
                    _activeWorkspace.value = ws
                }
            }

            apiClient.fetchSessionHistory(sessionId, limit = 200).onSuccess { historyPage ->
                val rebuilt = rebuildTurnsFromHistory(historyPage)
                if (rebuilt.isNotEmpty()) {
                    _turns.value = rebuilt
                }
            }

            refreshSandboxFiles(sessionId)

            val sessionSummary = _sessions.value.firstOrNull { it.id == sessionId }
            if (sessionSummary?.running == true) {
                resumeActiveRun(sessionId, lastEventId = savedCursor)
            }
        }
    }

    private fun observeSessionStepsInFirestore(sessionId: String) {
        activeSessionStepsJob = viewModelScope.launch {
            firestore.collection("orch_cloud_sessions")
                .document(sessionId)
                .collection("steps")
                .orderBy("seq")
                .snapshots()
                .catch { e ->
                    if (e is CancellationException) throw e
                }
                .collect { snapshot ->
                    if (_isStreaming.value || snapshot.isEmpty) return@collect
                    val records = snapshot.documents.mapNotNull { doc ->
                        val seq = (doc.get("seq") as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: return@mapNotNull null
                        val turn = (doc.get("turn") as? Number)?.toLong() ?: 1L
                        val rawRecord = doc.get("record")
                        val jsonObj = when (rawRecord) {
                            is String -> runCatching { jsonParser.parseToJsonElement(rawRecord).jsonObject }.getOrNull()
                            is Map<*, *> -> mapToJsonObject(rawRecord)
                            else -> mapToJsonObject(doc.data.orEmpty())
                        } ?: return@mapNotNull null
                        com.example.data.model.StoredRecord(seq = seq, turn = turn, record = jsonObj)
                    }
                    if (records.isNotEmpty() && _turns.value.isEmpty()) {
                        _turns.value = rebuildTurnsFromHistory(HistoryPage(records = records))
                    }
                }
        }
    }

    private fun mapToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonPrimitive("")
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Iterable<*> -> JsonArray(value.map { mapToJsonElement(it) })
        is Map<*, *> -> mapToJsonObject(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun mapToJsonObject(map: Map<*, *>): JsonObject {
        val entries = LinkedHashMap<String, JsonElement>()
        for ((k, v) in map) {
            if (k != null && v != null) {
                entries[k.toString()] = mapToJsonElement(v)
            }
        }
        return JsonObject(entries)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            apiClient.deleteSession(sessionId).onSuccess {
                _sessions.update { list -> list.filterNot { it.id == sessionId } }
                if (_activeSessionId.value == sessionId) {
                    activeSessionStepsJob?.cancel()
                    _activeSessionId.value = null
                    _turns.value = emptyList()
                    _sandboxFiles.value = emptyList()
                }
            }.onFailure { err ->
                _streamStatusBanner.value = err.localizedMessage ?: "Unable to delete session."
            }
        }
    }

    fun addAttachment(attachment: PendingAttachment) {
        _pendingAttachments.update { current ->
            val totalBytes = current.sumOf { it.sizeBytes } + attachment.sizeBytes
            if (totalBytes <= 18L * 1024L * 1024L) {
                current + attachment
            } else {
                _streamStatusBanner.value = "Attachments cannot exceed 18 MB."
                current
            }
        }
    }

    fun removeAttachment(name: String) {
        _pendingAttachments.update { list -> list.filterNot { it.name == name } }
    }

    fun sendPrompt(promptText: String) {
        val trimmed = promptText.trim()
        val attachments = _pendingAttachments.value
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (_isStreaming.value) return

        val sessionId = _activeSessionId.value ?: startNewSession()
        val nextTurnNum = (_turns.value.maxOfOrNull { it.turnNumber } ?: 0L) + 1L
        val workspace = _activeWorkspace.value
        val modelId = _selectedModelId.value.ifBlank {
            _models.value.firstOrNull()?.id.orEmpty()
        }

        val newTurn = ChatTurn(
            turnNumber = nextTurnNum,
            userPrompt = trimmed,
            attachments = attachments.map { it.name },
            steps = emptyList(),
            isStreaming = true
        )
        _turns.update { it + newTurn }
        _pendingAttachments.value = emptyList()
        _isStreaming.value = true
        _streamStatusBanner.value = null

        val request = ChatRequest(
            sessionId = sessionId,
            prompt = trimmed,
            model = modelId,
            workspace = workspace.workspace.takeIf { it.isNotBlank() },
            repoUrl = workspace.repoUrl.takeIf { it.isNotBlank() },
            attachments = attachments.map { AttachmentIn(name = it.name, dataBase64 = it.dataBase64) }
        )

        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            apiClient.streamChat(request).collect { event ->
                handleSseEvent(
                    sessionId = sessionId,
                    turnNumber = nextTurnNum,
                    event = event
                )
            }
        }
    }

    fun resumeActiveRun(sessionId: String = _activeSessionId.value.orEmpty(), lastEventId: ULong? = null) {
        if (sessionId.isBlank()) return
        val cursor = lastEventId ?: _lastEventId.value
        val activeTurnNum = _turns.value.lastOrNull()?.turnNumber ?: 1L
        _isStreaming.value = true
        _streamStatusBanner.value = null
        _turns.update { list ->
            if (list.isEmpty()) {
                listOf(ChatTurn(turnNumber = activeTurnNum, userPrompt = "", isStreaming = true))
            } else {
                list.mapIndexed { idx, t ->
                    if (idx == list.lastIndex) t.copy(isStreaming = true, errorCode = null, statusNote = null) else t
                }
            }
        }

        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            apiClient.resumeChat(sessionId, cursor).collect { event ->
                handleSseEvent(
                    sessionId = sessionId,
                    turnNumber = activeTurnNum,
                    event = event
                )
            }
        }
    }

    fun cancelActiveRun() {
        val sessionId = _activeSessionId.value ?: return
        activeStreamJob?.cancel()
        _isStreaming.value = false
        _streamStatusBanner.value = null
        _turns.update { list ->
            list.mapIndexed { idx, turn ->
                if (idx == list.lastIndex) {
                    turn.copy(isStreaming = false, statusNote = "Stopped")
                } else {
                    turn
                }
            }
        }
        viewModelScope.launch {
            apiClient.cancelRun(sessionId).onSuccess {
                refreshServerState()
            }
        }
    }

    private suspend fun handleSseEvent(
        sessionId: String,
        turnNumber: Long,
        event: SseStreamEvent
    ) {
        event.eventId?.let { id ->
            _lastEventId.value = id
            preferences.setLastEventId(sessionId, id)
        }

        when (event) {
            is SseStreamEvent.KeepAlive -> Unit
            is SseStreamEvent.InteractionCreated -> {
                if (event.environmentId.isNotBlank()) {
                    _activeWorkspace.update { it.copy(environmentId = event.environmentId) }
                }
            }
            is SseStreamEvent.OrchResync -> {
                _lastEventId.value = 0uL
                preferences.setLastEventId(sessionId, 0uL)
                _turns.update { list ->
                    list.map { turn ->
                        if (turn.turnNumber == turnNumber) turn.copy(steps = emptyList()) else turn
                    }
                }
            }
            is SseStreamEvent.StepStart -> {
                val parsedStep = buildStepFromJsonObject(event.index, event.step)
                upsertStep(turnNumber, parsedStep)
            }
            is SseStreamEvent.StepDelta -> {
                val deltaText = extractDeltaText(event.delta)
                val deltaArgs = extractDeltaArgs(event.delta)
                val deltaKind = extractDeltaKindOverride(event.delta)
                appendStepDelta(
                    turnNumber = turnNumber,
                    stepIndex = event.index,
                    deltaText = deltaText,
                    deltaArgs = deltaArgs,
                    kindOverride = deltaKind
                )
            }
            is SseStreamEvent.StepStop -> {
                finalizeStepUsage(
                    turnNumber = turnNumber,
                    stepIndex = event.index,
                    inputTokens = event.inputTokens,
                    outputTokens = event.outputTokens,
                    thoughtTokens = event.thoughtTokens
                )
            }
            is SseStreamEvent.InteractionStatusUpdate -> {
                if (event.status == "cancelled" || event.status == "completed" || event.status == "failed") {
                    _isStreaming.value = false
                }
            }
            is SseStreamEvent.InteractionCompleted -> {
                val totalContext = event.inputTokens + event.outputTokens + event.thoughtTokens
                if (totalContext > 0L) {
                    _sessions.update { list ->
                        list.map { s ->
                            if (s.id == sessionId) {
                                s.copy(
                                    contextTokens = totalContext,
                                    inputTokens = event.inputTokens,
                                    outputTokens = event.outputTokens,
                                    reasoningTokens = event.thoughtTokens
                                )
                            } else {
                                s
                            }
                        }
                    }
                }
            }
            is SseStreamEvent.StreamError -> {
                if (event.code == "stream_interrupted") {
                    _streamStatusBanner.value = "Reconnecting stream..."
                    val cursor = _lastEventId.value
                    viewModelScope.launch {
                        delay(1200L)
                        val sessionStillRunning = _sessions.value.firstOrNull { it.id == sessionId }?.running ?: true
                        if (sessionStillRunning && _activeSessionId.value == sessionId) {
                            resumeActiveRun(sessionId, cursor)
                        } else {
                            _isStreaming.value = false
                            _streamStatusBanner.value = null
                        }
                    }
                    return
                }
                _isStreaming.value = false
                _streamStatusBanner.value = null
                _turns.update { list ->
                    list.map { turn ->
                        if (turn.turnNumber == turnNumber) {
                            turn.copy(
                                isStreaming = false,
                                errorCode = event.code,
                                statusNote = event.message
                            )
                        } else {
                            turn
                        }
                    }
                }
            }
            is SseStreamEvent.Done -> {
                _isStreaming.value = false
                _streamStatusBanner.value = null
                _turns.update { list ->
                    list.map { turn ->
                        if (turn.turnNumber == turnNumber) {
                            turn.copy(isStreaming = false)
                        } else {
                            turn
                        }
                    }
                }
                refreshServerState()
                refreshSandboxFiles(sessionId)
            }
        }
    }

    private fun buildStepFromJsonObject(index: Int, stepObj: JsonObject): AntigravityStep {
        val rawType = stepObj["type"]?.jsonPrimitive?.contentOrNull
            ?: stepObj["kind"]?.jsonPrimitive?.contentOrNull
            ?: "model_output"
        val kind = StepKind.fromWire(rawType)
        val functionObj = stepObj["function"] as? JsonObject
        val toolName = stepObj["name"]?.jsonPrimitive?.contentOrNull
            ?: stepObj["tool_name"]?.jsonPrimitive?.contentOrNull
            ?: functionObj?.get("name")?.jsonPrimitive?.contentOrNull
            ?: stepObj["command"]?.jsonPrimitive?.contentOrNull
        val argsText = extractArgsString(stepObj) ?: extractArgsString(functionObj)
        val initialContent = extractStepInitialContent(stepObj)
        val title = when (kind) {
            StepKind.FUNCTION_CALL, StepKind.FUNCTION_RESULT -> toolName ?: kind.label
            StepKind.GOOGLE_SEARCH -> initialContent.ifBlank { kind.label }
            StepKind.URL_CONTEXT -> initialContent.ifBlank { kind.label }
            StepKind.CODE_EXECUTION -> toolName ?: initialContent.lineSequence().firstOrNull()?.trim() ?: kind.label
            else -> toolName ?: kind.label
        }
        return AntigravityStep(
            index = index,
            kind = kind,
            rawType = rawType,
            title = title,
            content = initialContent,
            toolName = toolName,
            toolArgsJson = argsText
        )
    }

    private fun extractArgsString(obj: JsonObject?): String? {
        if (obj == null) return null
        val rawArgs = obj["arguments"] ?: obj["args"] ?: obj["input"] ?: obj["code"] ?: return null
        return when (rawArgs) {
            is JsonPrimitive -> rawArgs.contentOrNull?.takeIf { it.isNotBlank() }
            else -> rawArgs.toString().takeIf { it.isNotBlank() && it != "{}" && it != "null" }
        }
    }

    private fun extractStepInitialContent(obj: JsonObject): String {
        obj["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        obj["thought"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        obj["output"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        obj["result"]?.let { res ->
            when (res) {
                is JsonPrimitive -> res.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                else -> return res.toString()
            }
        }
        (obj["queries"] as? JsonArray)?.let { arr ->
            val joined = arr.mapNotNull { it.jsonPrimitive.contentOrNull }.filter { it.isNotBlank() }.joinToString(" · ")
            if (joined.isNotBlank()) return joined
        }
        obj["query"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        (obj["urls"] as? JsonArray)?.let { arr ->
            val joined = arr.mapNotNull { it.jsonPrimitive.contentOrNull }.filter { it.isNotBlank() }.joinToString(" · ")
            if (joined.isNotBlank()) return joined
        }
        obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    private fun upsertStep(turnNumber: Long, step: AntigravityStep) {
        _turns.update { list ->
            list.map { turn ->
                if (turn.turnNumber == turnNumber) {
                    val existingIdx = turn.steps.indexOfFirst { it.index == step.index }
                    val updatedSteps = if (existingIdx >= 0) {
                        val prev = turn.steps[existingIdx]
                        val merged = step.copy(
                            content = if (step.content.isNotBlank()) step.content else prev.content,
                            toolName = step.toolName ?: prev.toolName,
                            toolArgsJson = step.toolArgsJson ?: prev.toolArgsJson
                        )
                        turn.steps.toMutableList().apply { set(existingIdx, merged) }
                    } else {
                        turn.steps + step
                    }
                    turn.copy(steps = updatedSteps)
                } else {
                    turn
                }
            }
        }
    }

    private fun appendStepDelta(
        turnNumber: Long,
        stepIndex: Int,
        deltaText: String,
        deltaArgs: String?,
        kindOverride: StepKind?
    ) {
        if (deltaText.isEmpty() && deltaArgs.isNullOrEmpty()) return
        _turns.update { list ->
            list.map { turn ->
                if (turn.turnNumber == turnNumber) {
                    val existingIdx = turn.steps.indexOfFirst { it.index == stepIndex }
                    val updatedSteps = if (existingIdx >= 0) {
                        turn.steps.map { step ->
                            if (step.index == stepIndex) {
                                val nextKind = if (step.kind == StepKind.MODEL_OUTPUT && kindOverride != null) {
                                    kindOverride
                                } else {
                                    step.kind
                                }
                                val nextArgs = when {
                                    deltaArgs.isNullOrEmpty() -> step.toolArgsJson
                                    step.toolArgsJson.isNullOrEmpty() -> deltaArgs
                                    else -> step.toolArgsJson + deltaArgs
                                }
                                step.copy(
                                    kind = nextKind,
                                    content = step.content + deltaText,
                                    toolArgsJson = nextArgs
                                )
                            } else {
                                step
                            }
                        }
                    } else {
                        val resolvedKind = kindOverride ?: StepKind.MODEL_OUTPUT
                        turn.steps + AntigravityStep(
                            index = stepIndex,
                            kind = resolvedKind,
                            rawType = resolvedKind.wireName,
                            title = resolvedKind.label,
                            content = deltaText,
                            toolArgsJson = deltaArgs
                        )
                    }
                    turn.copy(steps = updatedSteps)
                } else {
                    turn
                }
            }
        }
    }

    private fun finalizeStepUsage(
        turnNumber: Long,
        stepIndex: Int,
        inputTokens: Long,
        outputTokens: Long,
        thoughtTokens: Long
    ) {
        _turns.update { list ->
            list.map { turn ->
                if (turn.turnNumber == turnNumber) {
                    turn.copy(
                        steps = turn.steps.map { step ->
                            if (step.index == stepIndex) {
                                step.copy(
                                    isFinished = true,
                                    inputTokens = inputTokens,
                                    outputTokens = outputTokens,
                                    thoughtTokens = thoughtTokens
                                )
                            } else {
                                step
                            }
                        }
                    )
                } else {
                    turn
                }
            }
        }
    }

    private fun extractDeltaKindOverride(delta: JsonElement): StepKind? {
        val obj = delta as? JsonObject ?: return null
        val typeRaw = obj["type"]?.jsonPrimitive?.contentOrNull
        if (!typeRaw.isNullOrBlank()) {
            return StepKind.fromWire(typeRaw)
        }
        return when {
            obj.containsKey("thought") -> StepKind.THOUGHT
            obj.containsKey("queries") || obj.containsKey("query") -> StepKind.GOOGLE_SEARCH
            obj.containsKey("urls") || obj.containsKey("url") -> StepKind.URL_CONTEXT
            obj.containsKey("code") || obj.containsKey("output") -> StepKind.CODE_EXECUTION
            obj.containsKey("arguments") -> StepKind.FUNCTION_CALL
            else -> null
        }
    }

    private fun extractDeltaArgs(delta: JsonElement): String? {
        val obj = delta as? JsonObject ?: return null
        return extractArgsString(obj)
    }

    private fun extractDeltaText(delta: JsonElement): String {
        return when (delta) {
            is JsonPrimitive -> delta.contentOrNull.orEmpty()
            is JsonObject -> {
                delta["text"]?.jsonPrimitive?.contentOrNull?.let { return it }
                delta["thought"]?.jsonPrimitive?.contentOrNull?.let { return it }
                delta["output"]?.jsonPrimitive?.contentOrNull?.let { return it }
                (delta["queries"] as? JsonArray)?.let { arr ->
                    val joined = arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }.joinToString(" · ")
                    if (joined.isNotBlank()) return joined
                }
                delta["query"]?.jsonPrimitive?.contentOrNull?.let { return it }
                (delta["urls"] as? JsonArray)?.let { arr ->
                    val joined = arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }.joinToString(" · ")
                    if (joined.isNotBlank()) return joined
                }
                delta["url"]?.jsonPrimitive?.contentOrNull?.let { return it }
                delta["result"]?.let { res ->
                    return when (res) {
                        is JsonPrimitive -> res.contentOrNull.orEmpty()
                        else -> res.toString()
                    }
                }
                delta["content"]?.let { c ->
                    return when (c) {
                        is JsonPrimitive -> c.contentOrNull.orEmpty()
                        is JsonArray -> c.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }.joinToString("")
                        else -> c.toString()
                    }
                }
                ""
            }
            else -> ""
        }
    }

    fun updateFileSearchQuery(query: String) {
        _fileSearchQuery.value = query
        val sessionId = _activeSessionId.value ?: return
        refreshSandboxFiles(sessionId, query)
    }

    fun refreshSandboxFiles(sessionId: String, query: String? = _fileSearchQuery.value) {
        viewModelScope.launch {
            apiClient.listSandboxFiles(sessionId, query?.takeIf { it.isNotBlank() }).onSuccess { files ->
                _sandboxFiles.value = files
            }.onFailure { err ->
                Log.w(TAG, "Failed to list files for session $sessionId", err)
            }
        }
    }

    fun openArtifactInBottomSheet(path: String, displayName: String? = null) {
        val name = displayName ?: path.substringAfterLast('/')
        val sessionId = _activeSessionId.value
        if (sessionId.isNullOrBlank()) {
            _artifactSheetState.value = ArtifactSheetState.Error(
                path = path,
                name = name,
                message = "Select or start a session first to open files."
            )
            return
        }
        _artifactSheetState.value = ArtifactSheetState.Loading(path = path, name = name)

        viewModelScope.launch {
            val metadataResult = apiClient.fetchFileMetadata(sessionId, path)
            val metadata = metadataResult.getOrElse { err ->
                _artifactSheetState.value = ArtifactSheetState.Error(
                    path = path,
                    name = name,
                    message = err.localizedMessage ?: "Unable to load file metadata."
                )
                return@launch
            }

            val format = ArtifactFormat.resolve(metadata.extension, metadata.mime)
            val artifactsDir = File(cacheDir, "artifacts").apply { mkdirs() }
            val safeFile = File(artifactsDir, name.replace(Regex("[^a-zA-Z0-9._\\-]"), "_"))

            try {
                when (format) {
                    ArtifactFormat.PDF -> {
                        val bytes = apiClient.fetchFileBinary(sessionId, path).getOrThrow()
                        withContext(Dispatchers.IO) { safeFile.writeBytes(bytes) }
                        _artifactSheetState.value = ArtifactSheetState.ReadyPdf(
                            metadata = metadata.copy(sizeBytes = bytes.size.toLong()),
                            path = path,
                            pdfFile = safeFile
                        )
                    }
                    ArtifactFormat.DOCX -> {
                        val bytes = apiClient.fetchFileBinary(sessionId, path).getOrThrow()
                        withContext(Dispatchers.IO) { safeFile.writeBytes(bytes) }
                        val parsed = withContext(Dispatchers.Default) {
                            OfficeAndArtifactParser.parseDocx(metadata.name, bytes)
                        }
                        _artifactSheetState.value = ArtifactSheetState.ReadyDocx(
                            metadata = metadata.copy(sizeBytes = bytes.size.toLong()),
                            path = path,
                            document = parsed,
                            cacheFile = safeFile
                        )
                    }
                    ArtifactFormat.XLSX -> {
                        val bytes = apiClient.fetchFileBinary(sessionId, path).getOrThrow()
                        withContext(Dispatchers.IO) { safeFile.writeBytes(bytes) }
                        val parsed = withContext(Dispatchers.Default) {
                            OfficeAndArtifactParser.parseXlsxOrCsv(metadata.name, bytes)
                        }
                        _artifactSheetState.value = ArtifactSheetState.ReadyXlsx(
                            metadata = metadata.copy(sizeBytes = bytes.size.toLong()),
                            path = path,
                            workbook = parsed,
                            cacheFile = safeFile
                        )
                    }
                    ArtifactFormat.PPTX -> {
                        val bytes = apiClient.fetchFileBinary(sessionId, path).getOrThrow()
                        withContext(Dispatchers.IO) { safeFile.writeBytes(bytes) }
                        val parsed = withContext(Dispatchers.Default) {
                            OfficeAndArtifactParser.parsePptx(bytes)
                        }
                        _artifactSheetState.value = ArtifactSheetState.ReadyPptx(
                            metadata = metadata.copy(sizeBytes = bytes.size.toLong()),
                            path = path,
                            deck = parsed,
                            cacheFile = safeFile
                        )
                    }
                    ArtifactFormat.IMAGE -> {
                        val dataUrl = apiClient.fetchFileImageDataUrl(sessionId, path).getOrThrow()
                        val cachedImageFile = withContext(Dispatchers.IO) {
                            runCatching {
                                if (dataUrl.contains("base64,")) {
                                    val decoded = Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT)
                                    safeFile.writeBytes(decoded)
                                    safeFile
                                } else {
                                    null
                                }
                            }.getOrNull()
                        }
                        _artifactSheetState.value = ArtifactSheetState.ReadyImage(
                            metadata = metadata,
                            path = path,
                            dataUrl = dataUrl,
                            localFile = cachedImageFile
                        )
                    }
                    ArtifactFormat.MARKDOWN, ArtifactFormat.CODE_TEXT, ArtifactFormat.BINARY -> {
                        val textResult = apiClient.fetchFileText(sessionId, path).getOrThrow()
                        withContext(Dispatchers.IO) {
                            safeFile.writeText(textResult.content)
                        }
                        _artifactSheetState.value = ArtifactSheetState.ReadyText(
                            metadata = metadata.copy(sizeBytes = textResult.content.toByteArray().size.toLong()),
                            path = path,
                            content = textResult.content,
                            truncated = textResult.truncated,
                            isMarkdown = format == ArtifactFormat.MARKDOWN,
                            cacheFile = safeFile
                        )
                    }
                }
            } catch (e: Exception) {
                _artifactSheetState.value = ArtifactSheetState.Error(
                    path = path,
                    name = name,
                    message = e.localizedMessage ?: "Unable to open file."
                )
            }
        }
    }

    fun closeArtifactBottomSheet() {
        _artifactSheetState.value = ArtifactSheetState.Hidden
    }

    fun transcribeVoiceBase64(base64Audio: String, format: String, onTextReady: (String) -> Unit) {
        _isTranscribingVoice.value = true
        viewModelScope.launch {
            val result = apiClient.transcribeAudio(base64Audio, format)
            _isTranscribingVoice.value = false
            result.onSuccess { text ->
                if (text.isNotBlank()) {
                    onTextReady(text.trim())
                }
            }.onFailure { err ->
                _streamStatusBanner.value = err.localizedMessage ?: "Voice transcription unavailable."
            }
        }
    }

    private fun rebuildTurnsFromHistory(history: HistoryPage): List<ChatTurn> {
        val turnsByNum = LinkedHashMap<Long, ChatTurn>()
        for (stored in history.records) {
            val turnNum = stored.turn.coerceAtLeast(1L)
            val current = turnsByNum[turnNum] ?: ChatTurn(
                turnNumber = turnNum,
                userPrompt = "",
                isStreaming = false
            )
            val kind = stored.record["kind"]?.jsonPrimitive?.contentOrNull
                ?: stored.record["type"]?.jsonPrimitive?.contentOrNull
            when (kind) {
                "user_input" -> {
                    val promptText = when (val contentEl = stored.record["content"]) {
                        is JsonPrimitive -> contentEl.contentOrNull.orEmpty()
                        is JsonArray -> contentEl.mapNotNull { el ->
                            (el as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: (el as? JsonPrimitive)?.contentOrNull
                        }.joinToString("\n")
                        else -> stored.record["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
                    turnsByNum[turnNum] = current.copy(userPrompt = promptText)
                }
                "step" -> {
                    val idx = stored.record["index"]?.jsonPrimitive?.intOrNull ?: current.steps.size
                    val stepObj = (stored.record["step"] as? JsonObject) ?: stored.record
                    val baseStep = buildStepFromJsonObject(idx, stepObj)
                    val deltasArray = stored.record["deltas"] as? JsonArray
                    val deltaContent = deltasArray?.joinToString("") { extractDeltaText(it) }.orEmpty()
                    val deltaArgs = deltasArray?.mapNotNull { extractDeltaArgs(it) }?.joinToString("").orEmpty()
                    val usageObj = (stored.record["step_usage"] as? JsonObject)
                        ?: (stored.record["usage"] as? JsonObject)
                        ?: (stepObj["usage"] as? JsonObject)
                    val inTok = usageObj?.get("total_input_tokens")?.jsonPrimitive?.longOrNull
                        ?: usageObj?.get("input_tokens")?.jsonPrimitive?.longOrNull ?: 0L
                    val outTok = usageObj?.get("total_output_tokens")?.jsonPrimitive?.longOrNull
                        ?: usageObj?.get("output_tokens")?.jsonPrimitive?.longOrNull ?: 0L
                    val thoughtTok = usageObj?.get("total_thought_tokens")?.jsonPrimitive?.longOrNull
                        ?: usageObj?.get("thought_tokens")?.jsonPrimitive?.longOrNull ?: 0L

                    val mergedContent = if (deltaContent.isNotBlank()) {
                        if (baseStep.content.isNotBlank() && !deltaContent.startsWith(baseStep.content)) {
                            baseStep.content + "\n" + deltaContent
                        } else {
                            deltaContent
                        }
                    } else {
                        baseStep.content
                    }
                    val mergedArgs = when {
                        deltaArgs.isNotBlank() -> deltaArgs
                        else -> baseStep.toolArgsJson
                    }
                    val finalStep = baseStep.copy(
                        content = mergedContent,
                        toolArgsJson = mergedArgs,
                        isFinished = true,
                        inputTokens = inTok,
                        outputTokens = outTok,
                        thoughtTokens = thoughtTok
                    )
                    val existingIdx = current.steps.indexOfFirst { it.index == idx }
                    val updatedSteps = if (existingIdx >= 0) {
                        current.steps.toMutableList().apply { set(existingIdx, finalStep) }
                    } else {
                        current.steps + finalStep
                    }
                    turnsByNum[turnNum] = current.copy(steps = updatedSteps)
                }
            }
        }
        return turnsByNum.values.toList()
    }

    companion object {
        private const val TAG = "OrchVM"

        fun factory(): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val prefs = OrchCloudPreferences(app)
                val apiClient = OrchCloudApiClient(
                    baseUrlProvider = { prefs.getServerUrl() },
                    fcmTokenProvider = { prefs.fcmTokenFlow.firstOrNull() }
                )
                val firestore = FirebaseFirestore.getInstance(
                    app.getString(R.string.firestore_database_id)
                )
                return OrchCloudViewModel(
                    preferences = prefs,
                    apiClient = apiClient,
                    firestore = firestore,
                    cacheDir = app.cacheDir
                ) as T
            }
        }
    }
}
