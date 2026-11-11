package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom

private fun generateSecureRandomNonce(byteLength: Int = 32): String {
    val randomBytes = ByteArray(byteLength)
    SecureRandom().nextBytes(randomBytes)
    return Base64.encodeToString(
        randomBytes,
        Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
    )
}

private suspend fun signInFirebaseWithCredentialResponse(
    result: GetCredentialResponse
): Boolean {
    val credential = result.credential
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        return true
    }
    return false
}

fun launchBottomSheetSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }

    val activityContext = (context as? Activity)?.let { MutableContextWrapper(it) } ?: context

    scope.launch {
        try {
            val authorizedOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .setNonce(generateSecureRandomNonce())
                .build()
            val authorizedRequest = GetCredentialRequest.Builder()
                .addCredentialOption(authorizedOption)
                .build()

            val result = credentialManager.getCredential(
                request = authorizedRequest,
                context = activityContext
            )
            if (signInFirebaseWithCredentialResponse(result)) {
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: NoCredentialException) {
            try {
                val allAccountsBottomSheetOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .setNonce(generateSecureRandomNonce())
                    .build()
                val bottomSheetRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(allAccountsBottomSheetOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = bottomSheetRequest,
                    context = activityContext
                )
                if (signInFirebaseWithCredentialResponse(result)) {
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Sign-in configuration unavailable.")
        return
    }

    val activity = context as? Activity
    if (activity == null) {
        onAuthError("Activity context required for Google Sign-In")
        return
    }
    val mutableContext = MutableContextWrapper(activity)

    scope.launch {
        try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .setNonce(generateSecureRandomNonce())
                .build()
            val signInRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(
                request = signInRequest,
                context = mutableContext
            )
            if (signInFirebaseWithCredentialResponse(result)) {
                onAuthSuccess()
            } else {
                onAuthError("Unsupported credential type.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Unable to sign in.")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val termsUrl = BuildConfig.ORCH_TERMS_URL
    val privacyUrl = BuildConfig.ORCH_PRIVACY_URL

    LaunchedEffect(Unit) {
        launchBottomSheetSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OrchBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_orch_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = OrchTextPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Sign in to continue to your cloud workspace.",
                style = MaterialTheme.typography.bodyLarge,
                color = OrchTextSecondary,
                textAlign = TextAlign.Center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        ) {
            if (errorMessage != null) {
                Surface(
                    color = OrchSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OrchAccentCoral, RoundedCornerShape(14.dp))
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrchAccentCoral,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrchSurface,
                    contentColor = OrchTextPrimary,
                    disabledContainerColor = OrchBorder,
                    disabledContentColor = OrchTextSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .border(1.dp, OrchBorder, RoundedCornerShape(16.dp))
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = OrchAccentCoral,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.sign_in_with_google),
                            style = MaterialTheme.typography.titleMedium,
                            color = OrchTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val linkStyles = TextLinkStyles(
                style = SpanStyle(
                    color = OrchAccentCoral,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            )
            val annotatedConsent = buildAnnotatedString {
                append("By continuing, you agree to Orch's ")
                withLink(
                    LinkAnnotation.Url(
                        url = termsUrl,
                        styles = linkStyles
                    )
                ) {
                    append("Terms of Service")
                }
                append(" and ")
                withLink(
                    LinkAnnotation.Url(
                        url = privacyUrl,
                        styles = linkStyles
                    )
                ) {
                    append("Privacy Policy")
                }
                append(".")
            }

            Text(
                text = annotatedConsent,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OrchTextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("legal_consent_text")
            )
        }
    }
}
