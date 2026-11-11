package com.example.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import java.io.File

class VoicePromptRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Result<File> = runCatching {
        stopRecordingCleanup()
        val voiceDir = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(voiceDir, "prompt_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        file
    }

    fun stopAndEncodeBase64(): Result<Pair<String, String>> = runCatching {
        val activeRecorder = recorder ?: error("No active voice recording")
        try {
            activeRecorder.stop()
        } finally {
            activeRecorder.release()
            recorder = null
        }
        val file = outputFile ?: error("Recorded audio file missing")
        val bytes = file.readBytes()
        require(bytes.isNotEmpty()) { "Recorded audio is empty" }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        encoded to "m4a"
    }

    fun stopRecordingCleanup() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }
}
