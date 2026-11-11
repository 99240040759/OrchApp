package com.example.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.preferences.OrchCloudPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OrchCloudMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val appContext = applicationContext
        val prefs = OrchCloudPreferences(appContext)
        serviceScope.launch {
            prefs.setFcmToken(token)
            val user = Firebase.auth.currentUser
            if (user != null) {
                runCatching {
                    val db = FirebaseFirestore.getInstance(
                        appContext.getString(R.string.firestore_database_id)
                    )
                    db.collection("users")
                        .document(user.uid)
                        .set(
                            mapOf(
                                "fcm_token" to token,
                                "fcmToken" to token,
                                "email" to (user.email ?: ""),
                                "updated_at" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val sessionId = message.data["sessionId"] ?: message.data["session_id"]
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "OrchCloud Session Update"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: if (sessionId != null) "Tap to resume session $sessionId" else "Cloud agent run updated."

        showSessionNotification(
            context = applicationContext,
            sessionId = sessionId,
            title = title,
            body = body
        )
    }

    companion object {
        const val CHANNEL_ID = "orchcloud_runs_channel"
        const val EXTRA_SESSION_ID = "extra_orchcloud_session_id"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "OrchCloud Agent Runs",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when Cloud Run agent sessions finish, pause, or require attention."
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }

        fun showSessionNotification(
            context: Context,
            sessionId: String?,
            title: String,
            body: String
        ) {
            ensureNotificationChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val deepLinkIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    if (!sessionId.isNullOrBlank()) {
                        "orchcloud://session/$sessionId"
                    } else {
                        "orchcloud://session/dashboard"
                    }
                ),
                context,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!sessionId.isNullOrBlank()) {
                    putExtra(EXTRA_SESSION_ID, sessionId)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                sessionId?.hashCode() ?: 1001,
                deepLinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context)
                .notify(sessionId?.hashCode() ?: 1001, notification)
        }
    }
}
