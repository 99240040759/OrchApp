package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.auth.signOutUser
import com.example.ui.chat.ChatScreen
import com.example.ui.components.ArtifactModalBottomSheet
import com.example.ui.connectors.ConnectorsScreen
import com.example.ui.sheets.AccountSheet
import com.example.ui.sheets.AttachmentSheet
import com.example.ui.sheets.FilesSheet
import com.example.ui.sheets.ModelSheet
import com.example.ui.sheets.TerminalSheet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class Sheet { None, Attach, Files, Terminal, Models, Account }
private enum class AppScreen { Chat, Connectors }

@Composable
fun OrchApp(userScope: UserScope, userId: String, userName: String?, userEmail: String?) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val vm: OrchViewModel = viewModel(
        viewModelStoreOwner = userScope,
        key = userId,
        factory = remember(application) { OrchViewModel.factory(application) }
    )
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    var sheet by rememberSaveable { mutableStateOf(Sheet.None) }
    var screen by rememberSaveable { mutableStateOf(AppScreen.Chat) }

    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val selectedModelId by vm.selectedModelId.collectAsStateWithLifecycle()
    val thinkingEffort by vm.thinkingEffort.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val connectors by vm.connectors.collectAsStateWithLifecycle()
    val terminal by vm.terminalState.collectAsStateWithLifecycle()
    val artifact by vm.artifact.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onForeground() }
    LaunchedEffect(vm) { vm.notices.collectLatest { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) {
        vm.saveResults.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        vm.addAttachments(it)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.addAttachments(it)
    }

    val title = sessions.firstOrNull { it.id == chat.sessionId }?.title ?: "New chat"

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = screen == AppScreen.Chat,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(320.dp)
            ) {
                SessionsDrawer(
                    sessions = sessions,
                    activeId = chat.sessionId,
                    userName = userName,
                    onConnectorsScreen = screen == AppScreen.Connectors,
                    activeConnectorCount = connectors.activeIds.size,
                    onNewChat = {
                        vm.newChat()
                        screen = AppScreen.Chat
                        scope.launch { drawerState.close() }
                    },
                    onConnectors = {
                        screen = AppScreen.Connectors
                        scope.launch { drawerState.close() }
                    },
                    onOpen = {
                        vm.openSession(it)
                        screen = AppScreen.Chat
                        scope.launch { drawerState.close() }
                    },
                    onDelete = vm::deleteSession,
                    onAccount = {
                        scope.launch { drawerState.close() }
                        sheet = Sheet.Account
                    }
                )
            }
        }
    ) {
        when (screen) {
            AppScreen.Chat -> ChatScreen(
                title = title,
                chat = chat,
                attachments = attachments,
                models = models,
                selectedModelId = selectedModelId,
                thinkingEffort = thinkingEffort,
                snackbar = snackbar,
                onMenu = { scope.launch { drawerState.open() } },
                onOpenExplorer = { sheet = Sheet.Files },
                onOpenTerminal = { sheet = Sheet.Terminal },
                onSend = vm::send,
                onStop = vm::stop,
                onAttach = { sheet = Sheet.Attach },
                onPickModel = { sheet = Sheet.Models },
                onRemoveAttachment = vm::removeAttachment,
                onOpenFile = vm::openFile,
                onLoadEarlier = vm::loadEarlier,
                onRetryLoad = { vm.openSession(chat.sessionId, force = true) },
                transcribe = vm::transcribe
            )
            AppScreen.Connectors -> ConnectorsScreen(
                connectors = connectors,
                onBack = { screen = AppScreen.Chat },
                onRefresh = vm::loadConnectors,
                onToggle = vm::toggleConnector,
                onConnect = { id ->
                    vm.connectConnector(id) { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure {
                            Toast.makeText(context, "Unable to open browser.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDisconnect = vm::disconnectConnector
            )
        }
    }

    when (sheet) {
        Sheet.None -> Unit
        Sheet.Attach -> AttachmentSheet(
            chat = chat,
            onPickImages = {
                sheet = Sheet.None
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickFiles = {
                sheet = Sheet.None
                filePicker.launch(arrayOf("*/*"))
            },
            onConnectRepo = vm::connectRepo,
            onEditRepo = vm::clearRepoError,
            onDisconnectRepo = vm::disconnectRepo,
            onDismiss = { sheet = Sheet.None }
        )
        Sheet.Files -> FilesSheet(
            repoName = chat.repo?.name ?: "Workspace",
            files = files,
            onBrowse = vm::browseFiles,
            onNavigateUp = vm::browseUp,
            onOpen = vm::openFile,
            onDismiss = { sheet = Sheet.None }
        )
        Sheet.Terminal -> {
            val repo = chat.repo
            if (repo != null) {
                TerminalSheet(
                    repoName = repo.name,
                    workspace = repo.workspace,
                    terminal = terminal,
                    onConnect = vm::connectTerminal,
                    onInput = vm::sendTerminalInput,
                    onResize = vm::resizeTerminal,
                    onClear = vm::clearTerminal,
                    onDisconnect = vm::disconnectTerminal,
                    onDismiss = { sheet = Sheet.None }
                )
            } else {
                sheet = Sheet.None
            }
        }
        Sheet.Models -> ModelSheet(
            models = models,
            selectedId = selectedModelId,
            thinkingEffort = thinkingEffort,
            onSelectModel = vm::selectModel,
            onSelectThinkingEffort = vm::selectThinkingEffort,
            onDismiss = { sheet = Sheet.None }
        )
        Sheet.Account -> AccountSheet(
            name = userName,
            email = userEmail,
            budget = budget,
            onSignOut = {
                sheet = Sheet.None
                userScope.autoSignIn = false
                signOutUser(CredentialManager.create(context), scope)
            },
            onDismiss = { sheet = Sheet.None }
        )
    }

    ArtifactModalBottomSheet(
        state = artifact,
        saving = saving,
        onSave = vm::saveArtifact,
        onDismiss = vm::closeArtifact
    )
}

