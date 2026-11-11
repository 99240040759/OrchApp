package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fcm.OrchCloudMessagingService
import com.example.ui.OrchCloudAuthenticatedApp
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {

    private var deepLinkSessionId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        OrchCloudMessagingService.ensureNotificationChannel(this)
        deepLinkSessionId = extractSessionIdFromIntent(intent)

        setContent {
            MyApplicationTheme {
                AppRoot(
                    deepLinkSessionId = deepLinkSessionId,
                    onDeepLinkConsumed = { deepLinkSessionId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSessionIdFromIntent(intent)?.let { sessionId ->
            deepLinkSessionId = sessionId
        }
    }

    private fun extractSessionIdFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        intent.getStringExtra(OrchCloudMessagingService.EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        val data = intent.data ?: return null
        if (data.scheme == "orchcloud" && data.host == "session") {
            return data.lastPathSegment?.takeIf { it.isNotBlank() && it != "dashboard" }
        }
        return null
    }
}

@Composable
fun AppRoot(
    auth: FirebaseAuth = Firebase.auth,
    deepLinkSessionId: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        key(uid) {
            OrchCloudAuthenticatedApp(
                currentUserId = uid,
                userEmail = user.email,
                userDisplayName = user.displayName,
                deepLinkSessionId = deepLinkSessionId,
                onDeepLinkConsumed = onDeepLinkConsumed
            )
        }
    }
}
