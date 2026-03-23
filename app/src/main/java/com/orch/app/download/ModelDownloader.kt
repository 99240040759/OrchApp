package com.orch.app.download

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class ModelDownloader(private val context: Context) {

    private val modelUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    private val fileName = "orch_model.gguf"

    fun getModelFile(): File {
        return File(context.getExternalFilesDir(null), fileName)
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() > 500_000_000L
    }

    fun downloadModel(): Flow<DownloadState> = flow {
        if (isModelDownloaded()) {
            emit(DownloadState.Completed(getModelFile()))
            return@flow
        }

        val file = getModelFile()
        if (file.exists()) {
            file.delete()
        }

        val request = DownloadManager.Request(Uri.parse(modelUrl))
            .setTitle("Downloading Orch Model")
            .setDescription("Downloading the reasoning model for offline use")
            .setDestinationInExternalFilesDir(context, null, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        var isDownloading = true
        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0) {
                    val status = cursor.getInt(statusIndex)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            isDownloading = false
                            emit(DownloadState.Completed(getModelFile()))
                        }
                        DownloadManager.STATUS_FAILED -> {
                            isDownloading = false
                            emit(DownloadState.Error("Download failed. Check internet connection or storage."))
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            val totalSizeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            if (totalSizeIndex >= 0 && downloadedIndex >= 0) {
                                val totalSize = cursor.getLong(totalSizeIndex)
                                val downloaded = cursor.getLong(downloadedIndex)
                                if (totalSize > 0) {
                                    val progress = (downloaded.toFloat() / totalSize.toFloat()) * 100f
                                    emit(DownloadState.Progress(progress.toInt()))
                                }
                            }
                        }
                    }
                }
            }
            cursor.close()
            delay(1000)
        }
    }
}

sealed class DownloadState {
    data class Progress(val percentage: Int) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
