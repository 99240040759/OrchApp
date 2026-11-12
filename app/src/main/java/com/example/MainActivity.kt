package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.OrchApp
import com.example.ui.UserScope
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.update.UpdateDialog
import com.example.update.UpdateViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private fun FirebaseAuth.authState(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            MyApplicationTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot(auth: FirebaseAuth = Firebase.auth) {
    val userScope: UserScope = viewModel()
    val updates: UpdateViewModel = viewModel()
    val update by updates.dialog.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { updates.check() }
    val authState = remember(auth) { auth.authState() }
    val user by authState.collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val current = user
    if (current == null) {
        LaunchedEffect(Unit) { userScope.reset() }
        AuthScreen(
            autoPrompt = userScope.autoSignIn,
            onAuthSuccess = { userScope.autoSignIn = true }
        )
    } else {
        key(current.uid) {
            OrchApp(
                userScope = userScope,
                userId = current.uid,
                userName = current.displayName,
                userEmail = current.email
            )
        }
    }

    UpdateDialog(
        state = update,
        onRestart = updates::install,
        onRetry = updates::retry,
        onLater = updates::later
    )
}
