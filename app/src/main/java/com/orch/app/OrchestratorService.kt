package com.orch.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * OrchestratorService — keeps Orch AI inference alive when the app is backgrounded.
 * Prevents Android's low-memory killer from halting mid-generation on constrained devices.
 *
 * Branded as "Orch AI" in all user-visible text.
 */
class OrchestratorService : Service() {

    companion object {
        const val CHANNEL_ID      = "orch_ai_inference"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START    = "com.orch.app.ACTION_START_INFERENCE"
        const val ACTION_STOP     = "com.orch.app.ACTION_STOP_INFERENCE"
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
        return START_NOT_STICKY // Don't restart automatically; lifecycle is tied to inference
    }

    private fun startForegroundInference() {
        // Tap the notification to bring the app back to foreground
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Orch AI")
            .setContentText("Generating response…")
            .setSubText("Tap to return")
            .setSmallIcon(R.mipmap.ic_launcher)          // Use app icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
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
            "Orch AI — Generating",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description   = "Shows while Orch AI is running a local inference"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
