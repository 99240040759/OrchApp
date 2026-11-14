package com.example.update

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.OrchAlertDialog

@Composable
fun UpdateDialog(
    state: UpdateState,
    onRestart: () -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit
) {
    if (state is UpdateState.Idle) return

    val title: String
    val message: String
    when (state) {
        is UpdateState.Downloading -> {
            title = "Updating Orch"
            message = "Downloading ${state.info.tag}…"
        }
        is UpdateState.Ready -> {
            title = "Update ready"
            message = "${state.info.tag} is downloaded. Orch will close while Android installs it, " +
                "then open it again from your home screen."
        }
        is UpdateState.Installing -> {
            title = "Installing update"
            message = "Confirm the install if Android asks."
        }
        is UpdateState.Failed -> {
            title = "Update failed"
            message = state.message
        }
        UpdateState.Idle -> return
    }

    val confirmLabel = when (state) {
        is UpdateState.Ready -> "Restart"
        is UpdateState.Failed -> "Try again"
        else -> null
    }
    val onConfirm: (() -> Unit)? = when (state) {
        is UpdateState.Ready -> onRestart
        is UpdateState.Failed -> onRetry
        else -> null
    }

    OrchAlertDialog(
        title = title,
        onDismiss = onLater,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        dismissLabel = "Later"
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state is UpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        when (state) {
            is UpdateState.Downloading -> {
                val progress = state.progress
                if (progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                }
            }
            is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> Unit
        }
    }
}
