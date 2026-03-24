package com.orch.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.orch.app.ui.ChatScreen
import com.orch.app.ui.LoadingScreen
import com.orch.app.ui.theme.OrchAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result - we proceed regardless since notifications are optional
        // but improve the experience
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+ (required for foreground service notifications)
        requestNotificationPermissionIfNeeded()

        setContent {
            OrchAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unload the model and free native memory when the app fully exits
        if (isFinishing) {
            viewModel.cancelGeneration()
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val appState           by viewModel.appState.collectAsState()
    val messages           by viewModel.messages.collectAsState()
    val isGenerating       by viewModel.isGenerating.collectAsState()
    val conversations      by viewModel.conversations.collectAsState()
    val currentConvId      by viewModel.currentConversationId.collectAsState()
    val tokensPerSec       by viewModel.tokensPerSec.collectAsState()
    val updateState        by viewModel.updateState.collectAsState()

    when (val state = appState) {
        is AppState.ReadyToChat -> {
            ChatScreen(
                messages           = messages,
                onSendMessage      = { viewModel.sendMessage(it) },
                isGenerating       = isGenerating,
                tokensPerSec       = tokensPerSec,
                conversations      = conversations,
                currentConversationId = currentConvId,
                onNewChat          = { viewModel.newChat() },
                onLoadChat         = { viewModel.loadChat(it) },
                onDeleteChat       = { viewModel.deleteChat(it) },
                onDeleteAllHistory = { viewModel.deleteAllHistory() },
                updateState        = updateState,
                onStartUpdate      = { viewModel.startUpdateDownload() },
                onInstallUpdate    = { viewModel.installUpdate() },
                onStopGeneration   = { viewModel.cancelGeneration() },
                onRegenerateResponse = { viewModel.regenerateResponse() }
            )
        }
        else -> {
            LoadingScreen(
                state   = state,
                onRetry = { viewModel.retryLoading() },
                onCancelDownload = { viewModel.cancelDownload() }
            )
        }
    }
}
