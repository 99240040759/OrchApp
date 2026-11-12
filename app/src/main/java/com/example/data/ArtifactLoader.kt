package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface ArtifactContent {
    data class Pdf(val file: File) : ArtifactContent
    data class Image(val file: File) : ArtifactContent
    data class Text(val text: String, val truncated: Boolean, val markdown: Boolean) : ArtifactContent

    data object DownloadOnly : ArtifactContent
}

sealed interface ArtifactState {
    data object Hidden : ArtifactState
    data class Loading(val name: String, val path: String) : ArtifactState
    data class Failed(val name: String, val path: String, val message: String) : ArtifactState
    data class Ready(
        val metadata: FileMetadata,
        val path: String,
        val content: ArtifactContent
    ) : ArtifactState
}

class ArtifactLoader(private val api: OrchApi, cacheDir: File) {
    private val root = File(cacheDir, "artifacts")

    init {
        clear()
    }

    fun clear() {
        root.deleteRecursively()
        root.mkdirs()
    }

    suspend fun load(sessionId: String, path: String): ArtifactState.Ready = withContext(Dispatchers.IO) {
        val metadata = api.fileMetadata(sessionId, path)
        val extension = metadata.extension.lowercase().trimStart('.')
        val mime = metadata.mime.lowercase()

        target(sessionId, path, metadata.name).delete()

        val content: ArtifactContent = when {
            extension in DOWNLOAD_ONLY -> ArtifactContent.DownloadOnly
            extension == "pdf" -> ArtifactContent.Pdf(fetch(sessionId, path, metadata.name))
            mime.startsWith("image/") && !mime.contains("svg") && extension != "svg" ->
                ArtifactContent.Image(fetch(sessionId, path, metadata.name))
            else -> {
                val text = api.fileText(sessionId, path)
                ArtifactContent.Text(text.content, text.truncated, extension == "md" || extension == "markdown")
            }
        }
        ArtifactState.Ready(metadata, path, content)
    }

    suspend fun download(sessionId: String, path: String, name: String): File =
        withContext(Dispatchers.IO) {
            target(sessionId, path, name).takeIf { it.isFile && it.length() > 0 } ?: fetch(sessionId, path, name)
        }

    private fun target(sessionId: String, path: String, name: String): File {
        val folder = UUID.nameUUIDFromBytes("$sessionId|$path".toByteArray()).toString()
        val safe = name.replace(UNSAFE, "_").trim('.').take(100).ifEmpty { "file" }
        return File(File(root, folder).apply { mkdirs() }, safe)
    }

    private suspend fun fetch(sessionId: String, path: String, name: String): File {
        val file = target(sessionId, path, name)
        val partial = File(file.parentFile, file.name + ".part")
        try {
            api.fileBinary(sessionId, path).use { body ->
                body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            }
        } catch (failure: Throwable) {
            partial.delete()
            throw failure
        }
        file.delete()
        check(partial.renameTo(file)) { "Unable to store ${file.name}." }
        return file
    }

    private companion object {
        val UNSAFE = Regex("[^A-Za-z0-9._-]")

        val DOWNLOAD_ONLY = setOf(
            "docx", "doc", "xlsx", "xls", "xlsm", "pptx", "ppt", "odt", "ods", "odp", "rtf",
            "pages", "numbers", "key",
            "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar",
            "jar", "apk", "aab", "dex", "class", "so", "dll", "exe", "bin", "dmg", "iso",
            "mp3", "wav", "ogg", "flac", "aac", "m4a", "mp4", "mov", "mkv", "avi", "webm"
        )
    }
}
