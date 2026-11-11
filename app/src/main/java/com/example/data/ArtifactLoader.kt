package com.example.data

import android.util.Base64
import com.example.data.parser.DocxDocument
import com.example.data.parser.OfficeAndArtifactParser
import com.example.data.parser.PptxDeck
import com.example.data.parser.XlsxWorkbook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ArtifactContent {
    data class Pdf(val file: File) : ArtifactContent
    data class Docx(val document: DocxDocument) : ArtifactContent
    data class Xlsx(val workbook: XlsxWorkbook) : ArtifactContent
    data class Pptx(val deck: PptxDeck) : ArtifactContent
    data class Image(val dataUrl: String) : ArtifactContent
    data class Text(val text: String, val truncated: Boolean, val markdown: Boolean) : ArtifactContent
}

sealed interface ArtifactState {
    data object Hidden : ArtifactState
    data class Loading(val name: String, val path: String) : ArtifactState
    data class Failed(val name: String, val message: String) : ArtifactState
    data class Ready(
        val metadata: FileMetadata,
        val path: String,
        val file: File,
        val content: ArtifactContent
    ) : ArtifactState
}

class ArtifactLoader(private val api: OrchApi, cacheDir: File) {

    private val directory = File(cacheDir, "artifacts").apply { mkdirs() }

    suspend fun load(sessionId: String, path: String): ArtifactState.Ready = withContext(Dispatchers.IO) {
        val metadata = api.fileMetadata(sessionId, path)
        val file = File(directory, metadata.name.replace(UNSAFE, "_"))
        val extension = metadata.extension.lowercase().trimStart('.')
        val mime = metadata.mime.lowercase()

        suspend fun bytes(): ByteArray = api.fileBinary(sessionId, path).use { it.bytes() }.also { file.writeBytes(it) }

        val content: ArtifactContent = when {
            extension == "pdf" -> {
                bytes()
                ArtifactContent.Pdf(file)
            }
            extension == "docx" -> ArtifactContent.Docx(OfficeAndArtifactParser.parseDocx(metadata.name, bytes()))
            extension == "xlsx" || extension == "csv" ->
                ArtifactContent.Xlsx(OfficeAndArtifactParser.parseXlsxOrCsv(metadata.name, bytes()))
            extension == "pptx" -> ArtifactContent.Pptx(OfficeAndArtifactParser.parsePptx(bytes()))
            mime.startsWith("image/") -> {
                val dataUrl = api.fileImage(sessionId, path).dataUrl
                if (dataUrl.contains("base64,")) {
                    file.writeBytes(Base64.decode(dataUrl.substringAfter("base64,"), Base64.DEFAULT))
                }
                ArtifactContent.Image(dataUrl)
            }
            else -> {
                val text = api.fileText(sessionId, path)
                file.writeText(text.content)
                ArtifactContent.Text(text.content, text.truncated, extension == "md" || extension == "markdown")
            }
        }
        ArtifactState.Ready(metadata, path, file, content)
    }

    private companion object {
        val UNSAFE = Regex("[^A-Za-z0-9._-]")
    }
}
