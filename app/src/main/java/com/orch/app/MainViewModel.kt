package com.orch.app

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.AiChat
import com.arm.aichat.ReasoningToken
import com.orch.app.data.ChatConversation
import com.orch.app.data.ChatHistoryRepository
import com.orch.app.data.ChatMessage
import com.orch.app.update.UpdateChecker
import com.orch.app.update.UpdateUiState
import com.orch.app.util.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

private const val TAG = "MainViewModel"

// ── Model ──────────────────────────────────────────────────────────────────
// Orch reasoning model (~1.28 GB) — optimized for mobile (4GB+ RAM phones)
// Thinking mode enabled: the model uses <think>...</think> chains before answering.
private const val MODEL_URL =
    "https://huggingface.co/lm-kit/qwen-3-1.7b-instruct-gguf/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
private const val MODEL_FILENAME = "orch_model_qwen3_1.7b_q4km.gguf"
private const val MODEL_EXPECTED_SIZE = 1282439360L   // ~1.28 GB Q4_K_M

// ── GitHub repo for OTA updates ────────────────────────────────────────────
// Set these to your own GitHub username / repo name.
// The app looks at: https://api.github.com/repos/OWNER/REPO/releases/latest
private const val GITHUB_OWNER = "sameer786ss"  // GitHub username
private const val GITHUB_REPO  = "OrchApp"      // GitHub repo name

// ── System prompt — reasoning model thinking-mode ─────────────────────────
// The model natively supports <think>…</think> chains for reasoning.
// The engine parses these as ReasoningToken.Thinking vs .Content automatically.
private const val SYSTEM_PROMPT = """You are Orch AI, a powerful private reasoning assistant running fully offline on this device. You think carefully and thoroughly before giving your final answer. Format code with proper markdown fenced code blocks (``` followed by the language name). Be precise, helpful, and concise in your final answers."""

// ── App state ──────────────────────────────────────────────────────────────
sealed class AppState {
    object Initializing : AppState()
    data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : AppState()
    object LoadingModel : AppState()
    data class Error(val message: String) : AppState()
    object ReadyToChat : AppState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // ── Inference engine (ARM AI SDK) ──────────────────────────────────────
    private val engine = AiChat.getInferenceEngine(application)

    // ── Repositories ──────────────────────────────────────────────────────
    private val historyRepo = ChatHistoryRepository(application)

    // ── State flows ───────────────────────────────────────────────────────
    private val _appState = MutableStateFlow<AppState>(AppState.Initializing)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _tokensPerSec = MutableStateFlow(0f)
    val tokensPerSec: StateFlow<Float> = _tokensPerSec.asStateFlow()

    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    val conversations: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    // ── Update state ───────────────────────────────────────────────────────
    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    // ── Internals ──────────────────────────────────────────────────────────
    private var generationJob: Job? = null

    private val updateChecker by lazy {
        val versionCode = try {
            getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
                .let { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                    it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode }
        } catch (_: Exception) { 1 }
        UpdateChecker(getApplication(), GITHUB_OWNER, GITHUB_REPO, versionCode)
    }

    init {
        loadConversationList()
        checkAndInitEngine()
    }

    // ── Engine initialisation ──────────────────────────────────────────────

    private fun checkAndInitEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val modelFile = File(context.getExternalFilesDir(null), MODEL_FILENAME)
            if (modelFile.exists() && modelFile.length() >= MODEL_EXPECTED_SIZE - 5_000_000L) {
                // File size check with 5 MB tolerance for minor GGUF metadata differences
                initEngine(modelFile.absolutePath)
            } else {
                if (modelFile.exists()) {
                    Log.i(TAG, "Incomplete model (${modelFile.length()} B) — re-downloading")
                    modelFile.delete()
                }
                downloadModel(modelFile)
            }
        }
    }

    private fun downloadModel(targetFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                // Check network connectivity
                if (!DeviceUtils.isNetworkAvailable(context)) {
                    _appState.value = AppState.Error(
                        "No internet connection.\n\n" +
                        "Please connect to WiFi or mobile data and try again."
                    )
                    return@launch
                }

                // Check storage space
                val storageDir = context.getExternalFilesDir(null)
                if (!DeviceUtils.hasEnoughStorage(storageDir, MODEL_EXPECTED_SIZE)) {
                    val available = DeviceUtils.formatBytes(DeviceUtils.getAvailableStorageBytes(storageDir))
                    val required = DeviceUtils.formatBytes(MODEL_EXPECTED_SIZE)
                    _appState.value = AppState.Error(
                        "Not enough storage space.\n\n" +
                        "Required: $required\n" +
                        "Available: $available\n\n" +
                        "Free up some space and try again."
                    )
                    return@launch
                }

                _appState.value = AppState.Downloading(0, 0L, MODEL_EXPECTED_SIZE)

                val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                
                // If it's already downloading, attach to it instead of restarting.
                var activeDownloadId = -1L
                val query = android.app.DownloadManager.Query()
                val cursor = downloadManager.query(query)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TITLE))
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                        if (title == "Orch AI Model" && status != android.app.DownloadManager.STATUS_SUCCESSFUL && status != android.app.DownloadManager.STATUS_FAILED) {
                            activeDownloadId = cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_ID))
                            break
                        }
                    }
                    cursor.close()
                }

                val downloadId = if (activeDownloadId != -1L) {
                    activeDownloadId
                } else {
                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(MODEL_URL))
                        .setTitle("Orch AI Model")
                        .setDescription("Downloading reasoning model for offline AI")
                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setDestinationInExternalFilesDir(context, null, MODEL_FILENAME)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(true)
                    downloadManager.enqueue(request)
                }

                // Poll progress
                var isDownloading = true
                while (isDownloading) {
                    kotlinx.coroutines.delay(1000)
                    val progressCursor = downloadManager.query(android.app.DownloadManager.Query().setFilterById(downloadId))
                    if (progressCursor != null && progressCursor.moveToFirst()) {
                        val status = progressCursor.getInt(progressCursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                        val bytesDownloaded = progressCursor.getLong(progressCursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val totalBytes = progressCursor.getLong(progressCursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        
                        if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                            isDownloading = false
                            _appState.value = AppState.Downloading(100, totalBytes, totalBytes)
                            initEngine(targetFile.absolutePath)
                        } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                            isDownloading = false
                            val reason = progressCursor.getInt(progressCursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_REASON))
                            _appState.value = AppState.Error("Download failed with reason code: $reason")
                            if (targetFile.exists()) targetFile.delete()
                        } else {
                            val activeTotal = if (totalBytes > 0) totalBytes else MODEL_EXPECTED_SIZE
                            val progress = if (activeTotal > 0) (bytesDownloaded * 100L / activeTotal).toInt() else 0
                            _appState.value = AppState.Downloading(progress, bytesDownloaded, activeTotal)
                        }
                    } else {
                        // User manually deleted or cancelled it
                        isDownloading = false
                        _appState.value = AppState.Error("Download cancelled or interrupted")
                        if (targetFile.exists()) targetFile.delete()
                    }
                    progressCursor?.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                if (targetFile.exists()) targetFile.delete()
                _appState.value = AppState.Error("Download failed: ${e.message}\n\nCheck your internet connection and try again.")
            }
        }
    }

    private fun initEngine(modelPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _appState.value = AppState.LoadingModel

                // Wait for native library initialisation
                var waited = 0
                while (
                    engine.state.value is com.arm.aichat.InferenceEngine.State.Uninitialized ||
                    engine.state.value is com.arm.aichat.InferenceEngine.State.Initializing
                ) {
                    kotlinx.coroutines.delay(100)
                    waited += 100
                    if (waited > 30_000) throw IOException("Engine init timed out")
                }

                // Clean up if stuck in error/old model
                if (engine.state.value is com.arm.aichat.InferenceEngine.State.Error ||
                    engine.state.value is com.arm.aichat.InferenceEngine.State.ModelReady) {
                    try { engine.cleanUp() } catch (_: Exception) {}
                }

                engine.loadModel(modelPath)
                engine.setSystemPrompt(SYSTEM_PROMPT)

                Log.i(TAG, "Orch engine ready | model=$modelPath | state=${engine.state.value}")
                _appState.value = AppState.ReadyToChat

                // Check for updates quietly after model is ready
                checkForUpdate()

            } catch (e: Exception) {
                Log.e(TAG, "Engine init failed", e)
                _appState.value = AppState.Error("Failed to load model: ${e.message}")
            }
        }
    }

    fun retryLoading() { checkAndInitEngine() }

    // ── Update system ──────────────────────────────────────────────────────

    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateUiState.Checking
            _updateState.value = updateChecker.checkForUpdate()
        }
    }

    fun startUpdateDownload() {
        val available = _updateState.value as? UpdateUiState.Available ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _updateState.value = UpdateUiState.Downloading(0)
                val apkFile = updateChecker.downloadApk(available.apkUrl) { progress ->
                    _updateState.value = UpdateUiState.Downloading(progress)
                }
                _updateState.value = UpdateUiState.ReadyToInstall(apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Update download failed", e)
                _updateState.value = UpdateUiState.Available(
                    available.versionName, available.apkUrl, available.releaseNotes
                )
            }
        }
    }

    fun installUpdate() {
        val ready = _updateState.value as? UpdateUiState.ReadyToInstall ?: return
        updateChecker.installApk(ready.apkFile)
    }

    // ── Message sending ────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(text = text.trim(), isUser = true)
        _messages.value = _messages.value + userMessage

        // Create conversation record on first message
        if (_currentConversationId.value == null) {
            val title = text.trim().take(45).let { if (text.length > 45) "$it…" else it }
            val conv = ChatConversation(title = title, messages = _messages.value)
            _currentConversationId.value = conv.id
            viewModelScope.launch(Dispatchers.IO) {
                historyRepo.saveConversation(conv)
                _conversations.value = historyRepo.getAllConversations()
            }
        }

        sendMessageInternal(text.trim())
    }

    /** Internal message sending (used by sendMessage and regenerateResponse) */
    private fun sendMessageInternal(text: String) {
        // Guard: ensure engine is ready
        if (engine.state.value !is com.arm.aichat.InferenceEngine.State.ModelReady) {
            _messages.value = _messages.value + ChatMessage(
                text = "Orch is not ready yet — the model is still loading.",
                isUser = false
            )
            return
        }

        _isGenerating.value = true
        startForegroundService()

        val placeholderId = java.util.UUID.randomUUID().toString()
        _messages.value = _messages.value + ChatMessage(id = placeholderId, text = "", isUser = false)

        var thinkingBuffer = StringBuilder()
        var contentBuffer  = StringBuilder()
        var tokenCount     = 0
        val startTime      = System.currentTimeMillis()
        var thinkingStartTime = System.currentTimeMillis()
        var thinkingDuration = 0L
        var isThinkingState  = true

        generationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                engine.sendUserPromptWithReasoning(text).collect { token ->
                    when (token) {
                        is ReasoningToken.Thinking -> {
                            thinkingBuffer.append(token.text)
                            _messages.value = _messages.value.map {
                                if (it.id == placeholderId) it.copy(
                                    thinkingText = thinkingBuffer.toString(),
                                    isThinking = true
                                ) else it
                            }
                        }
                        is ReasoningToken.Content -> {
                            if (isThinkingState) {
                                thinkingDuration = System.currentTimeMillis() - thinkingStartTime
                                isThinkingState = false
                            }
                            contentBuffer.append(token.text)
                            tokenCount++
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                            _tokensPerSec.value = if (elapsed > 0f) tokenCount / elapsed else 0f

                            _messages.value = _messages.value.map {
                                if (it.id == placeholderId) it.copy(
                                    text = contentBuffer.toString(),
                                    thinkingText = thinkingBuffer.toString(),
                                    isThinking = false,
                                    thinkingDurationMs = thinkingDuration
                                ) else it
                            }
                        }
                        is ReasoningToken.Done -> {
                            _messages.value = _messages.value.map {
                                if (it.id == placeholderId) it.copy(
                                    text = contentBuffer.toString().trim(),
                                    thinkingText = thinkingBuffer.toString().trim(),
                                    isThinking = false,
                                    thinkingDurationMs = thinkingDuration
                                ) else it
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // User cancelled - mark message as incomplete but keep what we have
                val currentText = contentBuffer.toString().trim()
                if (currentText.isNotEmpty()) {
                    _messages.value = _messages.value.map {
                        if (it.id == placeholderId) it.copy(
                            text = currentText + "\n\n[Generation stopped]",
                            isThinking = false
                        ) else it
                    }
                } else {
                    // Remove empty placeholder
                    _messages.value = _messages.value.filter { it.id != placeholderId }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation error", e)
                _messages.value = _messages.value.map {
                    if (it.id == placeholderId) it.copy(
                        text = "Error: ${e.message ?: "Unknown error occurred"}",
                        isThinking = false
                    ) else it
                }
            } finally {
                _isGenerating.value = false
                _tokensPerSec.value = 0f
                stopForegroundService()
                saveCurrentConversation()
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        _isGenerating.value = false
        _tokensPerSec.value = 0f
        stopForegroundService()

        // Remove incomplete AI message if generation was cancelled
        val msgs = _messages.value
        if (msgs.isNotEmpty() && !msgs.last().isUser && msgs.last().text.isEmpty()) {
            _messages.value = msgs.dropLast(1)
        }
        saveCurrentConversation()
    }

    /** Regenerate the last AI response */
    fun regenerateResponse() {
        if (_isGenerating.value) return
        val msgs = _messages.value
        if (msgs.isEmpty()) return

        // Find the last user message and remove any AI responses after it
        val lastUserIndex = msgs.indexOfLast { it.isUser }
        if (lastUserIndex == -1) return

        val userMessage = msgs[lastUserIndex].text
        _messages.value = msgs.subList(0, lastUserIndex + 1)

        // Re-send the user's message
        sendMessageInternal(userMessage)
    }

    /** Cancel model download */
    fun cancelDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager

                val query = android.app.DownloadManager.Query()
                val cursor = downloadManager.query(query)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TITLE))
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                        if (title == "Orch AI Model" && status == android.app.DownloadManager.STATUS_RUNNING) {
                            val downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_ID))
                            downloadManager.remove(downloadId)
                            break
                        }
                    }
                    cursor.close()
                }

                // Clean up partial file
                val modelFile = File(context.getExternalFilesDir(null), MODEL_FILENAME)
                if (modelFile.exists()) modelFile.delete()

                _appState.value = AppState.Error("Download cancelled. Tap 'Try Again' to restart.")
            } catch (e: Exception) {
                Log.e(TAG, "Cancel download failed", e)
            }
        }
    }

    // ── Foreground service ─────────────────────────────────────────────────

    private fun startForegroundService() {
        try {
            val ctx = getApplication<Application>()
            ctx.startForegroundService(Intent(ctx, OrchestratorService::class.java).apply {
                action = OrchestratorService.ACTION_START
            })
        } catch (e: Exception) {
            Log.w(TAG, "startForegroundService: ${e.message}")
        }
    }

    private fun stopForegroundService() {
        try {
            val ctx = getApplication<Application>()
            ctx.startService(Intent(ctx, OrchestratorService::class.java).apply {
                action = OrchestratorService.ACTION_STOP
            })
        } catch (e: Exception) {
            Log.w(TAG, "stopForegroundService: ${e.message}")
        }
    }

    // ── History ────────────────────────────────────────────────────────────

    private fun loadConversationList() {
        viewModelScope.launch(Dispatchers.IO) {
            _conversations.value = historyRepo.getAllConversations()
        }
    }

    private fun saveCurrentConversation() {
        val convId = _currentConversationId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = historyRepo.getConversation(convId)
            val conv = (existing ?: ChatConversation(id = convId)).copy(
                messages = _messages.value,
                updatedAt = System.currentTimeMillis()
            )
            historyRepo.saveConversation(conv)
            _conversations.value = historyRepo.getAllConversations()
        }
    }

    fun newChat() {
        cancelGeneration()
        _currentConversationId.value = null
        _messages.value = emptyList()
    }

    fun loadChat(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.getConversation(conversationId)?.let { conv ->
                _currentConversationId.value = conv.id
                _messages.value = conv.messages
            }
        }
    }

    fun deleteChat(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.deleteConversation(conversationId)
            _conversations.value = historyRepo.getAllConversations()
            if (_currentConversationId.value == conversationId) {
                _currentConversationId.value = null
                _messages.value = emptyList()
            }
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyRepo.deleteAllConversations()
            _conversations.value = emptyList()
            _currentConversationId.value = null
            _messages.value = emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        try { engine.destroy() } catch (_: Exception) {}
    }
}
