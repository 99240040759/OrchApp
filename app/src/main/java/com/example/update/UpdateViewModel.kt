package com.example.update

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.runCatchingSuspend
import com.example.data.toApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Installing(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val info: UpdateInfo, val message: String) : UpdateState
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updater = AppUpdater(application)

    private val state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val hidden = MutableStateFlow(false)

    val dialog: StateFlow<UpdateState> = combine(state, hidden) { current, dismissed ->
        if (dismissed) UpdateState.Idle else current
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateState.Idle)

    private var job: Job? = null
    private var lastCheck = 0L

    init {
        viewModelScope.launch { updater.cleanStale() }
        viewModelScope.launch {
            UpdateEvents.failures.collect { message ->
                val current = state.value
                if (current is UpdateState.Installing) {
                    state.value = UpdateState.Failed(current.info, message)
                    hidden.value = false
                }
            }
        }
    }

    fun check() {
        if (state.value != UpdateState.Idle || job?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (lastCheck != 0L && now - lastCheck < MIN_CHECK_INTERVAL_MS) return
        lastCheck = now
        job = viewModelScope.launch {
            val latest = runCatchingSuspend { updater.latest() }.getOrNull() ?: return@launch
            if (latest.versionCode > updater.installedVersion) download(latest)
        }
    }

    fun retry() {
        val info = when (val current = state.value) {
            is UpdateState.Failed -> current.info
            else -> return
        }
        job?.cancel()
        job = viewModelScope.launch { download(info) }
    }

    fun install() {
        val ready = state.value as? UpdateState.Ready ?: return
        if (!updater.canInstall()) {
            updater.openInstallSettings()
            return
        }
        state.value = UpdateState.Installing(ready.info, ready.file)
        viewModelScope.launch {
            runCatchingSuspend { withContext(Dispatchers.IO) { updater.install(ready.file) } }
                .onFailure { state.value = UpdateState.Failed(ready.info, it.message ?: "The update could not be started.") }
        }
    }

    fun later() {
        val current = state.value
        if (current is UpdateState.Installing) state.value = UpdateState.Ready(current.info, current.file)
        hidden.value = true
    }

    private suspend fun download(info: UpdateInfo) {
        hidden.value = false
        state.value = UpdateState.Downloading(info, null)
        try {
            val file = updater.download(info) { progress -> state.value = UpdateState.Downloading(info, progress) }
            state.value = UpdateState.Ready(info, file)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            state.value = UpdateState.Failed(info, failure.toApiError().message)
        }
    }

    private companion object {
        const val MIN_CHECK_INTERVAL_MS = 30 * 60 * 1000L
    }
}
