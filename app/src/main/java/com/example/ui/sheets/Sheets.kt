package com.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Budget
import com.example.data.ModelInfo
import com.example.ui.FilesUi
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesSheet(
    files: FilesUi,
    onQuery: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(query) { onQuery(query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search files", color = OrchTextSecondary) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = OrchTextSecondary) },
                modifier = Modifier.fillMaxWidth()
            )
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 520.dp).padding(top = 8.dp)) {
                LazyColumn {
                    items(files.entries, key = { it.path }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onDismiss()
                                    onOpen(entry.path)
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(entry.name, style = MaterialTheme.typography.titleMedium, color = OrchTextPrimary)
                            Text(
                                text = entry.path,
                                style = MaterialTheme.typography.labelSmall,
                                color = OrchTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                when {
                    files.loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        strokeWidth = 2.dp,
                        color = OrchTextSecondary
                    )
                    files.error != null -> Text(
                        text = files.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrchAccentCoral,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp)
                    )
                    files.entries.isEmpty() -> Text(
                        text = "No files found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrchTextSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    models: List<ModelInfo>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            models.forEach { model ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(model.id)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = listOfNotNull(model.name, model.badge).joinToString(" · "),
                            style = MaterialTheme.typography.titleMedium,
                            color = OrchTextPrimary
                        )
                        if (model.contextWindow > 0) {
                            Text(
                                text = "${formatContext(model.contextWindow)} context",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OrchTextSecondary
                            )
                        }
                    }
                    if (model.id == selectedId) {
                        Icon(Icons.Rounded.Check, "Selected", tint = OrchAccentCoral)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    name: String?,
    email: String?,
    budget: Budget?,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(OrchSurface)
                ) {
                    Text(
                        text = (name ?: email ?: "?").take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = OrchTextPrimary
                    )
                }
                Column {
                    Text(name ?: "Signed in", style = MaterialTheme.typography.titleMedium, color = OrchTextPrimary)
                    email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = OrchTextSecondary) }
                }
            }
            HorizontalDivider(color = OrchBorder)
            if (budget != null) {
                val ratio = if (budget.limitUsd > 0) (budget.costUsd / budget.limitUsd).toFloat().coerceIn(0f, 1f) else 0f
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Monthly usage", style = MaterialTheme.typography.titleMedium, color = OrchTextPrimary, modifier = Modifier.weight(1f))
                        Text(
                            text = "$%.2f / $%.2f".format(budget.costUsd, budget.limitUsd),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = OrchTextSecondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = if (ratio > 0.9f) OrchAccentCoral else OrchTextPrimary,
                        trackColor = OrchSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (budget.resetsOn.isNotBlank()) {
                        Text("Resets ${budget.resetsOn}", style = MaterialTheme.typography.bodyMedium, color = OrchTextSecondary)
                    }
                }
            }
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, modifier = Modifier.size(18.dp), tint = OrchTextPrimary)
                Text("  Sign out", color = OrchTextPrimary)
            }
        }
    }
}

private fun formatContext(tokens: Long): String = when {
    tokens >= 1_000_000 && tokens % 1_000_000 == 0L -> "${tokens / 1_000_000}M"
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    else -> "${tokens / 1000}K"
}
