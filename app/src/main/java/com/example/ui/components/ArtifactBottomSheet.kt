package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.ArtifactFormat
import com.example.data.model.DocxBlock
import com.example.data.model.DocxDocument
import com.example.data.model.FileMetadata
import com.example.data.model.PptxDeck
import com.example.data.model.XlsxWorkbook
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OrchAccentAmber
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchSurfaceElevated
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary
import com.example.ui.viewmodel.ArtifactSheetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactModalBottomSheet(
    state: ArtifactSheetState,
    onDismiss: () -> Unit
) {
    if (state is ArtifactSheetState.Hidden) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary,
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .testTag("artifact_modal_bottom_sheet")
    ) {
        when (state) {
            is ArtifactSheetState.Hidden -> Unit
            is ArtifactSheetState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = OrchAccentCoral)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading ${state.name}...",
                        style = MaterialTheme.typography.titleMedium,
                        color = OrchTextPrimary
                    )
                    Text(
                        text = state.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = OrchTextSecondary
                    )
                }
            }
            is ArtifactSheetState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unable to preview ${state.name}",
                        style = MaterialTheme.typography.titleLarge,
                        color = OrchAccentCoral
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrchTextSecondary
                    )
                }
            }
            is ArtifactSheetState.ReadyPdf -> {
                ArtifactHeaderBar(
                    metadata = state.metadata,
                    path = state.path,
                    shareFile = state.pdfFile,
                    onDismiss = onDismiss
                )
                PdfRendererContent(pdfFile = state.pdfFile)
            }
            is ArtifactSheetState.ReadyDocx -> {
                ArtifactHeaderBar(
                    metadata = state.metadata,
                    path = state.path,
                    shareFile = state.cacheFile,
                    onDismiss = onDismiss
                )
                DocxViewerContent(document = state.document)
            }
            is ArtifactSheetState.ReadyXlsx -> {
                ArtifactHeaderBar(
                    metadata = state.metadata,
                    path = state.path,
                    shareFile = state.cacheFile,
                    onDismiss = onDismiss
                )
                XlsxViewerContent(workbook = state.workbook)
            }
            is ArtifactSheetState.ReadyPptx -> {
                ArtifactHeaderBar(
                    metadata = state.metadata,
                    path = state.path,
                    shareFile = state.cacheFile,
                    onDismiss = onDismiss
                )
                PptxViewerContent(deck = state.deck)
            }
            is ArtifactSheetState.ReadyImage -> {
                ArtifactHeaderBar(
                    metadata = state.metadata,
                    path = state.path,
                    shareFile = state.localFile,
                    onDismiss = onDismiss
                )
                ImageZoomViewerContent(dataUrl = state.dataUrl, title = state.metadata.name)
            }
            is ArtifactSheetState.ReadyText -> {
                ArtifactHeaderBar(
                    metadata = state.metadata,
                    path = state.path,
                    shareFile = state.cacheFile,
                    onCopyText = state.content,
                    onDismiss = onDismiss
                )
                CodeAndMarkdownContent(
                    content = state.content,
                    truncated = state.truncated,
                    isMarkdown = state.isMarkdown
                )
            }
        }
    }
}

@Composable
private fun ArtifactHeaderBar(
    metadata: FileMetadata,
    path: String,
    shareFile: File?,
    onCopyText: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val format = remember(metadata) { ArtifactFormat.resolve(metadata.extension, metadata.mime) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = Color(format.badgeColorHex).copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(
                    1.dp,
                    Color(format.badgeColorHex).copy(alpha = 0.55f),
                    RoundedCornerShape(8.dp)
                )
            ) {
                Text(
                    text = metadata.extension.uppercase().ifBlank { format.name },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(format.badgeColorHex),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metadata.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OrchTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$path  •  ${formatBytes(metadata.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrchTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onCopyText != null) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(metadata.name, onCopyText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("artifact_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy content",
                        tint = OrchAccentCoral
                    )
                }
            }
            if (shareFile != null && shareFile.exists()) {
                IconButton(
                    onClick = { shareArtifactFile(context, shareFile, metadata.mime) },
                    modifier = Modifier.testTag("artifact_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share or export artifact",
                        tint = OrchAccentAmber
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("artifact_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close viewer",
                    tint = OrchTextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = OrchBorder)
    }
}

private fun shareArtifactFile(context: Context, file: File, mime: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export ${file.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PdfRendererContent(pdfFile: File) {
    var pageBitmaps by remember(pdfFile) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember(pdfFile) { mutableStateOf(true) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }

    DisposableEffect(pdfFile) {
        onDispose {
            pageBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    LaunchedEffect(pdfFile) {
        isLoading = true
        val rendered = withContext(Dispatchers.IO) {
            val list = mutableListOf<Bitmap>()
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount.coerceAtMost(20)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val width = (page.width * 2).coerceAtLeast(800)
                            val height = (page.height * 2).coerceAtLeast(1000)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            list.add(bitmap)
                        }
                    }
                }
            }
            list
        }
        pageBitmaps = rendered
        isLoading = false
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = OrchAccentCoral)
        }
        return
    }

    if (pageBitmaps.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("PDF contains 0 renderable pages.", color = OrchTextSecondary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                    enabled = currentPageIndex > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                }
                Text(
                    text = "Page ${currentPageIndex + 1} / ${pageBitmaps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = OrchAccentCoral
                )
                IconButton(
                    onClick = { if (currentPageIndex < pageBitmaps.lastIndex) currentPageIndex++ },
                    enabled = currentPageIndex < pageBitmaps.lastIndex
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { scale = (scale - 0.25f).coerceAtLeast(1f) },
                    label = { Text("- Zoom") }
                )
                AssistChip(
                    onClick = { scale = (scale + 0.25f).coerceAtMost(3f) },
                    label = { Text("+ Zoom (${(scale * 100).toInt()}%)") }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val bmp = pageBitmaps.getOrNull(currentPageIndex)
        if (bmp != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrchBackground)
                    .border(1.dp, OrchBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${currentPageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale
                        )
                )
            }
        }
    }
}

@Composable
private fun DocxViewerContent(document: DocxDocument) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(document.blocks) { block ->
            when (block) {
                is DocxBlock.Heading -> {
                    val textStyle = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = block.text,
                        style = textStyle,
                        color = if (block.level == 1) OrchAccentCoral else OrchTextPrimary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                is DocxBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OrchTextPrimary.copy(alpha = 0.92f)
                    )
                }
                is DocxBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "◆",
                            color = OrchAccentCoral,
                            modifier = Modifier.padding(end = 10.dp, top = 2.dp)
                        )
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OrchTextPrimary
                        )
                    }
                }
                is DocxBlock.TableGrid -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OrchSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OrchBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            block.rows.forEachIndexed { rowIdx, row ->
                                Row(
                                    modifier = Modifier
                                        .background(
                                            if (rowIdx == 0) OrchSurfaceElevated else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(vertical = 6.dp, horizontal = 8.dp)
                                ) {
                                    row.forEach { cell ->
                                        Text(
                                            text = cell,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                            color = if (rowIdx == 0) OrchAccentCoral else OrchTextPrimary,
                                            modifier = Modifier
                                                .width(160.dp)
                                                .padding(end = 12.dp)
                                        )
                                    }
                                }
                                if (rowIdx < block.rows.lastIndex) {
                                    HorizontalDivider(color = OrchBorder.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XlsxViewerContent(workbook: XlsxWorkbook) {
    var selectedSheetIdx by remember { mutableIntStateOf(0) }
    val activeSheet = workbook.sheets.getOrNull(selectedSheetIdx) ?: workbook.sheets.first()

    Column(modifier = Modifier.fillMaxSize()) {
        if (workbook.sheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedSheetIdx,
                containerColor = OrchSurface,
                contentColor = OrchAccentCoral,
                edgePadding = 16.dp
            ) {
                workbook.sheets.forEachIndexed { idx, sheet ->
                    Tab(
                        selected = idx == selectedSheetIdx,
                        onClick = { selectedSheetIdx = idx },
                        text = {
                            Text(
                                text = sheet.name,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }
        }

        val maxCols = activeSheet.rows.maxOfOrNull { it.size } ?: 1
        val hScroll = rememberScrollState()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(OrchSurface)
                .border(1.dp, OrchBorder, RoundedCornerShape(14.dp))
        ) {
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(hScroll)
                        .background(OrchSurfaceElevated)
                        .padding(vertical = 8.dp, horizontal = 10.dp)
                ) {
                    Text(
                        text = "#",
                        style = MaterialTheme.typography.labelMedium,
                        color = OrchTextSecondary,
                        modifier = Modifier.width(42.dp)
                    )
                    for (col in 0 until maxCols) {
                        val colLetter = ('A' + col).toString()
                        Text(
                            text = colLetter,
                            style = MaterialTheme.typography.labelMedium,
                            color = OrchAccentCoral,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }
                HorizontalDivider(color = OrchBorder)
            }

            itemsIndexed(activeSheet.rows) { rowIdx, row ->
                Row(
                    modifier = Modifier
                        .horizontalScroll(hScroll)
                        .background(
                            if (rowIdx == 0) OrchAccentCoral.copy(alpha = 0.08f)
                            else if (rowIdx % 2 == 1) OrchBackground.copy(alpha = 0.35f)
                            else Color.Transparent
                        )
                        .padding(vertical = 10.dp, horizontal = 10.dp)
                ) {
                    Text(
                        text = (rowIdx + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = OrchTextSecondary,
                        modifier = Modifier.width(42.dp)
                    )
                    for (col in 0 until maxCols) {
                        val value = row.getOrNull(col).orEmpty()
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = if (rowIdx == 0) MaterialTheme.typography.titleMedium.fontFamily else JetBrainsMonoFontFamily,
                            fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (rowIdx == 0) OrchAccentCoral else OrchTextPrimary,
                            modifier = Modifier
                                .width(150.dp)
                                .padding(end = 8.dp)
                        )
                    }
                }
                HorizontalDivider(color = OrchBorder.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun PptxViewerContent(deck: PptxDeck) {
    val pagerState = rememberPagerState(pageCount = { deck.slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(deck.slides) { idx, slide ->
                FilterChip(
                    selected = pagerState.currentPage == idx,
                    onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                    label = { Text("Slide ${slide.slideNumber}") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIdx ->
            val slide = deck.slides[pageIdx]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OrchSurface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp)
                        .border(1.dp, OrchAccentAmber.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SLIDE ${slide.slideNumber} OF ${deck.slides.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = OrchAccentAmber
                            )
                            Text(
                                text = "PRESENTATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = OrchTextSecondary
                            )
                        }

                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = OrchAccentCoral
                        )

                        HorizontalDivider(color = OrchBorder)

                        slide.bodyBullets.forEach { bullet ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "▸",
                                    color = OrchAccentAmber,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OrchTextPrimary
                                )
                            }
                        }
                    }
                }

                if (slide.speakerNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = OrchSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OrchBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "SPEAKER NOTES",
                                style = MaterialTheme.typography.labelMedium,
                                color = OrchAccentAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = slide.speakerNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OrchTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageZoomViewerContent(dataUrl: String?, title: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val decodedBitmap = remember(dataUrl) {
        if (dataUrl != null && dataUrl.contains("base64,")) {
            try {
                val base64Part = dataUrl.substringAfter("base64,")
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(OrchBackground)
            .border(1.dp, OrchBorder, RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            decodedBitmap != null -> {
                Image(
                    bitmap = decodedBitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }
            !dataUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = dataUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }
            else -> {
                Text(
                    text = "Image preview unavailable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrchTextSecondary
                )
            }
        }
    }
}

@Composable
private fun CodeAndMarkdownContent(
    content: String,
    truncated: Boolean,
    isMarkdown: Boolean
) {
    val lines = remember(content) { content.lines() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (truncated) {
            Surface(
                color = OrchAccentCoral.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Preview truncated (file exceeds 512 KB).",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrchAccentCoral,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(OrchBackground)
                .border(1.dp, OrchBorder, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            itemsIndexed(lines) { idx, line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.5.dp)
                ) {
                    if (!isMarkdown) {
                        Text(
                            text = "${idx + 1}".padStart(3, ' '),
                            style = MaterialTheme.typography.labelMedium,
                            color = OrchTextSecondary.copy(alpha = 0.55f),
                            modifier = Modifier.width(38.dp)
                        )
                    }
                    Text(
                        text = line,
                        style = if (isMarkdown && line.startsWith("#")) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontFamily = if (isMarkdown) MaterialTheme.typography.bodyLarge.fontFamily else JetBrainsMonoFontFamily,
                        color = when {
                            line.trimStart().startsWith("#") -> OrchAccentCoral
                            line.trimStart().startsWith("//") -> OrchAccentAmber
                            line.contains("pub fn") || line.contains("pub struct") -> OrchAccentAmber
                            else -> OrchTextPrimary
                        }
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
}
