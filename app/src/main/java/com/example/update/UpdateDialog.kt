package com.example.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary

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

    AlertDialog(
        onDismissRequest = onLater,
        containerColor = OrchSurface,
        title = { Text(title, color = OrchTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state is UpdateState.Failed) OrchAccentCoral else OrchTextSecondary
                )
                when (state) {
                    is UpdateState.Downloading -> {
                        val progress = state.progress
                        if (progress == null) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = OrchTextPrimary,
                                trackColor = OrchBorder
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = OrchTextPrimary,
                                trackColor = OrchBorder
                            )
                        }
                    }
                    is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = OrchTextSecondary
                        )
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Ready -> TextButton(onClick = onRestart) {
                    Text("Restart", color = OrchAccentCoral)
                }
                is UpdateState.Failed -> TextButton(onClick = onRetry) {
                    Text("Try again", color = OrchAccentCoral)
                }
                else -> Unit
            }
        },
        dismissButton = {
            if (state !is UpdateState.Installing) {
                TextButton(onClick = onLater) { Text("Later", color = OrchTextSecondary) }
            }
        }
    )
}
