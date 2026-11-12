package com.example.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SessionSummary
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchSurfaceElevated
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary

@Composable
fun SessionsDrawer(
    sessions: List<SessionSummary>,
    activeId: String,
    userName: String?,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAccount: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<SessionSummary?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
        ) {
            Text("Chats", style = MaterialTheme.typography.titleLarge, color = OrchTextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewChat) {
                Icon(Icons.Outlined.EditNote, "New chat", tint = OrchTextPrimary)
            }
        }
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No chats yet", style = MaterialTheme.typography.bodyMedium, color = OrchTextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionItem(
                        session = session,
                        selected = session.id == activeId,
                        onClick = { onOpen(session.id) },
                        onDelete = { pendingDelete = session }
                    )
                }
            }
        }
        HorizontalDivider(color = OrchBorder)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAccount)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(OrchSurfaceElevated)
            ) {
                Text((userName ?: "?").take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = OrchTextPrimary)
            }
            Text(
                text = userName ?: "Account",
                style = MaterialTheme.typography.titleMedium,
                color = OrchTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = OrchSurface,
            title = { Text("Delete chat?", color = OrchTextPrimary) },
            text = { Text("This removes the conversation and its sandbox.", color = OrchTextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session.id)
                    pendingDelete = null
                }) { Text("Delete", color = OrchAccentCoral) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = OrchTextSecondary) }
            }
        )
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) OrchSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = session.title ?: "New chat",
                style = MaterialTheme.typography.titleMedium,
                color = OrchTextPrimary,
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
                    style = MaterialTheme.typography.labelSmall,
                    color = OrchTextSecondary
                )
            }
        }
        if (session.running) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = OrchTextSecondary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, "Delete chat", tint = OrchTextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
