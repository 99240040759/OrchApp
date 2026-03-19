package com.orch.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.orch.app.R
import com.orch.app.data.ChatConversation
import com.orch.app.data.ChatMessage
import com.orch.app.ui.theme.*
import com.orch.app.update.UpdateUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    isGenerating: Boolean,
    tokensPerSec: Float,
    conversations: List<ChatConversation>,
    currentConversationId: String?,
    onNewChat: () -> Unit,
    onLoadChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onDeleteAllHistory: () -> Unit,
    updateState: UpdateUiState = UpdateUiState.Idle,
    onStartUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {}
) {
    var textState by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom on new content if already at bottom or if it's a user message
    val isAtBottom = remember { derivedStateOf { 
        val layoutInfo = listState.layoutInfo
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        if (layoutInfo.totalItemsCount == 0) true
        else {
            val lastVisibleItem = visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisibleItem.index >= layoutInfo.totalItemsCount - 2 // Allow a small buffer
        }
    } }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, messages.lastOrNull()?.thinkingText?.length) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages.last()
            if (isAtBottom.value || lastMessage.isUser || isGenerating) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistorySidebar(
                conversations = conversations,
                currentConversationId = currentConversationId,
                onNewChat = {
                    onNewChat()
                    scope.launch { drawerState.close() }
                },
                onLoadChat = { id ->
                    onLoadChat(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteChat = onDeleteChat,
                onDeleteAllHistory = onDeleteAllHistory
            )
        },
        gesturesEnabled = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ── Top bar ────────────────────────────────────────────────────
            Surface(
                color = DarkSurface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = WarmOrange)
                    }

                    Text(
                        text = "Orch AI",
                        color = WarmOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Tokens/sec badge during generation
                    if (isGenerating && tokensPerSec > 0f) {
                        Surface(
                            color = WarmOrange.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "%.1f t/s".format(tokensPerSec),
                                color = WarmOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Update badge
                    UpdateHeaderChip(
                        updateState = updateState,
                        onStartUpdate = onStartUpdate,
                        onInstallUpdate = onInstallUpdate
                    )
                }
            }

            // ── Message list ───────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageItem(message)
                        }
                    }
                }
            }

            // ── Input bar ──────────────────────────────────────────────────
            GlassInputBar(
                text = textState,
                onTextChange = { textState = it },
                onSend = {
                    if (textState.isNotBlank() && !isGenerating) {
                        onSendMessage(textState.trim())
                        textState = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                },
                isGenerating = isGenerating
            )
        }
    }
}

// ── Update Header Chip ─────────────────────────────────────────────────────
@Composable
fun UpdateHeaderChip(
    updateState: UpdateUiState,
    onStartUpdate: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    AnimatedVisibility(
        visible = updateState !is UpdateUiState.Idle && updateState !is UpdateUiState.UpToDate,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally()
    ) {
        when (updateState) {
            is UpdateUiState.Checking -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = TextSecondary,
                        strokeWidth = 1.5.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Checking…", color = TextSecondary, fontSize = 12.sp)
                }
            }
            is UpdateUiState.Available -> {
                Surface(
                    onClick = onStartUpdate,
                    color = WarmOrange.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = "Update available",
                            tint = WarmOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "v${updateState.versionName}",
                            color = WarmOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            is UpdateUiState.Downloading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { updateState.progress / 100f },
                        modifier = Modifier.size(18.dp),
                        color = WarmOrange,
                        trackColor = WarmOrange.copy(alpha = 0.2f),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${updateState.progress}%",
                        color = WarmOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            is UpdateUiState.ReadyToInstall -> {
                Button(
                    onClick = onInstallUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .height(32.dp)
                ) {
                    Icon(
                        Icons.Default.GetApp,
                        contentDescription = "Install",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Install",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            else -> {}
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────
@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "Orch Logo",
                modifier = Modifier.size(100.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Orch AI",
                color = WarmOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Private • Offline • Reasoning",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            // Suggestion chips
            val suggestions = listOf(
                "Write a Python script",
                "Explain quantum computing",
                "Debug my code",
                "Summarise this text"
            )
            suggestions.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    row.forEach { hint ->
                        SuggestionChip(
                            onClick = { /* handled via onSendMessage in parent */ },
                            label = { Text(hint, fontSize = 12.sp, color = TextSecondary) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = DarkSurface
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = DarkSurfaceBorder
                            )
                        )
                    }
                }
            }
        }
    }
}

// ── Message item ───────────────────────────────────────────────────────────
@Composable
fun MessageItem(message: ChatMessage) {
    if (message.isUser) UserMessageBubble(message) else AiMessageWithReasoning(message)
}

@Composable
fun UserMessageBubble(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    brush = Brush.linearGradient(listOf(WarmOrange, WarmOrange.copy(alpha = 0.88f))),
                    shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.text,
                color = OnWarmOrange,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * AI message: collapsible thinking chain + rendered markdown body (code blocks, inline code).
 */
@Composable
fun AiMessageWithReasoning(message: ChatMessage) {
    var thinkExpanded by remember { mutableStateOf(false) }

    val segments = remember(message.text) {
        if (message.text.isBlank()) emptyList()
        else MarkdownParser.parse(message.text)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 24.dp)
    ) {
        // ── Integrated thinking dropdown ──────────────────────────────
        if (message.thinkingText.isNotBlank()) {
            
            // Local state to track manual toggle. Automatically closes when thinking ends.
            var internalExpanded by remember(message.id) { mutableStateOf(message.isThinking) }
            LaunchedEffect(message.isThinking) {
                if (!message.isThinking && internalExpanded) {
                    internalExpanded = false
                }
            }

            // Live Timer
            var liveSeconds by remember(message.id) { mutableStateOf(0L) }
            LaunchedEffect(message.isThinking) {
                if (message.isThinking) {
                    var ms = 0L
                    while (true) {
                        kotlinx.coroutines.delay(100)
                        ms += 100
                        liveSeconds = ms
                    }
                }
            }
            
            val displayMs = if (message.isThinking) liveSeconds else message.thinkingDurationMs
            val seconds = (displayMs / 1000f)
            val durationText = if (seconds > 0) " for ${String.format("%.1fs", seconds)}" else ""
            val headerText = if (message.isThinking) "Thinking$durationText" else "Thought$durationText"

            val expanded = if (message.isThinking) true else internalExpanded

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { if (!message.isThinking) internalExpanded = !internalExpanded }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = headerText,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(8.dp))
                            .background(DarkSurfaceLight.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .animateContentSize()
                    ) {
                        val scrollState = rememberScrollState()
                        // Auto-scroll to bottom of thinking text while streaming
                        LaunchedEffect(message.thinkingText.length) {
                             scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        
                        Text(
                            text = message.thinkingText,
                            color = TextSecondary.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(scrollState)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }

        // ── Answer body ────────────────────────────────────────────────
        if (message.text.isEmpty() && message.thinkingText.isNotEmpty()) {
            // No placeholder text as per request
        } else if (message.text.isEmpty()) {
            // Initial placeholder (nothing yet)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.filter { 
                    it !is MessageSegment.Plain || it.text.isNotBlank() 
                }.forEach { segment ->
                    when (segment) {
                        is MessageSegment.Plain -> RenderedPlainText(segment.text)
                        is MessageSegment.CodeBlock -> CodeBlockView(segment.language, segment.code)
                        is MessageSegment.InlineCode -> InlineCodeChip(segment.code)
                    }
                }
            }
        }
    }
}

/** Renders plain text with support for bold (**), italic (*), and mixed inline segments */
@Composable
private fun RenderedPlainText(text: String) {
    val inlineSegments = remember(text) { MarkdownParser.parse(text) }
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        inlineSegments.forEach { seg ->
            when (seg) {
                is MessageSegment.Plain -> {
                    if (seg.text.isNotBlank()) {
                        Text(
                            text = parseMarkdownToAnnotatedString(seg.text.trimEnd('\n')),
                            color = OnDarkSurface,
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
                is MessageSegment.InlineCode -> InlineCodeChip(seg.code)
                is MessageSegment.CodeBlock -> CodeBlockView(seg.language, seg.code)
            }
        }
    }
}

/** Simple parser to convert **bold** and *italic* into AnnotatedString */
@Composable
private fun parseMarkdownToAnnotatedString(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold: **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                // Italic: *text* (avoiding match if inside bold or if it's just a bullet)
                text.startsWith("*", i) && (i == 0 || text[i-1] != '*') -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1 && end > i + 1 && text[end-1] != ' ') {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append("*")
                        i += 1
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

@Composable
private fun ThinkingPulseDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinkPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(WarmOrange.copy(alpha = alpha))
    )
}

// ── Typing Indicator ───────────────────────────────────────────────────────
@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    @Composable
    fun dot(offset: Int) = infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600), repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(offset)
        ), label = "dot$offset"
    )

    val d1 = dot(0); val d2 = dot(200); val d3 = dot(400)

    Row(
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(d1, d2, d3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(WarmOrange.copy(alpha = alpha.value))
            )
        }
    }
}

// ── Input Bar ──────────────────────────────────────────────────────────────
@Composable
fun GlassInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean
) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val canSend = text.isNotBlank() && !isGenerating

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceLight.copy(alpha = 0.97f), shape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(GlassBorder, GlassBorderSubtle)),
                shape = shape
            )
            .navigationBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        "Message Orch AI…",
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor    = OnDarkSurface,
                    unfocusedTextColor  = OnDarkSurface,
                    cursorColor         = WarmOrange,
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 6,
                minLines = 2,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            // Send / Stop button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isGenerating -> WarmOrange.copy(alpha = 0.2f)
                            canSend      -> WarmOrange
                            else         -> Color.Transparent
                        }
                    )
                    .clickable(enabled = canSend || isGenerating) {
                        if (isGenerating) { /* cancel handled outside */ } else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGenerating) Icons.Default.Stop else Icons.Default.ArrowUpward,
                    contentDescription = if (isGenerating) "Stop" else "Send",
                    tint = when {
                        isGenerating -> WarmOrange
                        canSend      -> OnWarmOrange
                        else         -> TextSecondary.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ── History Sidebar ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySidebar(
    conversations: List<ChatConversation>,
    currentConversationId: String?,
    onNewChat: () -> Unit,
    onLoadChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onDeleteAllHistory: () -> Unit
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        drawerContainerColor = DarkSurface,
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "History",
                    color = WarmOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                onClick = onNewChat,
                color = WarmOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat", tint = WarmOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("New Chat", color = WarmOrange, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (conversations.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No history", color = TextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    items(conversations, key = { it.id }) { convo ->
                        ConversationItem(
                            conversation = convo,
                            isSelected = convo.id == currentConversationId,
                            onClick = { onLoadChat(convo.id) },
                            onDelete = { onDeleteChat(convo.id) }
                        )
                    }
                }
            }

            if (conversations.isNotEmpty()) {
                Surface(
                    onClick = { showDeleteAllDialog = true },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear History", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear History", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Clear History", color = OnDarkSurface) },
            text = { Text("Delete all conversations? This cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDeleteAllHistory(); showDeleteAllDialog = false }) {
                    Text("Delete All", color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ConversationItem(
    conversation: ChatConversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = if (isSelected) WarmOrange.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isSelected) WarmOrange else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = conversation.title,
                color = if (isSelected) WarmOrange else OnDarkSurface,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete", color = OnDarkSurface) },
            text = { Text("Delete this conversation?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
