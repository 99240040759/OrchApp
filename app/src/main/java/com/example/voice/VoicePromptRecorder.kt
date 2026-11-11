package com.example.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

class VoicePromptRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    /** Starts recording. [onLimit] fires on the main thread when [MAX_DURATION_MS] is reached. */
    fun start(onLimit: () -> Unit): Result<File> = runCatching {
        cancel()
        val file = File(File(context.cacheDir, "voice").apply { mkdirs() }, "prompt_${System.currentTimeMillis()}.m4a")
        val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            media.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(64000)
                setMaxDuration(MAX_DURATION_MS)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit()
                }
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (error: Exception) {
            media.release()
            file.delete()
            throw error
        }
        recorder = media
        output = file
        file
    }

    fun stop(): Result<Pair<String, String>> = runCatching {
        val media = checkNotNull(recorder) { "No active recording" }
        val file = checkNotNull(output) { "Recording file missing" }
        recorder = null
        output = null
        try {
            media.stop()
        } catch (error: RuntimeException) {
            file.delete()
            throw IllegalStateException("Recording was too short", error)
        } finally {
            media.release()
        }
        val bytes = try {
            file.readBytes()
        } finally {
            file.delete()
        }
        check(bytes.isNotEmpty()) { "Recorded audio is empty" }
        Base64.encodeToString(bytes, Base64.NO_WRAP) to "m4a"
    }

    fun cancel() {
        recorder?.let { media ->
            runCatching { media.stop() }
            runCatching { media.release() }
        }
        recorder = null
        output?.delete()
        output = null
    }

    companion object {
        const val MAX_DURATION_MS = 60_000
    }
}
