package com.example.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.data.OrchJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Long,
    val tag: String,
    val apkUrl: String,
    val sizeBytes: Long
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tag: String = "",
    val assets: List<GithubAsset> = emptyList()
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    val size: Long = 0,
    @SerialName("browser_download_url") val url: String = ""
)

/**
 * Looks for a newer release of this app in GitHub Releases, downloads it and hands it to
 * Android's package installer.
 *
 * CI tags each release `v<run number>` and uses the same number as the app's versionCode,
 * so "newer" is a plain numeric comparison.
 */
class AppUpdater(private val context: Context) {

    // Deliberately separate from the Orch API client: that one attaches the user's Firebase
    // token to every request, which must never be sent to GitHub.
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val directory = File(context.cacheDir, "update")

    val installedVersion: Long
        get() = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /** The newest published release, or null when it has no installable APK. */
    suspend fun latest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.ORCH_UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        } ?: return@withContext null
        val release = runCatching { OrchJson.decodeFromString<GithubRelease>(body) }.getOrNull()
            ?: return@withContext null
        val versionCode = release.tag.removePrefix("v").toLongOrNull() ?: return@withContext null
        val asset = release.assets.firstOrNull { it.name == APK_NAME && it.url.isNotBlank() }
            ?: return@withContext null
        UpdateInfo(versionCode, release.tag, asset.url, asset.size)
    }

    /**
     * Downloads the APK (or reuses an already downloaded, valid copy) and returns it.
     * [onProgress] receives 0..1 when the total size is known.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = File(directory, "Orch-${info.versionCode}.apk")
        if (isValid(target, info)) return@withContext target

        directory.listFiles()?.forEach { it.delete() }
        val partial = File(directory, target.name + ".part")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("Download failed.")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            var copied = 0L
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        ensureActive()
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("Unable to store the update.")
        }
        if (!isValid(target, info)) {
            target.delete()
            throw IOException("The downloaded update is not valid. Please try again.")
        }
        target
    }

    /** Removes downloaded updates that are no longer newer than the installed app. */
    suspend fun cleanStale() = withContext(Dispatchers.IO) {
        directory.listFiles()?.forEach { file ->
            val version = archiveVersion(file)
            if (version == null || version <= installedVersion) file.delete()
        }
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the user allows this app to install updates. */
    fun openInstallSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Streams the APK into a package installer session and commits it. Android then asks the
     * user to confirm, replaces the app and closes it. Results arrive in [UpdateInstallReceiver].
     */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite(apk.name, 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(callback.intentSender)
        }
    }

    private fun isValid(file: File, info: UpdateInfo): Boolean =
        file.isFile && archiveVersion(file) == info.versionCode

    /** Version of the APK file if it is a valid package of this app, otherwise null. */
    @Suppress("DEPRECATION")
    private fun archiveVersion(file: File): Long? {
        if (!file.isFile || file.extension != "apk") return null
        val archive = context.packageManager.getPackageArchiveInfo(file.path, 0) ?: return null
        if (archive.packageName != context.packageName) return null
        return PackageInfoCompat.getLongVersionCode(archive)
    }

    private companion object {
        const val APK_NAME = "Orch.apk"
        const val BUFFER_BYTES = 64 * 1024
    }
}
