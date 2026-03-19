package com.orch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.orch.app.ui.ChatScreen
import com.orch.app.ui.LoadingScreen
import com.orch.app.ui.theme.OrchAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
                onInstallUpdate    = { viewModel.installUpdate() }
            )
        }
        else -> {
            LoadingScreen(
                state   = state,
                onRetry = { viewModel.retryLoading() }
            )
        }
    }
}
