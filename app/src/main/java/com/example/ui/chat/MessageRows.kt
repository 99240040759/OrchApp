package com.example.ui.chat

import android.content.ClipData
import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.chat.ChatRow
import com.example.chat.CompactionRow
import com.example.chat.CopyRow
import com.example.chat.EarlierRow
import com.example.chat.NoticeRow
import com.example.chat.PendingRow
import com.example.chat.ReasoningItem
import com.example.chat.TextRow
import com.example.chat.ToolItem
import com.example.chat.ToolStatus
import com.example.chat.UserAttachment
import com.example.chat.UserRow
import com.example.chat.WorkRow
import com.example.data.ToolDisplay
import com.example.data.ToolIcon
import com.example.ui.components.OrchBanner
import com.example.ui.theme.OrchAdded
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchMonoBody
import com.example.ui.theme.OrchMonoLabel
import com.example.ui.theme.OrchRemoved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64

@Composable
fun ChatRowView(
    row: ChatRow,
    onOpenFile: (String) -> Unit,
    onLoadEarlier: () -> Unit
) {
    when (row) {
        is UserRow -> UserBubble(row, onOpenFile)
        is WorkRow -> WorkBlock(row, onOpenFile)
        is TextRow -> ChatMarkdown(row.text, streaming = row.live)
        is PendingRow -> StatusLabel(row.label)
        is CompactionRow -> CompactionNotice(row)
        is NoticeRow -> OrchBanner(text = row.text, isError = row.isError)
        is CopyRow -> CopyAction(row.text)
        is EarlierRow -> EarlierButton(row.loading, row.failed, onLoadEarlier)
    }
}

private val Whitespace = Regex("\\s+")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserBubble(row: UserRow, onOpenFile: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = (maxWidth * 0.85f).coerceAtMost(460.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (row.attachments.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.attachments.forEach { AttachmentChip(it, onOpenFile) }
                }
            }
            if (row.text.isNotBlank()) {
                val bubbleShape = MaterialTheme.shapes.large
                SelectionContainer {
                    Text(
                        text = row.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .widthIn(max = bubbleMaxWidth)
                            .clip(bubbleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, bubbleShape)
                            .padding(horizontal = OrchDimens.CardPaddingH, vertical = OrchDimens.CardPaddingV)
                    )
                }
            }
        }
    }
}

private const val THUMBNAIL_PX = 144

private fun decodeThumbnail(dataUrl: String): ImageBitmap? {
    if (!dataUrl.contains("base64,")) return null
    val bytes = Base64.Mime.decode(dataUrl.substringAfter("base64,"))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= THUMBNAIL_PX && bounds.outHeight / (sample * 2) >= THUMBNAIL_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

@Composable
private fun AttachmentChip(attachment: UserAttachment, onOpenFile: (String) -> Unit) {
    val thumbnail by produceState<ImageBitmap?>(null, attachment.dataUrl) {
        value = attachment.dataUrl?.let { url ->
            withContext(Dispatchers.Default) { runCatching { decodeThumbnail(url) }.getOrNull() }
        }
    }
    val path = attachment.path
    val previewModifier = Modifier
        .size(36.dp)
        .clip(MaterialTheme.shapes.extraSmall)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (path != null) Modifier.clickable { onOpenFile(path) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        val previewUri = attachment.previewUri
        val bitmap = thumbnail
        when {
            previewUri != null -> AsyncImage(
                model = previewUri,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = previewModifier
            )
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = previewModifier
            )
        }
        Text(
            text = attachment.name,
            style = OrchMonoLabel,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 200.dp)
                .padding(horizontal = 4.dp)
        )
    }
}

private fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    else -> {
        val m = seconds / 60
        val s = seconds % 60
        if (s == 0L) "${m}m" else "${m}m ${s}s"
    }
}

@Composable
private fun WorkBlock(row: WorkRow, onOpenFile: (String) -> Unit) {
    var toggled by rememberSaveable(row.key, row.live) { mutableStateOf<Boolean?>(null) }
    val open = toggled ?: row.live
    val title = remember(row.durationSeconds, row.stopped) {
        val base = row.durationSeconds?.takeIf { it > 0 }?.let { "Worked for ${formatDuration(it)}" } ?: "Worked"
        if (row.stopped) "$base · Stopped" else base
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { toggled = !open }
                .padding(vertical = 6.dp, horizontal = 2.dp)
        ) {
            if (row.live) {
                StatusLabel("Working")
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = if (open) "Collapse details" else "Expand details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (open) 90f else 0f)
            )
        }
        if (open) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }
    )
}

@Composable
private fun ThinkingBlock(item: ReasoningItem) {
    var toggled by rememberSaveable(item.id, item.active) { mutableStateOf<Boolean?>(null) }
    val open = toggled ?: item.active
    val label = when {
        item.active -> "Thinking"
        item.durationSeconds != null && item.durationSeconds > 0 -> "Thought for ${formatDuration(item.durationSeconds)}"
        else -> "Thought"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { toggled = !open }
                .padding(vertical = 6.dp, horizontal = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = if (open) "Collapse thinking" else "Expand thinking",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (open) 90f else 0f)
            )
        }
        if (open) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp, top = 2.dp, bottom = 4.dp)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ChatMarkdown(
                    text = item.text.trim(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    compact = true,
                    streaming = item.active
                )
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
                .heightIn(min = 36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 6.dp, horizontal = 2.dp)
        ) {
            Text(
                text = if (item.status == ToolStatus.Running) item.display.activeLabel else item.display.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ToolTarget(item.display, onOpenFile, Modifier.weight(1f, fill = false))
            when {
                item.status == ToolStatus.Running -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.status == ToolStatus.Error -> Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Tool call failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (expandable) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = if (expanded) "Collapse output" else "Expand output",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 90f else 0f)
                )
            }
        }
        if (expanded && expandable) {
            Text(
                text = output,
                style = OrchMonoBody,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
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
    ToolIcon.ZapOff -> Icons.Outlined.StopCircle
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
    val target = display.targetText ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = display.icon.vector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = target.replace(Whitespace, " "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FileTag(display: ToolDisplay, filename: String, onOpenFile: (String) -> Unit, modifier: Modifier) {
    val path = display.fullPath
    val canOpen = display.opensArtifact && path != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (canOpen && path != null) Modifier.clickable { onOpenFile(path) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = filename,
            style = OrchMonoLabel,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        display.lineRange?.let {
            Text(it, style = OrchMonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        display.addedLines?.takeIf { it > 0 }?.let {
            Text("+$it", style = OrchMonoLabel, color = OrchAdded)
        }
        display.removedLines?.takeIf { it > 0 }?.let {
            Text("-$it", style = OrchMonoLabel, color = OrchRemoved)
        }
    }
}

@Composable
private fun CompactionNotice(row: CompactionRow) {
    Text(
        text = if (row.originalMessageCount > 0) "Compacted ${row.originalMessageCount} messages" else "Context compacted",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun CopyAction(text: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
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
            .heightIn(min = 40.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("response", text)))
                    copied = true
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            contentDescription = "Copy response",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (copied) "Copied" else "Copy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EarlierButton(loading: Boolean, failed: Boolean, onLoad: () -> Unit) {
    LaunchedEffect(failed) { if (!failed) onLoad() }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TextButton(onClick = onLoad) {
                Text(
                    text = if (failed) "Couldn't load. Tap to retry" else "Load earlier messages",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

