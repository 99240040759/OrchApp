package com.example.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SessionSummary
import com.example.ui.components.OrchAlertDialog
import com.example.ui.components.OrchEmptyMessage
import com.example.ui.components.OrchIconBadge
import com.example.ui.components.OrchSecondaryButton
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchSuccess

@Composable
fun SessionsDrawer(
    sessions: List<SessionSummary>,
    activeId: String,
    userName: String?,
    onConnectorsScreen: Boolean,
    activeConnectorCount: Int,
    onNewChat: () -> Unit,
    onConnectors: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAccount: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<SessionSummary?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrchDimens.ScreenPadding, vertical = OrchDimens.CardPaddingV)
        ) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            OrchSecondaryButton(
                text = "New chat",
                onClick = onNewChat,
                icon = Icons.Outlined.EditNote,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_new_chat_button")
            )
            Surface(
                onClick = onConnectors,
                shape = MaterialTheme.shapes.medium,
                color = if (onConnectorsScreen) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                border = if (onConnectorsScreen) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = OrchDimens.ButtonHeight)
                    .testTag("drawer_connectors_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Extension,
                        contentDescription = null,
                        tint = if (onConnectorsScreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Connectors",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (activeConnectorCount > 0) {
                        Surface(
                            color = OrchSuccess.copy(alpha = 0.16f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "$activeConnectorCount active",
                                style = MaterialTheme.typography.labelMedium,
                                color = OrchSuccess,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        if (sessions.isEmpty()) {
            OrchEmptyMessage(
                text = "No chats yet",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = OrchDimens.ItemGap, vertical = OrchDimens.ItemGap),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        selected = !onConnectorsScreen && session.id == activeId,
                        onClick = { onOpen(session.id) },
                        onDelete = { pendingDelete = session }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onAccount)
                .padding(horizontal = OrchDimens.ScreenPadding, vertical = OrchDimens.ItemGap)
                .testTag("drawer_account_row")
        ) {
            OrchIconBadge(
                size = OrchDimens.AvatarSmall,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = (userName ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = userName ?: "Account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    pendingDelete?.let { session ->
        OrchAlertDialog(
            title = "Delete chat?",
            onDismiss = { pendingDelete = null },
            confirmLabel = "Delete",
            onConfirm = {
                onDelete(session.id)
                pendingDelete = null
            }
        ) {
            Text(
                text = "This removes the conversation and its sandbox.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionItem(
    session: SessionSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OrchDimens.TouchTarget)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = session.title ?: "New chat",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (session.updatedAt > 0) {
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        session.updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (session.running) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete chat",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

