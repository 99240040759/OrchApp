package com.orch.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "UpdateChecker"

// ── Serialisable GitHub Release asset ─────────────────────────────────────
@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0L
)

@Serializable
data class GitHubRelease(
    val tag_name: String,               // "v2.0", "v1.1" etc.
    val name: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

// ── Update UI state (exposed to ViewModel) ─────────────────────────────────
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Available(
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: String
    ) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    data class ReadyToInstall(val apkFile: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

// ── Checker ────────────────────────────────────────────────────────────────
class UpdateChecker(
    private val context: Context,
    private val githubOwner: String,
    private val githubRepo: String,
    private val currentVersionCode: Int
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // URL for latest release metadata
    private val apiUrl: String
        get() = "https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest"

    /** Returns an [UpdateUiState] — call from IO dispatcher */
    suspend fun checkForUpdate(): UpdateUiState = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API returned ${response.code}")
                    return@withContext UpdateUiState.UpToDate // fail silently
                }
                val body = response.body?.string() ?: return@withContext UpdateUiState.UpToDate
                val release = json.decodeFromString<GitHubRelease>(body)

                // Parse version code from tag, e.g. "v2.0" → 20, or "v2" → 2
                val remoteVersionCode = parseVersionCode(release.tag_name)
                Log.i(TAG, "Remote tag=${release.tag_name} code=$remoteVersionCode, local=$currentVersionCode")

                if (remoteVersionCode <= currentVersionCode) {
                    return@withContext UpdateUiState.UpToDate
                }

                // Find the .apk asset
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext UpdateUiState.UpToDate

                UpdateUiState.Available(
                    versionName = release.tag_name.trimStart('v'),
                    apkUrl = apkAsset.browser_download_url,
                    releaseNotes = release.body.lines().take(3).joinToString("\n")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            UpdateUiState.UpToDate // Network errors are silent — don't annoy users
        }
    }

    /** Download APK to cacheDir/updates/orch-update.apk with progress 0..100 */
    suspend fun downloadApk(
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDir, "orch-update.apk")

        try {
            val request = Request.Builder().url(apkUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
                val body = response.body ?: throw IOException("Empty response body")
                val totalBytes = body.contentLength()
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(32_768) // 32 KB chunks
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = (downloaded * 100L / totalBytes).toInt()
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            if (apkFile.exists()) apkFile.delete()
            throw e
        }
        apkFile
    }

    /** Fire Android's native package installer intent */
    fun installApk(apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install intent failed", e)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private fun parseVersionCode(tag: String): Int {
        // Extracts the first number from tag (e.g., "v1.0" -> 1, "v2" -> 2)
        val clean = tag.trimStart('v', 'V')
        return clean.split(".").firstOrNull()?.toIntOrNull() ?: 0
    }
}
