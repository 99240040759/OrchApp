package com.example.data

import android.webkit.MimeTypeMap
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
        val name: String,
        val path: String,
        val mime: String,
        val sizeBytes: Long?,
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

    suspend fun load(workspace: String, path: String): ArtifactState.Ready = withContext(Dispatchers.IO) {
        val name = path.trimEnd('/').substringAfterLast('/').ifEmpty { "file" }
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"

        target(workspace, path, name).delete()

        when {
            extension in DOWNLOAD_ONLY -> ArtifactState.Ready(
                name = name,
                path = path,
                mime = mime,
                sizeBytes = null,
                content = ArtifactContent.DownloadOnly
            )
            extension == "pdf" -> {
                val file = fetch(workspace, path, name)
                ArtifactState.Ready(
                    name = name,
                    path = path,
                    mime = "application/pdf",
                    sizeBytes = file.length(),
                    content = ArtifactContent.Pdf(file)
                )
            }
            extension in RASTER_IMAGES -> {
                val file = fetch(workspace, path, name)
                ArtifactState.Ready(
                    name = name,
                    path = path,
                    mime = mime,
                    sizeBytes = file.length(),
                    content = ArtifactContent.Image(file)
                )
            }
            else -> {
                val text = try {
                    api.fileText(workspace, path)
                } catch (failure: Throwable) {
                    val apiError = failure.toApiError()
                    if (apiError.status == 400 && apiError.message.contains("binary", ignoreCase = true)) {
                        return@withContext ArtifactState.Ready(
                            name = name,
                            path = path,
                            mime = mime,
                            sizeBytes = null,
                            content = ArtifactContent.DownloadOnly
                        )
                    }
                    throw failure
                }
                val displayPath = text.path.ifBlank { path }
                val bytes = if (text.truncated) null else text.content.toByteArray().size.toLong()
                ArtifactState.Ready(
                    name = name,
                    path = displayPath,
                    mime = mime,
                    sizeBytes = bytes,
                    content = ArtifactContent.Text(
                        text = text.content,
                        truncated = text.truncated,
                        markdown = extension == "md" || extension == "markdown"
                    )
                )
            }
        }
    }

    suspend fun download(workspace: String, path: String, name: String): File =
        withContext(Dispatchers.IO) {
            target(workspace, path, name).takeIf { it.isFile && it.length() > 0 } ?: fetch(workspace, path, name)
        }

    private fun target(workspace: String, path: String, name: String): File {
        val folder = UUID.nameUUIDFromBytes("$workspace|$path".toByteArray()).toString()
        val safe = name.replace(UNSAFE, "_").trim('.').take(100).ifEmpty { "file" }
        return File(File(root, folder).apply { mkdirs() }, safe)
    }

    private suspend fun fetch(workspace: String, path: String, name: String): File {
        val file = target(workspace, path, name)
        val partial = File(file.parentFile, file.name + ".part")
        try {
            api.fileBinary(workspace, path).use { body ->
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

        val RASTER_IMAGES = setOf(
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "heic", "heif", "avif"
        )

        val DOWNLOAD_ONLY = setOf(
            "docx", "doc", "xlsx", "xls", "xlsm", "pptx", "ppt", "odt", "ods", "odp", "rtf",
            "pages", "numbers", "key",
            "zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar",
            "jar", "apk", "aab", "dex", "class", "so", "dll", "exe", "bin", "dmg", "iso",
            "mp3", "wav", "ogg", "flac", "aac", "m4a", "mp4", "mov", "mkv", "avi", "webm"
        )
    }
}
