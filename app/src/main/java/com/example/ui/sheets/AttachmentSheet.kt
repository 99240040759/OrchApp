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
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.ChatUi
import com.example.ui.ConnectorItemUi
import com.example.ui.ConnectorsUi
import com.example.ui.components.OrchBanner
import com.example.ui.components.OrchBottomSheet
import com.example.ui.components.OrchCard
import com.example.ui.components.OrchIconBadge
import com.example.ui.components.OrchPrimaryButton
import com.example.ui.components.OrchSecondaryButton
import com.example.ui.components.OrchTextField
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchAdded
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchSurfaceElevated
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary

@Composable
fun AttachmentSheet(
    chat: ChatUi,
    connectors: ConnectorsUi,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    onConnectRepo: (String) -> Unit,
    onEditRepo: () -> Unit,
    onDisconnectRepo: () -> Unit,
    onRefreshConnectors: () -> Unit,
    onToggleConnector: (String) -> Unit,
    onConnectConnector: (String) -> Unit,
    onDisconnectConnector: (id: String, accountId: String) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onRefreshConnectors() }

    OrchBottomSheet(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(OrchDimens.SectionGap),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OrchDimens.ScreenPadding)
                .padding(bottom = OrchDimens.ScreenPadding)
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

            HorizontalDivider(color = OrchBorder)

            GitHubSection(chat, onConnectRepo, onEditRepo, onDisconnectRepo)

            HorizontalDivider(color = OrchBorder)

            ConnectorsSection(
                connectors = connectors,
                onRefresh = onRefreshConnectors,
                onToggle = onToggleConnector,
                onConnect = onConnectConnector,
                onDisconnect = onDisconnectConnector
            )
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrchIconBadge {
                Icon(painterResource(R.drawable.ic_github_logo), null, tint = OrchTextPrimary, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("GitHub", style = MaterialTheme.typography.titleMedium, color = OrchTextPrimary)
                Text(
                    text = when {
                        repo != null && !editing -> "Repository connected"
                        locked -> "This chat has no repository"
                        else -> "Connect a public repository"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrchTextSecondary
                )
            }
            if (repo != null && !editing && !chat.repoLocked) {
                TextButton(onClick = { editing = true }) { Text("Change", color = OrchAccentCoral) }
            }
        }

        if (!showForm && repo != null) {
            OrchCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Check, null, tint = OrchAdded, modifier = Modifier.size(18.dp))
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = OrchTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!chat.repoLocked) {
                        TextButton(onClick = onDisconnect) { Text("Remove", color = OrchTextSecondary) }
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
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { connect() })
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (repo != null) {
                    TextButton(onClick = { editing = false }) { Text("Cancel", color = OrchTextSecondary) }
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

@Composable
private fun ConnectorsSection(
    connectors: ConnectorsUi,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (id: String, accountId: String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Connectors", style = MaterialTheme.typography.titleMedium, color = OrchTextPrimary)
                val activeCount = connectors.activeIds.size
                Text(
                    text = if (activeCount > 0) "$activeCount active for chat" else "Connect external apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrchTextSecondary
                )
            }
            if (connectors.loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OrchTextSecondary)
            } else {
                TextButton(onClick = onRefresh) { Text("Refresh", color = OrchTextSecondary) }
            }
        }

        connectors.error?.let { err -> OrchBanner(text = err, isError = true) }

        connectors.items.forEach { item ->
            ConnectorRow(
                item = item,
                onToggle = { onToggle(item.definition.id) },
                onConnect = { onConnect(item.definition.id) },
                onDisconnect = {
                    val accountId = item.accountId ?: return@ConnectorRow
                    onDisconnect(item.definition.id, accountId)
                }
            )
        }
    }
}

@Composable
private fun ConnectorRow(
    item: ConnectorItemUi,
    onToggle: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val def = item.definition
    OrchCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OrchIconBadge(size = 34.dp, containerColor = OrchSurfaceElevated) {
                if (def.id == "github") {
                    Icon(painterResource(R.drawable.ic_github_logo), null, tint = OrchTextPrimary, modifier = Modifier.size(17.dp))
                } else {
                    Icon(connectorIcon(def.id), null, tint = OrchTextPrimary, modifier = Modifier.size(17.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = def.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = OrchTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = def.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = OrchTextSecondary
                )
            }
            when {
                item.busy -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = OrchAccentCoral
                )
                item.connected -> Switch(
                    checked = item.active,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OrchBackground,
                        checkedTrackColor = OrchAdded,
                        uncheckedThumbColor = OrchTextSecondary,
                        uncheckedTrackColor = OrchSurfaceElevated,
                        uncheckedBorderColor = OrchBorder
                    )
                )
                else -> TextButton(onClick = onConnect) {
                    Text("Connect", color = OrchAccentCoral)
                }
            }
        }
        Text(
            text = def.description,
            style = MaterialTheme.typography.bodyMedium,
            color = OrchTextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (item.connected && !item.busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text(
                    text = if (item.active) "Enabled in chat" else "Connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.active) OrchAdded else OrchTextSecondary
                )
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect", color = OrchTextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun connectorIcon(id: String): ImageVector = when (id) {
    "googledrive" -> Icons.Outlined.CloudQueue
    "gmail" -> Icons.Outlined.Email
    "notion" -> Icons.Outlined.Description
    "slack" -> Icons.Outlined.Forum
    "jira" -> Icons.Outlined.TaskAlt
    else -> Icons.Outlined.Extension
}
