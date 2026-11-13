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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.ArtifactContent
import com.example.data.ArtifactState
import com.example.ui.chat.ChatMarkdown
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

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
            name = state.name
            subtitle = state.sizeBytes?.let { "${state.path}  ·  ${formatBytes(it)}" } ?: state.path
            mime = state.mime.ifBlank { DEFAULT_MIME }
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

    OrchBottomSheet(
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        OrchSheetHeader(
            title = name,
            subtitle = subtitle,
            onClose = onDismiss,
            actions = {
                if (saving) {
                    Box(modifier = Modifier.size(OrchDimens.TouchTarget), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = OrchTextSecondary)
                    }
                } else {
                    IconButton(onClick = download, enabled = canDownload) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download",
                            tint = if (canDownload) OrchTextPrimary else OrchTextSecondary
                        )
                    }
                }
            }
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is ArtifactState.Loading -> OrchCenteredSpinner()
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
private fun DownloadPanel(title: String, message: String, saving: Boolean, onDownload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(OrchDimens.ScreenPadding * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.InsertDriveFile, null, tint = OrchTextSecondary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
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
        Spacer(Modifier.height(20.dp))
        OrchPrimaryButton(
            text = "Download",
            onClick = onDownload,
            loading = saving,
            icon = Icons.Outlined.Download
        )
    }
}

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    data class Ready(val doc: PdfDoc) : PdfLoad
    data class Failed(val message: String) : PdfLoad
}

private class PdfDoc(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageCount: Int,
    val defaultSize: IntSize
) {
    val sizes = mutableStateMapOf<Int, IntSize>()
    private val lock = Mutex()
    private var closed = false

    suspend fun render(index: Int, widthPx: Int): Bitmap? = lock.withLock {
        currentCoroutineContext().ensureActive()
        if (closed) return@withLock null
        renderer.openPage(index).use { page ->
            sizes[index] = IntSize(page.width, page.height.coerceAtLeast(1))
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

private class PdfOwner {
    private var doc: PdfDoc? = null
    private var released = false

    fun open(file: File): PdfLoad {
        val result = openPdf(file)
        return if (result is PdfLoad.Ready && !adopt(result.doc)) PdfLoad.Loading else result
    }

    @Synchronized
    private fun adopt(opened: PdfDoc): Boolean {
        if (released) {
            opened.close()
            return false
        }
        doc = opened
        return true
    }

    @Synchronized
    fun release() {
        released = true
        doc?.close()
        doc = null
    }
}

private class PageCache : LruCache<Int, ImageBitmap>(PAGE_CACHE_BYTES) {
    override fun sizeOf(key: Int, value: ImageBitmap): Int = value.width * value.height * 4
}

private fun openPdf(file: File): PdfLoad {
    var descriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    return try {
        val opened = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor = opened
        val pdf = PdfRenderer(opened)
        renderer = pdf
        if (pdf.pageCount == 0) {
            pdf.close()
            opened.close()
            return PdfLoad.Failed("This PDF has no pages.")
        }
        val first = pdf.openPage(0).use { IntSize(it.width, it.height.coerceAtLeast(1)) }
        PdfLoad.Ready(PdfDoc(opened, pdf, pdf.pageCount, first))
    } catch (failure: Exception) {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        PdfLoad.Failed(
            if (failure is SecurityException) "This PDF is password protected. Download it to open it elsewhere."
            else "This PDF can't be displayed. Download it to open it elsewhere."
        )
    }
}

@Composable
private fun PdfViewer(file: File) {
    val owner = remember(file) { PdfOwner() }
    DisposableEffect(owner) { onDispose { owner.release() } }
    val load by produceState<PdfLoad>(PdfLoad.Loading, owner) {
        value = withContext(Dispatchers.IO) { owner.open(file) }
    }

    when (val current = load) {
        PdfLoad.Loading -> OrchCenteredSpinner()
        is PdfLoad.Failed -> OrchEmptyMessage(current.message)
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(count = doc.pageCount, key = { it }) { index ->
                PdfPage(doc, index, widthPx, cache)
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, index: Int, widthPx: Int, cache: PageCache) {
    val size = doc.sizes[index] ?: doc.defaultSize
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
            OrchBanner(
                text = "Preview is truncated. Download the file to get all of it.",
                isError = true,
                modifier = Modifier.padding(horizontal = OrchDimens.ScreenPadding, vertical = 6.dp)
            )
        }
        if (content.markdown) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OrchDimens.ScreenPadding, vertical = 12.dp)
            ) {
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
        OrchCenteredSpinner()
        return
    }
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val longest = remember(ready) { ready.maxOfOrNull { it.text.length } ?: 0 }
    val textWidth = remember(style, longest, density) {
        with(density) { (measurer.measure("0", style).size.width * longest).toDp() + 2.dp }
    }
    val horizontal = rememberScrollState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(OrchSurface),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        items(ready) { line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = line.number?.toString().orEmpty(),
                    style = style,
                    color = OrchTextSecondary.copy(alpha = 0.55f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(36.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f).horizontalScroll(horizontal)) {
                    Text(
                        text = line.text.ifEmpty { " " },
                        style = style,
                        color = OrchTextPrimary,
                        softWrap = false,
                        modifier = Modifier.width(textWidth)
                    )
                }
            }
        }
    }
}

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
