package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AntigravityStep
import com.example.data.model.ArtifactFormat
import com.example.data.model.BudgetStatus
import com.example.data.model.ChatTurn
import com.example.data.model.FileEntry
import com.example.data.model.ModelInfo
import com.example.data.model.SessionSummary
import com.example.data.model.StepKind
import com.example.data.model.WorkspaceInfo
import com.example.ui.auth.signOutUser
import com.example.ui.components.ArtifactModalBottomSheet
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OrchAccentAmber
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchSurfaceElevated
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary
import com.example.ui.viewmodel.OrchCloudViewModel
import com.example.ui.viewmodel.PendingAttachment
import com.example.voice.VoicePromptRecorder
import compose.icons.SimpleIcons
import compose.icons.TablerIcons
import compose.icons.simpleicons.Git
import compose.icons.simpleicons.Github
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.Compass
import compose.icons.tablericons.FileCode
import compose.icons.tablericons.FileSearch
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Terminal2
import compose.icons.tablericons.Tool
import compose.icons.tablericons.World
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrchCloudAuthenticatedApp(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?,
    deepLinkSessionId: String?,
    onDeepLinkConsumed: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: OrchCloudViewModel = viewModel(
        key = currentUserId,
        factory = OrchCloudViewModel.factory()
    )

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val budgetStatus by viewModel.budgetStatus.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val activeWorkspace by viewModel.activeWorkspace.collectAsStateWithLifecycle()
    val isInitializingWorkspace by viewModel.isInitializingWorkspace.collectAsStateWithLifecycle()
    val turns by viewModel.turns.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val streamStatusBanner by viewModel.streamStatusBanner.collectAsStateWithLifecycle()
    val sandboxFiles by viewModel.sandboxFiles.collectAsStateWithLifecycle()
    val fileSearchQuery by viewModel.fileSearchQuery.collectAsStateWithLifecycle()
    val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val artifactSheetState by viewModel.artifactSheetState.collectAsStateWithLifecycle()
    val isTranscribingVoice by viewModel.isTranscribingVoice.collectAsStateWithLifecycle()

    var showFilesSheet by rememberSaveable { mutableStateOf(false) }
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.syncFcmToken()
    }

    LaunchedEffect(currentUserId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.syncFcmToken()
            }
        } else {
            viewModel.syncFcmToken()
        }
    }

    LaunchedEffect(deepLinkSessionId) {
        if (!deepLinkSessionId.isNullOrBlank()) {
            viewModel.openSession(deepLinkSessionId)
            onDeepLinkConsumed()
        }
    }

    val activeSessionSummary = remember(sessions, activeSessionId) {
        sessions.firstOrNull { it.id == activeSessionId }
    }

    val contextUsageRatio = remember(activeSessionSummary, turns, models, selectedModelId) {
        val model = models.firstOrNull { it.id == selectedModelId } ?: models.firstOrNull()
        val maxWindow = model?.contextWindow ?: 0L
        if (maxWindow <= 0L) {
            0f
        } else {
            val sessionTokens = activeSessionSummary?.contextTokens?.takeIf { it > 0L }
                ?: turns.sumOf { t -> t.steps.sumOf { it.inputTokens + it.outputTokens + it.thoughtTokens } }
            (sessionTokens.toFloat() / maxWindow.toFloat()).coerceIn(0f, 1f)
        }
    }

    ArtifactModalBottomSheet(
        state = artifactSheetState,
        onDismiss = { viewModel.closeArtifactBottomSheet() }
    )

    if (showFilesSheet) {
        SandboxFilesBottomSheet(
            activeWorkspace = activeWorkspace,
            isInitializingWorkspace = isInitializingWorkspace,
            searchQuery = fileSearchQuery,
            sandboxFiles = sandboxFiles,
            onSearchQueryChange = { viewModel.updateFileSearchQuery(it) },
            onInitializeWorkspace = { repo -> viewModel.initializeWorkspaceRepo(repo) },
            onInspectFile = { path, name ->
                showFilesSheet = false
                viewModel.openArtifactInBottomSheet(path, name)
            },
            onDismiss = { showFilesSheet = false }
        )
    }

    if (showAccountSheet) {
        AccountAndUsageBottomSheet(
            userDisplayName = userDisplayName,
            userEmail = userEmail,
            budgetStatus = budgetStatus,
            onDismiss = { showAccountSheet = false }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = OrchBackground,
                drawerContentColor = OrchTextPrimary,
                windowInsets = WindowInsets.systemBars,
                modifier = Modifier.width(300.dp)
            ) {
                SessionsDrawerContent(
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    onNewChat = {
                        viewModel.startNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onSelectSession = { sessionId ->
                        viewModel.openSession(sessionId)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = { sessionId ->
                        viewModel.deleteSession(sessionId)
                    },
                    onOpenAccount = {
                        scope.launch { drawerState.close() }
                        showAccountSheet = true
                    }
                )
            }
        }
    ) {
        OrchConversationScreen(
            activeWorkspace = activeWorkspace,
            isInitializingWorkspace = isInitializingWorkspace,
            models = models,
            selectedModelId = selectedModelId,
            contextUsageRatio = contextUsageRatio,
            turns = turns,
            isStreaming = isStreaming,
            streamStatusBanner = streamStatusBanner,
            pendingAttachments = pendingAttachments,
            isTranscribingVoice = isTranscribingVoice,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onNewSession = { viewModel.startNewSession() },
            onOpenFilesSheet = {
                activeSessionId?.let { viewModel.refreshSandboxFiles(it) }
                showFilesSheet = true
            },
            onConnectRepo = { repoUrl -> viewModel.initializeWorkspaceRepo(repoUrl) },
            onSelectModel = { viewModel.selectModel(it) },
            onSendPrompt = { viewModel.sendPrompt(it) },
            onCancelRun = { viewModel.cancelActiveRun() },
            onAddAttachment = { viewModel.addAttachment(it) },
            onRemoveAttachment = { viewModel.removeAttachment(it) },
            onTranscribeVoice = { base64, fmt, cb ->
                viewModel.transcribeVoiceBase64(base64, fmt, cb)
            },
            onOpenArtifact = { path, name ->
                viewModel.openArtifactInBottomSheet(path, name)
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OrchConversationScreen(
    activeWorkspace: WorkspaceInfo,
    isInitializingWorkspace: Boolean,
    models: List<ModelInfo>,
    selectedModelId: String,
    contextUsageRatio: Float,
    turns: List<ChatTurn>,
    isStreaming: Boolean,
    streamStatusBanner: String?,
    pendingAttachments: List<PendingAttachment>,
    isTranscribingVoice: Boolean,
    onOpenDrawer: () -> Unit,
    onNewSession: () -> Unit,
    onOpenFilesSheet: () -> Unit,
    onConnectRepo: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSendPrompt: (String) -> Unit,
    onCancelRun: () -> Unit,
    onAddAttachment: (PendingAttachment) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onTranscribeVoice: (String, String, (String) -> Unit) -> Unit,
    onOpenArtifact: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showAttachmentSheet by rememberSaveable { mutableStateOf(false) }

    val showScrollToBottom by remember {
        derivedStateOf {
            listState.canScrollForward
        }
    }

    LaunchedEffect(turns.size, turns.lastOrNull()?.steps?.size) {
        if (turns.isNotEmpty()) {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    fun processSelectedUri(uri: Uri?, defaultPrefix: String) {
        if (uri == null) return
        scope.launch {
            val attachment = withContext(Dispatchers.IO) {
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    var displayName = "${defaultPrefix}_${System.currentTimeMillis()}"
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) {
                                val resolved = c.getString(nameIdx)
                                if (!resolved.isNullOrBlank()) displayName = resolved
                            }
                        }
                    }
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        PendingAttachment(
                            name = displayName,
                            sizeBytes = bytes.size.toLong(),
                            dataBase64 = encoded
                        )
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (attachment != null) {
                onAddAttachment(attachment)
            } else {
                Toast.makeText(context, "Unable to attach item", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        processSelectedUri(uri, "image")
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        processSelectedUri(uri, "file")
    }

    if (showAttachmentSheet) {
        AttachmentPickerBottomSheet(
            onPickImages = {
                showAttachmentSheet = false
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickFiles = {
                showAttachmentSheet = false
                documentPickerLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }

    if (showModelSheet) {
        ModelSelectorBottomSheet(
            models = models,
            selectedModelId = selectedModelId,
            onSelectModel = { modelId ->
                showModelSheet = false
                onSelectModel(modelId)
            },
            onDismiss = { showModelSheet = false }
        )
    }

    val repoShortTitle = activeWorkspace.repoUrl
        .trimEnd('/')
        .substringAfterLast('/')
        .removeSuffix(".git")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrchBackground)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.testTag("open_sessions_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Sessions",
                            tint = OrchTextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (repoShortTitle.isNotBlank()) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github_logo),
                                contentDescription = null,
                                tint = OrchTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = repoShortTitle.ifBlank { stringResourceTitle(context) },
                            style = MaterialTheme.typography.titleMedium,
                            color = OrchTextPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenFilesSheet,
                            modifier = Modifier.testTag("open_sandbox_files_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Workspace Files",
                                tint = OrchTextSecondary
                            )
                        }
                        IconButton(
                            onClick = onNewSession,
                            modifier = Modifier.testTag("new_cloud_session_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "New Chat",
                                tint = OrchTextSecondary
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = streamStatusBanner != null) {
                    Text(
                        text = streamStatusBanner.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = OrchAccentAmber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrchBackground)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                OrchComposerBar(
                    activeWorkspace = activeWorkspace,
                    isInitializingWorkspace = isInitializingWorkspace,
                    onConnectRepo = onConnectRepo,
                    models = models,
                    selectedModelId = selectedModelId,
                    contextUsageRatio = contextUsageRatio,
                    onOpenModelSheet = { showModelSheet = true },
                    pendingAttachments = pendingAttachments,
                    onRemoveAttachment = onRemoveAttachment,
                    onOpenAttachmentSheet = { showAttachmentSheet = true },
                    isStreaming = isStreaming,
                    isTranscribingVoice = isTranscribingVoice,
                    onTranscribeVoice = onTranscribeVoice,
                    onSendPrompt = onSendPrompt,
                    onCancelRun = onCancelRun
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imeNestedScroll(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (turns.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 88.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_orch_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "What can I help you build or research?",
                                style = MaterialTheme.typography.headlineMedium,
                                color = OrchTextSecondary,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }

                items(turns, key = { it.turnNumber }) { turn ->
                    OrchTurnView(
                        turn = turn,
                        onOpenArtifact = onOpenArtifact
                    )
                }
            }

            if (showScrollToBottom && turns.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OrchSurface)
                        .border(1.dp, OrchBorder, CircleShape)
                        .clickable {
                            scope.launch {
                                listState.animateScrollToItem(turns.lastIndex)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Scroll to bottom",
                        tint = OrchTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun stringResourceTitle(context: Context): String =
    context.getString(R.string.app_name)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrchTurnView(
    turn: ChatTurn,
    onOpenArtifact: (String, String) -> Unit
) {
    val workSteps = remember(turn.steps) {
        turn.steps.filter { it.kind != StepKind.MODEL_OUTPUT }
    }
    val outputSteps = remember(turn.steps) {
        turn.steps.filter { it.kind == StepKind.MODEL_OUTPUT }
    }
    val isActivelyWorking = turn.isStreaming && outputSteps.isEmpty()

    var isWorkExpanded by rememberSaveable(turn.turnNumber) {
        mutableStateOf(false)
    }

    LaunchedEffect(outputSteps.isNotEmpty(), turn.isStreaming) {
        if (outputSteps.isNotEmpty() || !turn.isStreaming) {
            isWorkExpanded = false
        }
    }

    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (turn.userPrompt.isNotBlank()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    color = OrchSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .border(1.dp, OrchBorder, RoundedCornerShape(18.dp))
                ) {
                    Text(
                        text = turn.userPrompt,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = OrchTextPrimary,
                            lineHeight = 24.sp
                        ),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                    )
                }
            }
        }

        if (workSteps.isNotEmpty() || isActivelyWorking) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isWorkExpanded = !isWorkExpanded }
                        .padding(vertical = 4.dp)
                        .testTag("worked_toggle_${turn.turnNumber}")
                ) {
                    if (isActivelyWorking) {
                        ShimmerWorkingLabel(text = "Working")
                    } else {
                        Text(
                            text = "Worked",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OrchTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isWorkExpanded) {
                            Icons.Default.KeyboardArrowDown
                        } else {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        },
                        contentDescription = if (isWorkExpanded) "Collapse steps" else "Expand steps",
                        tint = OrchTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = isWorkExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        workSteps.forEach { step ->
                            WorkStepDropdownCard(
                                step = step,
                                autoCollapseSignal = outputSteps.isNotEmpty() || !turn.isStreaming
                            )
                        }
                    }
                }
            }
        }

        outputSteps.forEach { step ->
            val detectedPaths = remember(step.content) {
                Regex("(/workspace/[A-Za-z0-9._/\\-]+\\.[A-Za-z0-9]+)")
                    .findAll(step.content)
                    .map { it.value }
                    .distinct()
                    .toList()
            }

            RichMarkdownBody(
                markdown = step.content,
                modifier = Modifier.fillMaxWidth()
            )

            if (detectedPaths.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    detectedPaths.forEach { filePath ->
                        val fileName = filePath.substringAfterLast('/')
                        val ext = fileName.substringAfterLast('.', "")
                        val fmt = ArtifactFormat.resolve(ext, "")
                        AssistChip(
                            onClick = { onOpenArtifact(filePath, fileName) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = OrchSurface,
                                labelColor = OrchTextPrimary
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = OrchBorder
                            ),
                            leadingIcon = {
                                Icon(
                                    imageVector = iconForFormat(fmt),
                                    contentDescription = null,
                                    tint = Color(fmt.badgeColorHex),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(fileName) }
                        )
                    }
                }
            }
        }

        if (turn.statusNote != null) {
            Text(
                text = turn.statusNote,
                style = MaterialTheme.typography.bodySmall,
                color = if (turn.errorCode != null) OrchAccentCoral else OrchTextSecondary
            )
        }
    }
}

@Composable
private fun ShimmerWorkingLabel(text: String) {
    val transition = rememberInfiniteTransition(label = "working_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -160f,
        targetValue = 320f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "working_shimmer_offset"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            OrchTextSecondary.copy(alpha = 0.55f),
            OrchTextPrimary,
            OrchAccentAmber,
            OrchTextSecondary.copy(alpha = 0.55f)
        ),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 140f, 0f)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            brush = brush,
            fontWeight = FontWeight.Medium
        )
    )
}

private val OrchBrainIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TablerBrain",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(
            "M15.5 13a3.5 3.5 0 0 0 -3.5 3.5v1a3.5 3.5 0 0 0 7 0v-1.8" +
                "M8.5 13a3.5 3.5 0 0 1 3.5 3.5v1a3.5 3.5 0 0 1 -7 0v-1.8" +
                "M17.5 16a3.5 3.5 0 0 0 0 -7h-.5" +
                "M19 9.3v-2.8a3.5 3.5 0 0 0 -7 0" +
                "M6.5 16a3.5 3.5 0 0 1 0 -7h.5" +
                "M5 9.3v-2.8a3.5 3.5 0 0 1 7 0v10"
        ).toNodes(),
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    ).build()
}

private fun resolveStepIcon(step: AntigravityStep): ImageVector {
    val toolLower = step.toolName?.lowercase().orEmpty()
    return when (step.kind) {
        StepKind.THOUGHT -> OrchBrainIcon
        StepKind.GOOGLE_SEARCH -> TablerIcons.World
        StepKind.URL_CONTEXT -> TablerIcons.Compass
        StepKind.CODE_EXECUTION -> TablerIcons.Terminal2
        StepKind.FUNCTION_RESULT -> TablerIcons.CircleCheck
        StepKind.FUNCTION_CALL -> when {
            toolLower.contains("github") -> SimpleIcons.Github
            toolLower.contains("git") -> SimpleIcons.Git
            toolLower.contains("bash") || toolLower.contains("shell") || toolLower.contains("exec") || toolLower.contains("command") ->
                TablerIcons.Terminal2
            toolLower.contains("grep") || toolLower.contains("search") || toolLower.contains("find") ->
                TablerIcons.FileSearch
            toolLower.contains("read") || toolLower.contains("view") || toolLower.contains("cat") ->
                TablerIcons.FileText
            toolLower.contains("write") || toolLower.contains("edit") || toolLower.contains("patch") || toolLower.contains("replace") ->
                TablerIcons.FileCode
            toolLower.contains("list") || toolLower.contains("dir") || toolLower.contains("ls") ->
                TablerIcons.Folder
            toolLower.contains("web") || toolLower.contains("http") || toolLower.contains("fetch") ->
                TablerIcons.World
            else -> TablerIcons.Tool
        }
        else -> TablerIcons.Tool
    }
}

@Composable
private fun WorkStepDropdownCard(
    step: AntigravityStep,
    autoCollapseSignal: Boolean
) {
    var expanded by rememberSaveable(step.index) { mutableStateOf(false) }

    LaunchedEffect(autoCollapseSignal) {
        if (autoCollapseSignal) {
            expanded = false
        }
    }

    val label = when (step.kind) {
        StepKind.GOOGLE_SEARCH -> "Searched Web"
        StepKind.URL_CONTEXT -> "Browsed"
        StepKind.CODE_EXECUTION -> "Executed"
        StepKind.FUNCTION_CALL -> step.toolName ?: "Tool Call"
        StepKind.FUNCTION_RESULT -> step.toolName ?: "Result"
        StepKind.THOUGHT -> "Thought"
        else -> step.title
    }

    val summary = remember(step.content, step.toolArgsJson, step.title) {
        step.content
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: step.toolArgsJson
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
            ?: step.title
    }

    val detailText = remember(step.content, step.toolArgsJson) {
        buildString {
            if (!step.toolArgsJson.isNullOrBlank()) {
                append(step.toolArgsJson.trim())
            }
            if (step.content.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(step.content.trim())
            }
        }
    }

    val hasExpandableDetail = detailText.isNotBlank()
    val stepIcon = remember(step.kind, step.toolName) { resolveStepIcon(step) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = hasExpandableDetail) { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = stepIcon,
                contentDescription = null,
                tint = if (step.kind == StepKind.THOUGHT) OrchAccentAmber else OrchTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = OrchTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = OrchTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasExpandableDetail) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (expanded) "Collapse step" else "Expand step",
                    tint = OrchTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(visible = expanded && hasExpandableDetail) {
            val scrollState = rememberScrollState()
            Surface(
                color = OrchSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp)
                    .heightIn(max = 120.dp)
                    .border(1.dp, OrchBorder, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = detailText,
                        style = if (step.kind == StepKind.THOUGHT) {
                            MaterialTheme.typography.bodyMedium.copy(
                                color = OrchTextSecondary,
                                lineHeight = 20.sp
                            )
                        } else {
                            MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = OrchTextPrimary,
                                lineHeight = 17.sp
                            )
                        }
                    )
                }
            }
        }
    }
}

private sealed interface MarkdownSegment {
    data class ProseBlock(val text: String) : MarkdownSegment
    data class FencedCode(val language: String, val code: String) : MarkdownSegment
}

private fun splitMarkdownSegments(raw: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val fenceRegex = Regex("```([a-zA-Z0-9_+\\-]*)\\n([\\s\\S]*?)```")
    var cursor = 0
    for (match in fenceRegex.findAll(raw)) {
        if (match.range.first > cursor) {
            val prose = raw.substring(cursor, match.range.first).trim()
            if (prose.isNotEmpty()) {
                prose.split("\n\n").filter { it.isNotBlank() }.forEach { block ->
                    segments.add(MarkdownSegment.ProseBlock(block.trim()))
                }
            }
        }
        val lang = match.groupValues[1].trim()
        val code = match.groupValues[2].trimEnd()
        segments.add(MarkdownSegment.FencedCode(language = lang, code = code))
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        val tail = raw.substring(cursor).trim()
        if (tail.isNotEmpty()) {
            tail.split("\n\n").filter { it.isNotBlank() }.forEach { block ->
                segments.add(MarkdownSegment.ProseBlock(block.trim()))
            }
        }
    }
    return segments
}

@Composable
private fun RichMarkdownBody(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val segments = remember(markdown) { splitMarkdownSegments(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.FencedCode -> {
                    Surface(
                        color = OrchSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OrchBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(OrchSurfaceElevated)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = segment.language.ifBlank { "code" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OrchTextSecondary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("code", segment.code))
                                            Toast.makeText(context, "Copied code", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = OrchTextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OrchTextSecondary
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = segment.code,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = OrchTextPrimary,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }
                is MarkdownSegment.ProseBlock -> {
                    val trimmed = segment.text
                    val lines = trimmed.lines()
                    if (lines.all {
                            val s = it.trimStart()
                            s.startsWith("- ") || s.startsWith("* ") || s.startsWith("• ")
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            lines.forEach { bulletLine ->
                                val clean = bulletLine.trimStart()
                                    .removePrefix("- ")
                                    .removePrefix("* ")
                                    .removePrefix("• ")
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = OrchTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(end = 12.dp, top = 1.dp)
                                    )
                                    Text(
                                        text = parseInlineMarkdown(clean),
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = OrchTextPrimary,
                                            lineHeight = 25.sp
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else if (trimmed.startsWith("#")) {
                        val headingText = trimmed.trimStart('#', ' ')
                        Text(
                            text = parseInlineMarkdown(headingText),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = OrchTextPrimary,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp
                            )
                        )
                    } else if (trimmed.matches(Regex("^\\d+\\..*")) && !trimmed.contains("\n")) {
                        Text(
                            text = parseInlineMarkdown(trimmed),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = OrchTextPrimary,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 24.sp
                            )
                        )
                    } else {
                        Text(
                            text = parseInlineMarkdown(trimmed),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = OrchTextPrimary,
                                lineHeight = 25.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = OrchAccentCoral,
            textDecoration = TextDecoration.Underline
        )
    )
    return buildAnnotatedString {
        val tokenRegex = Regex("(\\*\\*[^*]+\\*\\*)|(`[^`]+`)|(\\[([^\\]]+)\\]\\(([^)]+)\\))")
        var cursor = 0
        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val full = match.value
            when {
                full.startsWith("**") && full.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = OrchTextPrimary)) {
                        append(full.removeSurrounding("**"))
                    }
                }
                full.startsWith("`") && full.endsWith("`") -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            background = OrchSurface,
                            color = OrchAccentAmber
                        )
                    ) {
                        append(" ${full.removeSurrounding("`")} ")
                    }
                }
                full.startsWith("[") -> {
                    val label = match.groupValues[4]
                    val url = match.groupValues[5]
                    withLink(LinkAnnotation.Url(url = url, styles = linkStyles)) {
                        append(label)
                    }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@Composable
private fun OrchComposerBar(
    activeWorkspace: WorkspaceInfo,
    isInitializingWorkspace: Boolean,
    onConnectRepo: (String) -> Unit,
    models: List<ModelInfo>,
    selectedModelId: String,
    contextUsageRatio: Float,
    onOpenModelSheet: () -> Unit,
    pendingAttachments: List<PendingAttachment>,
    onRemoveAttachment: (String) -> Unit,
    onOpenAttachmentSheet: () -> Unit,
    isStreaming: Boolean,
    isTranscribingVoice: Boolean,
    onTranscribeVoice: (String, String, (String) -> Unit) -> Unit,
    onSendPrompt: (String) -> Unit,
    onCancelRun: () -> Unit
) {
    val context = LocalContext.current
    var promptInput by rememberSaveable { mutableStateOf("") }
    var isRecordingAudio by remember { mutableStateOf(false) }
    val voiceRecorder = remember(context) { VoicePromptRecorder(context) }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.stopRecordingCleanup() }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecording().onSuccess {
                isRecordingAudio = true
            }.onFailure { err ->
                Toast.makeText(context, err.localizedMessage ?: "Microphone unavailable", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val selectedModel = models.firstOrNull { it.id == selectedModelId } ?: models.firstOrNull()
    val modelDisplayLabel = selectedModel?.let { m ->
        if (!m.badge.isNullOrBlank()) "${m.name} · ${m.badge}" else m.name
    }.orEmpty()

    var repoText by rememberSaveable(activeWorkspace.repoUrl) {
        mutableStateOf(activeWorkspace.repoUrl)
    }

    val isRepoConnected = activeWorkspace.workspace.isNotBlank() && repoText.trim() == activeWorkspace.repoUrl.trim()

    val promptTextFieldColors = TextFieldDefaults.colors(
        focusedTextColor = OrchTextPrimary,
        unfocusedTextColor = OrchTextPrimary,
        disabledTextColor = OrchTextSecondary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = OrchAccentCoral,
        focusedPlaceholderColor = OrchTextSecondary,
        unfocusedPlaceholderColor = OrchTextSecondary
    )

    Surface(
        color = OrchSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, OrchBorder, RoundedCornerShape(22.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrchBackground)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_github_logo),
                    contentDescription = "GitHub",
                    tint = if (isRepoConnected) OrchTextPrimary else OrchTextSecondary,
                    modifier = Modifier.size(16.dp)
                )

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (repoText.isEmpty()) {
                        Text(
                            text = "Connect GitHub repository (owner/repo)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OrchTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = repoText,
                        onValueChange = { repoText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = OrchTextPrimary,
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        cursorBrush = SolidColor(OrchAccentCoral),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (repoText.isNotBlank() && !isInitializingWorkspace) {
                                    onConnectRepo(repoText)
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("composer_repo_input")
                    )
                }

                if (isInitializingWorkspace) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = OrchAccentCoral
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRepoConnected) OrchSurface else OrchSurfaceElevated)
                            .border(
                                1.dp,
                                if (isRepoConnected) OrchAccentAmber.copy(alpha = 0.5f) else OrchBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = repoText.isNotBlank()) {
                                onConnectRepo(repoText)
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("composer_repo_connect_button")
                    ) {
                        if (isRepoConnected) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(OrchAccentAmber)
                            )
                        }
                        Text(
                            text = if (isRepoConnected) "Connected" else "Connect",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isRepoConnected) {
                                OrchTextPrimary
                            } else if (repoText.isNotBlank()) {
                                OrchAccentCoral
                            } else {
                                OrchTextSecondary
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = OrchBorder, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)
            ) {
                if (pendingAttachments.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        items(pendingAttachments, key = { it.name }) { att ->
                            Surface(
                                color = OrchBackground,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.border(1.dp, OrchBorder, RoundedCornerShape(50))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = att.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OrchTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove attachment",
                                        tint = OrchTextSecondary,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onRemoveAttachment(att.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                TextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = {
                        Text(
                            text = "Message Orch...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OrchTextSecondary
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = OrchTextPrimary
                    ),
                    colors = promptTextFieldColors,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_prompt_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(OrchBackground)
                                .border(1.dp, OrchBorder, CircleShape)
                                .clickable(onClick = onOpenAttachmentSheet)
                                .testTag("attach_file_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Attach",
                                tint = OrchTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (models.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OrchBackground)
                                    .border(1.dp, OrchBorder, RoundedCornerShape(10.dp))
                                    .clickable(onClick = onOpenModelSheet)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("model_selector_button")
                            ) {
                                Text(
                                    text = modelDisplayLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OrchTextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select model",
                                    tint = OrchTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.size(18.dp),
                                color = OrchBorder,
                                strokeWidth = 2.dp
                            )
                            CircularProgressIndicator(
                                progress = { contextUsageRatio },
                                modifier = Modifier.size(18.dp),
                                color = if (contextUsageRatio > 0.85f) OrchAccentCoral else OrchTextSecondary,
                                strokeWidth = 2.dp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (isRecordingAudio) {
                                        isRecordingAudio = false
                                        voiceRecorder.stopAndEncodeBase64().onSuccess { (b64, fmt) ->
                                            onTranscribeVoice(b64, fmt) { transcribed ->
                                                promptInput = if (promptInput.isBlank()) {
                                                    transcribed
                                                } else {
                                                    "$promptInput $transcribed"
                                                }
                                            }
                                        }.onFailure { err ->
                                            Toast.makeText(context, err.localizedMessage ?: "Audio error", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            voiceRecorder.startRecording().onSuccess {
                                                isRecordingAudio = true
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.localizedMessage ?: "Mic error", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                                .testTag("voice_transcribe_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isTranscribingVoice) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = OrchAccentAmber
                                )
                            } else {
                                Icon(
                                    imageVector = if (isRecordingAudio) Icons.Default.GraphicEq else Icons.Default.MicNone,
                                    contentDescription = "Voice input",
                                    tint = if (isRecordingAudio) OrchAccentCoral else OrchTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        val canSend = isStreaming || promptInput.isNotBlank() || pendingAttachments.isNotEmpty()
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isStreaming -> OrchAccentCoral
                                        canSend -> OrchBorder
                                        else -> OrchBorder.copy(alpha = 0.5f)
                                    }
                                )
                                .clickable(enabled = canSend) {
                                    if (isStreaming) {
                                        onCancelRun()
                                    } else if (promptInput.isNotBlank() || pendingAttachments.isNotEmpty()) {
                                        val toSend = promptInput
                                        promptInput = ""
                                        onSendPrompt(toSend)
                                    }
                                }
                                .testTag("send_prompt_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.ArrowUpward,
                                contentDescription = if (isStreaming) "Stop" else "Send",
                                tint = if (canSend) OrchTextPrimary else OrchTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentPickerBottomSheet(
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Add Attachment",
                style = MaterialTheme.typography.titleLarge,
                color = OrchTextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = OrchSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, OrchBorder, RoundedCornerShape(16.dp))
                        .clickable(onClick = onPickImages)
                        .testTag("attach_images_option")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OrchBackground)
                                .border(1.dp, OrchBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_attach_images),
                                contentDescription = "Images",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Images",
                                style = MaterialTheme.typography.titleMedium,
                                color = OrchTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Photos & gallery",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrchTextSecondary
                            )
                        }
                    }
                }

                Surface(
                    color = OrchSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, OrchBorder, RoundedCornerShape(16.dp))
                        .clickable(onClick = onPickFiles)
                        .testTag("attach_files_option")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(OrchBackground)
                                .border(1.dp, OrchBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_attach_files),
                                contentDescription = "Files",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Files",
                                style = MaterialTheme.typography.titleMedium,
                                color = OrchTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Code, PDF & docs",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrchTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectorBottomSheet(
    models: List<ModelInfo>,
    selectedModelId: String,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select Model",
                style = MaterialTheme.typography.titleLarge,
                color = OrchTextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(models, key = { it.id }) { model ->
                    val isSelected = model.id == selectedModelId
                    Surface(
                        color = if (isSelected) OrchSurfaceElevated else OrchSurface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                if (isSelected) OrchAccentCoral else OrchBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectModel(model.id) }
                            .testTag("model_sheet_item_${model.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = model.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = OrchTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!model.badge.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(OrchBackground)
                                                .border(1.dp, OrchBorder, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = model.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OrchAccentAmber
                                            )
                                        }
                                    }
                                }
                                if (model.provider.isNotBlank()) {
                                    Text(
                                        text = model.provider,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OrchTextSecondary
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = TablerIcons.CircleCheck,
                                    contentDescription = "Selected",
                                    tint = OrchAccentCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SessionsDrawerContent(
    sessions: List<SessionSummary>,
    activeSessionId: String?,
    userDisplayName: String?,
    userEmail: String?,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenAccount: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_orch_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = stringResourceTitle(context),
                    style = MaterialTheme.typography.titleLarge,
                    color = OrchTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Surface(
                color = OrchSurface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .border(1.dp, OrchBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onNewChat)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = OrchTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.labelLarge,
                        color = OrchTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = OrchBorder)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Recent",
            style = MaterialTheme.typography.labelMedium,
            color = OrchTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                val isSelected = session.id == activeSessionId
                Surface(
                    color = if (isSelected) OrchSurface else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSession(session.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (session.running) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OrchAccentAmber)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = session.title?.ifBlank { session.id } ?: session.id,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OrchTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = OrchTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = OrchBorder)
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            color = OrchSurface,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, OrchBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenAccount)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PersonOutline,
                    contentDescription = null,
                    tint = OrchTextPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userDisplayName ?: "Account",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrchTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = OrchTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SandboxFilesBottomSheet(
    activeWorkspace: WorkspaceInfo,
    isInitializingWorkspace: Boolean,
    searchQuery: String,
    sandboxFiles: List<FileEntry>,
    onSearchQueryChange: (String) -> Unit,
    onInitializeWorkspace: (String) -> Unit,
    onInspectFile: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var repoInput by rememberSaveable(activeWorkspace.repoUrl) {
        mutableStateOf(activeWorkspace.repoUrl)
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = OrchTextPrimary,
        unfocusedTextColor = OrchTextPrimary,
        focusedContainerColor = OrchSurface,
        unfocusedContainerColor = OrchSurface,
        focusedBorderColor = OrchAccentCoral,
        unfocusedBorderColor = OrchBorder,
        cursorColor = OrchAccentCoral,
        focusedPlaceholderColor = OrchTextSecondary,
        unfocusedPlaceholderColor = OrchTextSecondary
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 680.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Workspace & Files",
                style = MaterialTheme.typography.titleLarge,
                color = OrchTextPrimary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github_logo),
                            contentDescription = "GitHub",
                            tint = OrchTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    placeholder = { Text("Repository (owner/repo)") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    enabled = repoInput.isNotBlank() && !isInitializingWorkspace,
                    onClick = { onInitializeWorkspace(repoInput) }
                ) {
                    if (isInitializingWorkspace) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = OrchAccentCoral
                        )
                    } else {
                        Text("Connect", color = OrchTextPrimary)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = OrchTextSecondary
                    )
                },
                placeholder = { Text("Filter files...") },
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sandboxFiles, key = { it.path }) { entry ->
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    val fmt = ArtifactFormat.resolve(ext, "")
                    Surface(
                        color = OrchSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OrchBorder, RoundedCornerShape(12.dp))
                            .clickable { onInspectFile(entry.path, entry.name) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = iconForFormat(fmt),
                                contentDescription = fmt.label,
                                tint = Color(fmt.badgeColorHex),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OrchTextPrimary
                                )
                                Text(
                                    text = entry.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OrchTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountAndUsageBottomSheet(
    userDisplayName: String?,
    userEmail: String?,
    budgetStatus: BudgetStatus,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val ratio = if (budgetStatus.limitUsd > 0.0) {
        (budgetStatus.costUsd / budgetStatus.limitUsd).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = userDisplayName ?: "Account",
                style = MaterialTheme.typography.titleLarge,
                color = OrchTextPrimary
            )
            if (!userEmail.isNullOrBlank()) {
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrchTextSecondary
                )
            }

            Surface(
                color = OrchSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OrchBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (budgetStatus.period.isNotBlank()) {
                                "Usage (${budgetStatus.period})"
                            } else {
                                "Monthly Usage"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OrchTextSecondary
                        )
                        Text(
                            text = String.format("$%.2f / $%.2f", budgetStatus.costUsd, budgetStatus.limitUsd),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = OrchTextPrimary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = OrchAccentCoral,
                        trackColor = OrchBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Terms of Service",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrchTextSecondary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri(BuildConfig.ORCH_TERMS_URL)
                    }
                )
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrchTextSecondary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri(BuildConfig.ORCH_PRIVACY_URL)
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    onDismiss()
                    signOutUser(credentialManager = credentialManager, scope = scope)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button")
            ) {
                Text("Sign Out", color = OrchAccentCoral)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun iconForFormat(format: ArtifactFormat): ImageVector = when (format) {
    ArtifactFormat.PDF -> Icons.Default.PictureAsPdf
    ArtifactFormat.DOCX -> Icons.Default.Description
    ArtifactFormat.XLSX -> Icons.Default.TableChart
    ArtifactFormat.PPTX -> Icons.Default.Slideshow
    ArtifactFormat.IMAGE -> Icons.Default.Image
    ArtifactFormat.MARKDOWN -> Icons.Default.Description
    ArtifactFormat.CODE_TEXT, ArtifactFormat.BINARY -> Icons.Default.Code
}
