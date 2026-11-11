package com.example.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

class VoicePromptRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(): Result<File> = runCatching {
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
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (error: Exception) {
            media.release()
            throw error
        }
        recorder = media
        output = file
        file
    }

    fun stop(): Result<Pair<String, String>> = runCatching {
        val media = checkNotNull(recorder) { "No active recording" }
        recorder = null
        try {
            media.stop()
        } finally {
            media.release()
        }
        val file = checkNotNull(output) { "Recording file missing" }
        val bytes = file.readBytes().also { file.delete() }
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
}
