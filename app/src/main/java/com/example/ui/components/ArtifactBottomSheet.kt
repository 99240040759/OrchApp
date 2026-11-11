package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.ArtifactContent
import com.example.data.ArtifactState
import com.example.ui.chat.ChatMarkdown
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-height viewer for a sandbox file: PDFs, images and text are previewed,
 * every other format offers a download. The only actions are Download and Close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactModalBottomSheet(
    state: ArtifactState,
    saving: Boolean,
    onSave: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is ArtifactState.Hidden) return

    val name: String
    val subtitle: String
    val mime: String
    val canDownload: Boolean
    when (state) {
        is ArtifactState.Ready -> {
            name = state.metadata.name
            subtitle = "${state.path}  ·  ${formatBytes(state.metadata.sizeBytes)}"
            mime = state.metadata.mime.ifBlank { DEFAULT_MIME }
            canDownload = true
        }
        is ArtifactState.Failed -> {
            name = state.name
            subtitle = state.path
            mime = DEFAULT_MIME
            canDownload = true
        }
        is ArtifactState.Loading -> {
            name = state.name
            subtitle = state.path
            mime = DEFAULT_MIME
            canDownload = false
        }
        ArtifactState.Hidden -> return
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { destination ->
        if (destination != null) onSave(destination)
    }
    val download = { saveLauncher.launch(name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary,
        modifier = Modifier.fillMaxSize()
    ) {
        SheetHeader(
            title = name,
            subtitle = subtitle,
            saving = saving,
            canDownload = canDownload,
            onDownload = download,
            onClose = onDismiss
        )
        HorizontalDivider(color = OrchBorder)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is ArtifactState.Loading -> CenteredSpinner()
                is ArtifactState.Failed -> DownloadPanel(
                    title = "Unable to open $name",
                    message = state.message,
                    saving = saving,
                    onDownload = download
                )
                is ArtifactState.Ready -> when (val content = state.content) {
                    is ArtifactContent.Pdf -> PdfViewer(content.file)
                    is ArtifactContent.Image -> ImageViewer(content.file, name)
                    is ArtifactContent.Text -> TextViewer(content)
                    ArtifactContent.DownloadOnly -> DownloadPanel(
                        title = name,
                        message = "This file type can't be previewed here.",
                        saving = saving,
                        onDownload = download
                    )
                }
                ArtifactState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    subtitle: String,
    saving: Boolean,
    canDownload: Boolean,
    onDownload: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OrchTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = OrchTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (saving) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = OrchTextSecondary)
            }
        } else {
            IconButton(onClick = onDownload, enabled = canDownload) {
                Icon(Icons.Outlined.Download, "Download", tint = if (canDownload) OrchTextPrimary else OrchTextSecondary)
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, "Close", tint = OrchTextSecondary)
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = OrchAccentCoral)
    }
}

@Composable
private fun DownloadPanel(title: String, message: String, saving: Boolean, onDownload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.InsertDriveFile, null, tint = OrchTextSecondary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = OrchTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = OrchTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDownload,
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(
                containerColor = OrchTextPrimary,
                contentColor = OrchBackground,
                disabledContainerColor = OrchBorder
            )
        ) {
            Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download")
        }
    }
}

// region PDF

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data class Ready(val doc: PdfDoc) : PdfLoad
    data class Failed(val message: String) : PdfLoad
}

/** A PDF opened once; pages are rendered on demand, one at a time (PdfRenderer allows only one open page). */
private class PdfDoc(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val sizes: List<IntSize>
) {
    private val lock = Mutex()
    private var closed = false

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        renderer.openPage(index).use { page ->
            val ratio = page.height.toFloat() / page.width.toFloat()
            var width = widthPx
            var height = (width * ratio).toInt()
            if (height > MAX_PAGE_HEIGHT_PX) {
                height = MAX_PAGE_HEIGHT_PX
                width = (height / ratio).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(width, height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /** Waits for any page still rendering, then releases the file. */
    fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                if (!closed) {
                    closed = true
                    runCatching { renderer.close() }
                    runCatching { descriptor.close() }
                }
            }
        }
    }
}

private class PageCache : LruCache<Int, ImageBitmap>(PAGE_CACHE_BYTES) {
    override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 4
}

private fun openPdf(file: File): PdfLoad {
    var descriptor: ParcelFileDescriptor? = null
    return try {
        val opened = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor = opened
        val renderer = PdfRenderer(opened)
        if (renderer.pageCount == 0) {
            renderer.close()
            opened.close()
            return PdfLoad.Failed("This PDF has no pages.")
        }
        val sizes = List(renderer.pageCount) { index ->
            renderer.openPage(index).use { IntSize(it.width, it.height.coerceAtLeast(1)) }
        }
        PdfLoad.Ready(PdfDoc(opened, renderer, sizes))
    } catch (_: SecurityException) {
        runCatching { descriptor?.close() }
        PdfLoad.Failed("This PDF is password protected. Download it to open it elsewhere.")
    } catch (_: Exception) {
        runCatching { descriptor?.close() }
        PdfLoad.Failed("This PDF can't be displayed. Download it to open it elsewhere.")
    }
}

@Composable
private fun PdfViewer(file: File) {
    val load by produceState<PdfLoad>(PdfLoad.Loading, file) {
        value = withContext(Dispatchers.IO) { openPdf(file) }
    }
    val doc = (load as? PdfLoad.Ready)?.doc
    DisposableEffect(doc) { onDispose { doc?.close() } }

    when (val current = load) {
        PdfLoad.Loading -> CenteredSpinner()
        is PdfLoad.Failed -> Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(current.message, style = MaterialTheme.typography.bodyMedium, color = OrchTextSecondary, textAlign = TextAlign.Center)
        }
        is PdfLoad.Ready -> PdfPages(current.doc)
    }
}

@Composable
private fun PdfPages(doc: PdfDoc) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceIn(1, MAX_PAGE_WIDTH_PX)
        val cache = remember(doc, widthPx) { PageCache() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = doc.sizes.size, key = { it }) { index ->
                PdfPage(doc, index, widthPx, cache)
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int, cache: PageCache) {
    val size = doc.sizes[index]
    val bitmap by produceState<ImageBitmap?>(cache.get(index), doc, index, widthPx) {
        if (value == null) {
            val rendered = withContext(Dispatchers.IO) { doc.render(index, widthPx) }
            if (rendered != null) {
                val image = rendered.asImageBitmap()
                cache.put(index, image)
                value = image
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(size.width.toFloat() / size.height.toFloat())
            .background(Color.White)
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// endregion

// region Image

@Composable
private fun ImageViewer(file: File, title: String) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }

    val transform = rememberTransformableState { zoom, pan, _ ->
        val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        val limitX = bounds.width * (newScale - 1f) / 2f
        val limitY = bounds.height * (newScale - 1f) / 2f
        offset = Offset(
            x = (offset.x + pan.x).coerceIn(-limitX, limitX),
            y = (offset.y + pan.y).coerceIn(-limitY, limitY)
        )
        scale = newScale
    }
    val request = remember(file) {
        ImageRequest.Builder(context)
            .data(file)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { bounds = it }
            // One finger only pans once zoomed in, so the sheet can still be dragged away.
            .transformable(state = transform, canPan = { scale > 1f })
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = DOUBLE_TAP_ZOOM
                    }
                })
            }
    ) {
        AsyncImage(
            model = request,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

// endregion

// region Text

private class CodeLine(val number: Int?, val text: String)

private fun toCodeLines(text: String): List<CodeLine> {
    val lines = ArrayList<CodeLine>()
    text.lineSequence().forEachIndexed { index, raw ->
        val line = raw.trimEnd('\r').replace("\t", "    ")
        if (line.length <= MAX_LINE_CHARS) {
            lines.add(CodeLine(index + 1, line))
        } else {
            line.chunked(MAX_LINE_CHARS).forEachIndexed { part, chunk ->
                lines.add(CodeLine(if (part == 0) index + 1 else null, chunk))
            }
        }
    }
    return lines
}

@Composable
private fun TextViewer(content: ArtifactContent.Text) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (content.truncated) {
            Text(
                text = "Preview is truncated. Download the file to get all of it.",
                style = MaterialTheme.typography.labelSmall,
                color = OrchAccentCoral,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        if (content.markdown) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Parsed off the main thread: documents can be large.
                ChatMarkdown(content.text, streaming = true)
            }
        } else {
            CodeView(content.text)
        }
    }
}

@Composable
private fun CodeView(text: String) {
    val lines by produceState<List<CodeLine>?>(null, text) {
        value = withContext(Dispatchers.Default) { toCodeLines(text) }
    }
    val ready = lines
    if (ready == null) {
        CenteredSpinner()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(OrchSurface),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        items(ready) { line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = line.number?.toString().orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = OrchTextSecondary.copy(alpha = 0.55f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = line.text.ifEmpty { " " },
                    style = MaterialTheme.typography.labelMedium,
                    color = OrchTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// endregion

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
}

private const val DEFAULT_MIME = "application/octet-stream"
private const val MAX_PAGE_WIDTH_PX = 1600
private const val MAX_PAGE_HEIGHT_PX = 4096
private const val PAGE_CACHE_BYTES = 32 * 1024 * 1024
private const val MAX_LINE_CHARS = 600
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
