package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
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
import com.example.ui.sheets.AccountSheet
import com.example.ui.sheets.AttachmentSheet
import com.example.ui.sheets.FilesSheet
import com.example.ui.sheets.ModelSheet
import com.example.ui.theme.OrchBackground
import kotlinx.coroutines.launch

private enum class Sheet { None, Attach, Files, Models, Account }

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

    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val selectedModelId by vm.selectedModelId.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val artifact by vm.artifact.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }
    LaunchedEffect(vm) { vm.notices.collect { snackbar.showSnackbar(it) } }
    // Toasts are drawn above bottom sheets, unlike the snackbar.
    LaunchedEffect(vm) {
        vm.saveResults.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        vm.addAttachments(it)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.addAttachments(it)
    }

    val title = sessions.firstOrNull { it.id == chat.sessionId }?.title ?: "New chat"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = OrchBackground, modifier = Modifier.width(320.dp)) {
                SessionsDrawer(
                    sessions = sessions,
                    activeId = chat.sessionId,
                    userName = userName,
                    onNewChat = {
                        vm.newChat()
                        scope.launch { drawerState.close() }
                    },
                    onOpen = {
                        vm.openSession(it)
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
        ChatScreen(
            title = title,
            chat = chat,
            attachments = attachments,
            models = models,
            selectedModelId = selectedModelId,
            snackbar = snackbar,
            onMenu = { scope.launch { drawerState.open() } },
            onNewChat = vm::newChat,
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
    }

    when (sheet) {
        Sheet.None -> Unit
        Sheet.Attach -> AttachmentSheet(
            chat = chat,
            onPickImages = { imagePicker.launch("image/*") },
            onPickFiles = { filePicker.launch(arrayOf("*/*")) },
            onOpenExplorer = { sheet = Sheet.Files },
            onConnectRepo = vm::connectRepo,
            onDisconnectRepo = vm::disconnectRepo,
            onDismiss = { if (sheet == Sheet.Attach) sheet = Sheet.None }
        )
        Sheet.Files -> FilesSheet(
            files = files,
            onQuery = vm::loadFiles,
            onOpen = vm::openFile,
            onDismiss = { sheet = Sheet.None }
        )
        Sheet.Models -> ModelSheet(
            models = models,
            selectedId = selectedModelId,
            onSelect = vm::selectModel,
            onDismiss = { sheet = Sheet.None }
        )
        Sheet.Account -> AccountSheet(
            name = userName,
            email = userEmail,
            budget = budget,
            onSignOut = {
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
