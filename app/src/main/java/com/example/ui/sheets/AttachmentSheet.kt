package com.example.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.ChatUi
import com.example.ui.components.OrchBottomSheet
import com.example.ui.components.OrchCard
import com.example.ui.components.OrchIconBadge
import com.example.ui.components.OrchPrimaryButton
import com.example.ui.components.OrchSecondaryButton
import com.example.ui.components.OrchSheetHeader
import com.example.ui.components.OrchTextField
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchMonoBody
import com.example.ui.theme.OrchSuccess

@Composable
fun AttachmentSheet(
    chat: ChatUi,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onConnectRepo: (String) -> Unit,
    onEditRepo: () -> Unit,
    onDisconnectRepo: () -> Unit,
    onDismiss: () -> Unit
) {
    OrchBottomSheet(onDismiss = onDismiss) {
        OrchSheetHeader(
            title = "Add to chat",
            onClose = onDismiss
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(OrchDimens.SectionGap),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(OrchDimens.ScreenPadding)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
                modifier = Modifier.fillMaxWidth()
            ) {
                OrchSecondaryButton(
                    text = "Photos",
                    onClick = onPickImages,
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.weight(1f)
                )
                OrchSecondaryButton(
                    text = "Files",
                    onClick = onPickFiles,
                    icon = Icons.Outlined.AttachFile,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            GitHubSection(chat, onConnectRepo, onEditRepo, onDisconnectRepo)
        }
    }
}

@Composable
private fun GitHubSection(
    chat: ChatUi,
    onConnect: (String) -> Unit,
    onEdit: () -> Unit,
    onDisconnect: () -> Unit
) {
    val repo = chat.repo
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(repo) { if (repo != null) editing = false }
    val locked = chat.repoLocked
    val showForm = !locked && (repo == null || editing)

    Column(verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OrchIconBadge {
                Icon(
                    painter = painterResource(R.drawable.ic_github_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GitHub repository",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        repo != null && !editing -> "Repository connected"
                        locked -> "This chat has no repository"
                        else -> "Connect a public repository"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (repo != null && !editing && !chat.repoLocked) {
                TextButton(onClick = { editing = true }) {
                    Text("Change", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (!showForm && repo != null) {
            OrchCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = OrchSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = repo.name,
                        style = OrchMonoBody,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!chat.repoLocked) {
                        TextButton(onClick = onDisconnect) {
                            Text("Remove", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (showForm) {
            val connect = { if (input.isNotBlank()) onConnect(input) }
            OrchTextField(
                value = input,
                onValueChange = {
                    input = it
                    onEdit()
                },
                placeholder = "owner/repo",
                isError = chat.repoError != null,
                supportingText = chat.repoError,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { connect() })
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (repo != null) {
                    TextButton(onClick = { editing = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OrchPrimaryButton(
                    text = "Connect",
                    onClick = connect,
                    enabled = input.isNotBlank(),
                    loading = chat.connecting
                )
            }
        }
    }
}

