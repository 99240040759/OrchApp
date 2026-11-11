package com.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.ChatUi
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchAdded
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    chat: ChatUi,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onOpenExplorer: () -> Unit,
    onConnectRepo: (String) -> Unit,
    onDisconnectRepo: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary
    ) {
        Column(modifier = Modifier.imePadding().padding(bottom = 16.dp)) {
            SheetAction(Icons.Outlined.Image, "Photos", "Attach images") {
                onDismiss()
                onPickImages()
            }
            SheetAction(Icons.Outlined.AttachFile, "Files", "Attach documents and code") {
                onDismiss()
                onPickFiles()
            }
            SheetAction(
                icon = Icons.Outlined.FolderOpen,
                title = "File explorer",
                subtitle = if (chat.repoLocked) "Browse the workspace sandbox" else "Available after the first message",
                enabled = chat.repoLocked
            ) {
                onDismiss()
                onOpenExplorer()
            }
            GitHubSection(chat, onConnectRepo, onDisconnectRepo)
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        IconBadge { Icon(icon, null, tint = OrchTextPrimary, modifier = Modifier.size(20.dp)) }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = OrchTextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OrchTextSecondary)
        }
    }
}

@Composable
private fun IconBadge(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(40.dp).clip(CircleShape).background(OrchSurface)
    ) {
        content()
    }
}

@Composable
private fun GitHubSection(chat: ChatUi, onConnect: (String) -> Unit, onDisconnect: () -> Unit) {
    val repo = chat.repo
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(repo) { if (repo != null) editing = false }
    val showForm = repo == null || editing

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge {
                Icon(painterResource(R.drawable.ic_github_logo), null, tint = OrchTextPrimary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("GitHub", style = MaterialTheme.typography.titleMedium, color = OrchTextPrimary)
                Text(
                    text = if (repo != null && !editing) "Repository connected" else "Connect a public repository",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrchTextSecondary
                )
            }
            if (repo != null && !editing && !chat.repoLocked) {
                TextButton(onClick = { editing = true }) { Text("Change", color = OrchAccentCoral) }
            }
        }

        if (!showForm && repo != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OrchSurface)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.Check, null, tint = OrchAdded, modifier = Modifier.size(18.dp))
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = OrchTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (!chat.repoLocked) {
                    TextButton(onClick = onDisconnect) { Text("Remove", color = OrchTextSecondary) }
                }
            }
        }

        if (showForm) {
            val connect = { if (input.isNotBlank()) onConnect(input) }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text("owner/repo", color = OrchTextSecondary) },
                isError = chat.repoError != null,
                supportingText = chat.repoError?.let { { Text(it, color = OrchAccentCoral) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { connect() }),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (repo != null) {
                    TextButton(onClick = { editing = false }) { Text("Cancel", color = OrchTextSecondary) }
                }
                Button(
                    onClick = connect,
                    enabled = input.isNotBlank() && !chat.connecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrchTextPrimary,
                        contentColor = OrchBackground,
                        disabledContainerColor = OrchBorder
                    )
                ) {
                    if (chat.connecting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OrchBackground)
                    } else {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
