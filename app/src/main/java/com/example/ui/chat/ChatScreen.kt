package com.example.ui.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat.ChatRow
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

private val ResponseGap = 100.dp

@Composable
fun ChatScreen(
    title: String,
    chat: ChatUi,
    attachments: List<PendingAttachment>,
    models: List<ModelInfo>,
    selectedModelId: String,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onPickModel: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onOpenFile: (String) -> Unit,
    onLoadEarlier: () -> Unit,
    transcribe: suspend (String, String) -> String?
) {
    val model = models.firstOrNull { it.id == selectedModelId }
    val contextRatio = if (model != null && model.contextWindow > 0) {
        (chat.contextTokens.toFloat() / model.contextWindow).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OrchBackground)
            .imePadding()
    ) {
        TopBar(title, onMenu, onNewChat)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MessageList(chat, onOpenFile, onLoadEarlier)
            EdgeFade(top = true, modifier = Modifier.align(Alignment.TopCenter))
            EdgeFade(top = false, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Composer(
            placeholder = if (chat.canSend) "Ask anything" else "Connect a GitHub repository to start",
            streaming = chat.streaming,
            canSend = chat.canSend,
            attachments = attachments,
            modelLabel = model?.let { listOfNotNull(it.name, it.badge).joinToString(" · ") } ?: "Model",
            contextRatio = contextRatio,
            onSend = onSend,
            onStop = onStop,
            onAttach = onAttach,
            onPickModel = onPickModel,
            onRemoveAttachment = onRemoveAttachment,
            transcribe = transcribe
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
    val colors = if (top) listOf(OrchBackground, Color.Transparent) else listOf(Color.Transparent, OrchBackground)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (top) 16.dp else 22.dp)
            .background(Brush.verticalGradient(colors))
    )
}

@Composable
private fun MessageList(chat: ChatUi, onOpenFile: (String) -> Unit, onLoadEarlier: () -> Unit) {
    key(chat.sessionId) {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val rows: List<ChatRow> = chat.rows
        val reversed = remember(rows) { rows.asReversed() }
        val userCount = rows.count { it is UserRow }
        val atBottom by remember {
            derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
        }

        LaunchedEffect(userCount) { listState.scrollToItem(0) }

        Box(modifier = Modifier.fillMaxSize()) {
            if (rows.isEmpty()) {
                EmptyState(chat)
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = ResponseGap),
                modifier = Modifier.fillMaxSize()
            ) {
                items(reversed, key = { it.key }, contentType = { it::class }) { row ->
                    ChatRowView(row, onOpenFile, onLoadEarlier)
                }
            }
            AnimatedVisibility(
                visible = !atBottom,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
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
private fun EmptyState(chat: ChatUi) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        when {
            chat.loading -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = OrchTextSecondary
            )
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
