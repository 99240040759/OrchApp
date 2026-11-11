package com.example.ui.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.chat.ChatItem
import com.example.chat.ChatRow
import com.example.chat.CopyRow
import com.example.chat.EarlierRow
import com.example.chat.NoticeRow
import com.example.chat.PendingRow
import com.example.chat.ReasoningItem
import com.example.chat.TextRow
import com.example.chat.ToolDisplay
import com.example.chat.ToolIcon
import com.example.chat.ToolItem
import com.example.chat.ToolStatus
import com.example.chat.UserAttachment
import com.example.chat.UserRow
import com.example.chat.WorkRow
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchAdded
import com.example.ui.theme.OrchRemoved
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchSurfaceElevated
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ChatRowView(
    row: ChatRow,
    onOpenFile: (String) -> Unit,
    onLoadEarlier: () -> Unit
) {
    when (row) {
        is UserRow -> UserBubble(row, onOpenFile)
        is WorkRow -> WorkBlock(row, onOpenFile)
        is TextRow -> ChatMarkdown(row.text)
        is PendingRow -> StatusLabel("Thinking")
        is NoticeRow -> Notice(row)
        is CopyRow -> CopyAction(row.text)
        is EarlierRow -> EarlierButton(row.loading, onLoadEarlier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserBubble(row: UserRow, onOpenFile: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (row.attachments.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.attachments.forEach { AttachmentChip(it, onOpenFile) }
            }
        }
        if (row.text.isNotBlank()) {
            SelectionContainer {
                Text(
                    text = row.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OrchTextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(OrchSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: UserAttachment, onOpenFile: (String) -> Unit) {
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, attachment.dataUrl) {
        value = attachment.dataUrl?.let { url ->
            withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val path = attachment.path
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OrchSurfaceElevated)
            .then(if (path != null) Modifier.clickable { onOpenFile(path) } else Modifier)
            .padding(6.dp)
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
            )
        }
        Text(
            text = attachment.name,
            style = MaterialTheme.typography.labelMedium,
            color = OrchTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp).padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun WorkBlock(row: WorkRow, onOpenFile: (String) -> Unit) {
    var toggled by rememberSaveable(row.key) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(row.live) { if (!row.live) toggled = null }
    val open = toggled ?: row.live

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { toggled = !open }
                .padding(vertical = 4.dp)
        ) {
            if (row.live) {
                StatusLabel(currentStatus(row.items))
            } else {
                Text(
                    text = "Worked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrchTextSecondary
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = OrchTextSecondary,
                modifier = Modifier.size(18.dp).rotate(if (open) 90f else 0f)
            )
        }
        if (open) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
            ) {
                row.items.forEach { item ->
                    when (item) {
                        is ReasoningItem -> ThinkingBlock(item)
                        is ToolItem -> ToolRow(item, onOpenFile)
                        else -> Unit
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (rest == 0) "${minutes}m" else "${minutes}m ${rest}s"
}

private fun currentStatus(items: List<ChatItem>): String {
    val running = items.lastOrNull { it is ToolItem && it.status == ToolStatus.Running } as? ToolItem
    if (running == null) return "Thinking"
    val display = running.display
    val target = (display.filename ?: display.target).orEmpty().replace(Regex("\\s+"), " ").trim()
    return if (target.isEmpty()) display.activeLabel else "${display.activeLabel} $target"
}

@Composable
private fun StatusLabel(text: String) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = OrchTextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }
    )
}

@Composable
private fun ThinkingBlock(item: ReasoningItem) {
    var toggled by rememberSaveable(item.id) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(item.active) { if (!item.active) toggled = null }
    val open = toggled ?: item.active
    val elapsed by produceState(item.durationSec, item.active, item.startedAt) {
        if (!item.active) {
            value = item.durationSec
            return@produceState
        }
        while (true) {
            value = ((System.currentTimeMillis() - item.startedAt) / 1000).toInt().coerceAtLeast(1)
            delay(1000)
        }
    }
    val label = when {
        item.active -> "Thinking for ${formatDuration(elapsed)}"
        item.durationSec > 0 -> "Thought for ${formatDuration(item.durationSec)}"
        else -> "Thought process"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { toggled = !open }
                .padding(vertical = 4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = OrchTextSecondary)
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = OrchTextSecondary,
                modifier = Modifier.size(18.dp).rotate(if (open) 90f else 0f)
            )
        }
        if (open) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp, top = 2.dp, bottom = 4.dp)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ChatMarkdown(item.text.trim(), color = OrchTextSecondary)
            }
        }
    }
}

@Composable
private fun ToolRow(item: ToolItem, onOpenFile: (String) -> Unit) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    val output = item.output?.trim().orEmpty()
    val expandable = output.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = item.display.label,
                style = MaterialTheme.typography.bodyMedium,
                color = OrchTextSecondary
            )
            ToolTarget(item.display, onOpenFile, Modifier.weight(1f, fill = false))
            when {
                item.status == ToolStatus.Running -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = OrchTextSecondary
                )
                item.status == ToolStatus.Error -> Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Tool call failed",
                    tint = OrchAccentCoral,
                    modifier = Modifier.size(14.dp)
                )
            }
            if (expandable) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = OrchTextSecondary,
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f)
                )
            }
        }
        if (expanded && expandable) {
            Text(
                text = output,
                style = MaterialTheme.typography.labelMedium,
                color = OrchTextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OrchSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            )
        }
    }
}

private fun ToolIcon.vector(): ImageVector = when (this) {
    ToolIcon.Terminal -> Icons.Outlined.Terminal
    ToolIcon.Search -> Icons.Outlined.Search
    ToolIcon.Globe -> Icons.Outlined.Language
    ToolIcon.Book -> Icons.Outlined.MenuBook
    ToolIcon.Cpu -> Icons.Outlined.Memory
    ToolIcon.Stop -> Icons.Outlined.StopCircle
    ToolIcon.Folder -> Icons.Outlined.Folder
    ToolIcon.File -> Icons.Outlined.InsertDriveFile
}

@Composable
private fun ToolTarget(display: ToolDisplay, onOpenFile: (String) -> Unit, modifier: Modifier) {
    val filename = display.filename
    if (filename != null) {
        FileTag(display, filename, onOpenFile, modifier)
        return
    }
    val target = display.target ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Icon(display.icon.vector(), null, tint = OrchTextSecondary, modifier = Modifier.size(14.dp))
        Text(
            text = target.replace(Regex("\\s+"), " "),
            style = MaterialTheme.typography.bodyMedium,
            color = OrchTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FileTag(display: ToolDisplay, filename: String, onOpenFile: (String) -> Unit, modifier: Modifier) {
    val path = display.fullPath
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(OrchSurfaceElevated)
            .then(if (path != null) Modifier.clickable { onOpenFile(path) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = filename,
            style = MaterialTheme.typography.labelMedium,
            color = OrchTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        display.lineRange?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = OrchTextSecondary)
        }
        display.added?.takeIf { it > 0 }?.let {
            Text("+$it", style = MaterialTheme.typography.labelSmall, color = OrchAdded)
        }
        display.removed?.takeIf { it > 0 }?.let {
            Text("-$it", style = MaterialTheme.typography.labelSmall, color = OrchRemoved)
        }
    }
}

@Composable
private fun Notice(row: NoticeRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (row.isError) OrchAccentCoral.copy(alpha = 0.12f) else OrchSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (row.isError) {
            Icon(Icons.Rounded.WarningAmber, null, tint = OrchAccentCoral, modifier = Modifier.size(16.dp))
        }
        Text(
            text = row.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.isError) OrchAccentCoral else OrchTextSecondary
        )
    }
}

@Composable
private fun CopyAction(text: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                clipboard.setText(AnnotatedString(text))
                copied = true
            }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            contentDescription = "Copy response",
            tint = OrchTextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = if (copied) "Copied" else "Copy",
            style = MaterialTheme.typography.labelMedium,
            color = OrchTextSecondary
        )
    }
}

@Composable
private fun EarlierButton(loading: Boolean, onLoad: () -> Unit) {
    LaunchedEffect(Unit) { onLoad() }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OrchTextSecondary)
        } else {
            TextButton(onClick = onLoad) {
                Text("Load earlier messages", color = OrchTextSecondary)
            }
        }
    }
}
