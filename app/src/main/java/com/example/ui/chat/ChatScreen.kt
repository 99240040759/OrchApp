package com.example.ui.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.chat.ChatRow
import com.example.chat.EarlierRow
import com.example.chat.UserRow
import com.example.data.ModelInfo
import com.example.ui.ChatUi
import com.example.ui.PendingAttachment
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary
import kotlinx.coroutines.launch

/** Space between the end of the response and the input bar while the answer is streaming. */
private val StreamingBottomGap = 120.dp
private val IdleBottomGap = 24.dp

/** Space between a user prompt and the start of its response. */
private val PromptToReplyGap = 6.dp

/** Space between consecutive blocks inside one response. */
private val BlockGap = 10.dp

/** Space between the end of a response and the next prompt. */
private val TurnGap = 28.dp

@Composable
fun ChatScreen(
    title: String,
    chat: ChatUi,
    attachments: List<PendingAttachment>,
    models: List<ModelInfo>,
    selectedModelId: String,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onSend: (String) -> Boolean,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onPickModel: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onOpenFile: (String) -> Unit,
    onLoadEarlier: () -> Unit,
    onRetryLoad: () -> Unit,
    transcribe: suspend (String, String) -> String?
) {
    val model = remember(models, selectedModelId) { models.firstOrNull { it.id == selectedModelId } }
    val contextRatio = remember(chat.contextTokens, model) {
        if (model != null && model.contextWindow > 0) {
            (chat.contextTokens.toFloat() / model.contextWindow).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    val modelLabel = remember(model) {
        model?.let { listOfNotNull(it.name, it.badge).joinToString(" · ") } ?: "Model"
    }

    // Column layout: top bar, list (takes the remaining height), composer.
    // The composer consumes the navigation bar and keyboard insets, so the list is resized
    // by the system's animated inset instead of being covered by an overlay.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OrchBackground)
    ) {
        TopBar(title, onMenu, onNewChat)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MessageList(
                chat = chat,
                onOpenFile = onOpenFile,
                onLoadEarlier = onLoadEarlier,
                onRetryLoad = onRetryLoad
            )
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Composer(
            placeholder = when {
                chat.loading -> "Loading chat…"
                chat.canSend -> "Ask anything"
                chat.loadFailed -> "This chat could not be loaded"
                else -> "Connect a GitHub repository to start"
            },
            streaming = chat.streaming,
            canSend = chat.canSend && !chat.loading,
            attachments = attachments,
            modelLabel = modelLabel,
            contextRatio = contextRatio,
            onSend = onSend,
            onStop = onStop,
            onAttach = onAttach,
            onPickModel = onPickModel,
            onRemoveAttachment = onRemoveAttachment,
            transcribe = transcribe,
            modifier = Modifier.navigationBarsPadding().imePadding()
        )
    }
}

@Composable
private fun TopBar(title: String, onMenu: () -> Unit, onNewChat: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(OrchBackground)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Rounded.Menu, "Sessions", tint = OrchTextPrimary)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OrchTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        IconButton(onClick = onNewChat) {
            Icon(Icons.Outlined.EditNote, "New chat", tint = OrchTextPrimary)
        }
    }
}

@Composable
private fun EdgeFade(top: Boolean, modifier: Modifier) {
    val brush = remember(top) {
        val colors = if (top) listOf(OrchBackground, Color.Transparent) else listOf(Color.Transparent, OrchBackground)
        Brush.verticalGradient(colors)
    }
    Box(modifier = modifier.fillMaxWidth().height(14.dp).background(brush))
}

private fun gapAbove(above: ChatRow?, row: ChatRow): Dp = when {
    above == null -> 0.dp
    above is EarlierRow || row is EarlierRow -> 12.dp
    row is UserRow && above !is UserRow -> TurnGap
    row is UserRow -> BlockGap
    above is UserRow -> PromptToReplyGap
    else -> BlockGap
}

@Composable
private fun MessageList(
    chat: ChatUi,
    onOpenFile: (String) -> Unit,
    onLoadEarlier: () -> Unit,
    onRetryLoad: () -> Unit
) {
    key(chat.sessionId) {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val reversed = remember(chat.rows) { chat.rows.asReversed() }
        val atBottom by remember {
            derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
        }

        // Whether the user is parked at the newest message. Updated only when a scroll
        // gesture ends, so rows being inserted can't flip it.
        val stick = remember { booleanArrayOf(true) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                if (!scrolling) {
                    stick[0] = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                }
            }
        }

        // A keyed, reversed list keeps its anchor on the previously newest row, so a newly
        // added row would land below the viewport. Follow it when the user is at the bottom,
        // and always jump to the bottom when a new prompt is sent or a chat is opened.
        // Prepending older history changes neither key, so reading history is never interrupted.
        val seen = remember { arrayOfNulls<String>(2) }
        val newestKey = reversed.firstOrNull()?.key
        val newestPromptKey = reversed.firstOrNull { it is UserRow }?.key
        SideEffect {
            val promptChanged = seen[1] != newestPromptKey
            val rowChanged = seen[0] != newestKey
            seen[0] = newestKey
            seen[1] = newestPromptKey
            if (promptChanged || (rowChanged && stick[0])) {
                listState.requestScrollToItem(0)
                stick[0] = true
            }
        }

        val bottomGap by animateDpAsState(
            targetValue = if (chat.streaming) StreamingBottomGap else IdleBottomGap,
            animationSpec = tween(durationMillis = 250),
            label = "bottomGap"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            if (reversed.isEmpty()) {
                EmptyState(chat, onRetryLoad)
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomGap),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = reversed,
                    key = { _, row -> row.key },
                    contentType = { _, row -> row::class }
                ) { index, row ->
                    Box(modifier = Modifier.padding(top = gapAbove(reversed.getOrNull(index + 1), row))) {
                        ChatRowView(row, onOpenFile, onLoadEarlier)
                    }
                }
            }
            EdgeFade(top = true, modifier = Modifier.align(Alignment.TopCenter))
            EdgeFade(top = false, modifier = Modifier.align(Alignment.BottomCenter))
            AnimatedVisibility(
                visible = !atBottom,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    shape = CircleShape,
                    color = OrchSurface,
                    border = BorderStroke(1.dp, OrchBorder),
                    shadowElevation = 4.dp
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = "Scroll to latest",
                        tint = OrchTextPrimary,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(chat: ChatUi, onRetryLoad: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            chat.loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = OrchTextSecondary
            )
            chat.loadFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Couldn't load this chat",
                    style = MaterialTheme.typography.titleMedium,
                    color = OrchTextSecondary,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onRetryLoad) {
                    Text("Try again", color = OrchTextPrimary)
                }
            }
            chat.canSend -> Text(
                text = chat.repo?.let { "What should we work on in ${it.name}?" } ?: "Start the conversation",
                style = MaterialTheme.typography.titleMedium,
                color = OrchTextSecondary,
                textAlign = TextAlign.Center
            )
            else -> Text(
                text = "Tap + and connect a GitHub repository to start",
                style = MaterialTheme.typography.titleMedium,
                color = OrchTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
