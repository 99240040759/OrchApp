package com.orch.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the AI inference process alive
 * even when the app is backgrounded, preventing Android from killing
 * the LLM inference thread mid-generation on low-RAM devices.
 *
 * FIX #2: Prevents Android from killing inference mid-generation.
 */
class LlamaForegroundService : Service() {

    companion object {
        const val CHANNEL_ID    = "llama_inference_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START  = "com.orch.app.START_INFERENCE"
        const val ACTION_STOP   = "com.orch.app.STOP_INFERENCE"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundInference()
            ACTION_STOP  -> stopForegroundInference()
        }
        return START_STICKY
    }

    private fun startForegroundInference() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Orch — AI Thinking")
            .setContentText("Running local offline model…")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopForegroundInference() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Local AI Inference",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the offline AI model running in the background"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
