package com.example.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.data.ThinkingEffort
import com.example.ui.FilesUi
import com.example.ui.components.OrchActionChip
import com.example.ui.components.OrchBottomSheet
import com.example.ui.components.OrchCard
import com.example.ui.components.OrchCenteredSpinner
import com.example.ui.components.OrchEmptyMessage
import com.example.ui.components.OrchIconBadge
import com.example.ui.components.OrchSecondaryButton
import com.example.ui.components.OrchTextField
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary

@Composable
fun FilesSheet(
    repoName: String,
    files: FilesUi,
    onBrowse: (path: String, query: String) -> Unit,
    onNavigateUp: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { onBrowse(files.currentPath, query) }

    OrchBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = OrchDimens.ScreenPadding)
                .padding(bottom = OrchDimens.ScreenPadding)
        ) {
            OrchTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search files",
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = OrchTextSecondary) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, "Clear search", tint = OrchTextSecondary)
                        }
                    }
                }
            )
            if (query.isBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = OrchDimens.ItemGap, bottom = OrchDimens.TightGap)
                ) {
                    if (files.currentPath.isNotEmpty()) {
                        IconButton(onClick = onNavigateUp, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Parent folder",
                                tint = OrchTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(OrchDimens.TightGap))
                    }
                    Text(
                        text = if (files.currentPath.isEmpty()) repoName else "$repoName/${files.currentPath}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = OrchTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (files.treeTruncated) {
                        Text(
                            text = "Truncated",
                            style = MaterialTheme.typography.labelSmall,
                            color = OrchAccentCoral
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 500.dp)
                    .padding(top = OrchDimens.TightGap)
            ) {
                val isEmpty = if (query.isNotBlank()) files.searchEntries.isEmpty() else files.treeEntries.isEmpty()
                when {
                    files.loading -> OrchCenteredSpinner()
                    files.error != null -> OrchEmptyMessage(text = files.error, isError = true)
                    isEmpty -> OrchEmptyMessage(text = if (query.isNotBlank()) "No files found" else "Empty folder")
                    query.isNotBlank() -> LazyColumn {
                        items(files.searchEntries, key = { it.path }) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        onDismiss()
                                        onOpen(entry.path)
                                    }
                                    .padding(horizontal = OrchDimens.ItemGap, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = OrchTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
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
                    }
                    else -> LazyColumn {
                        items(files.treeEntries, key = { "${it.isDir}:${it.path}" }) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        if (entry.isDir) {
                                            onBrowse(entry.path, "")
                                        } else {
                                            onDismiss()
                                            onOpen(entry.path)
                                        }
                                    }
                                    .padding(horizontal = OrchDimens.ItemGap, vertical = 11.dp)
                            ) {
                                Icon(
                                    imageVector = if (entry.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (entry.isDir) OrchAccentCoral else OrchTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OrchTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (entry.isDir) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = OrchTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelSheet(
    models: List<ModelInfo>,
    selectedId: String,
    thinkingEffort: ThinkingEffort,
    onSelectModel: (String) -> Unit,
    onSelectThinkingEffort: (ThinkingEffort) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedModel = models.firstOrNull { it.id == selectedId }
    OrchBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = OrchDimens.ScreenPadding)
        ) {
            models.forEach { model ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectModel(model.id)
                            if (!model.supportsThinking) onDismiss()
                        }
                        .padding(horizontal = OrchDimens.ScreenPadding, vertical = OrchDimens.CardPaddingV)
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
            if (selectedModel?.supportsThinking != false) {
                HorizontalDivider(color = OrchBorder, modifier = Modifier.padding(vertical = OrchDimens.ItemGap))
                Column(
                    verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
                    modifier = Modifier.padding(horizontal = OrchDimens.ScreenPadding, vertical = OrchDimens.ItemGap)
                ) {
                    Text(
                        text = "Thinking effort",
                        style = MaterialTheme.typography.titleMedium,
                        color = OrchTextPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThinkingEffort.entries.forEach { effort ->
                            OrchActionChip(
                                label = effort.label,
                                selected = effort == thinkingEffort,
                                onClick = { onSelectThinkingEffort(effort) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountSheet(
    name: String?,
    email: String?,
    budget: Budget?,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    OrchBottomSheet(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(OrchDimens.SectionGap),
            modifier = Modifier
                .padding(horizontal = OrchDimens.ScreenPadding)
                .padding(bottom = OrchDimens.ScreenPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrchDimens.CardPaddingH)
            ) {
                OrchIconBadge(size = 44.dp) {
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
                OrchCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Monthly usage",
                                style = MaterialTheme.typography.titleMedium,
                                color = OrchTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$%.2f / $%.2f".format(budget.costUsd, budget.limitUsd),
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = OrchTextSecondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = if (ratio > 0.9f) OrchAccentCoral else OrchTextPrimary,
                            trackColor = OrchBackground,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (budget.resetsOn.isNotBlank()) {
                            Text(
                                text = "Resets ${budget.resetsOn}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OrchTextSecondary
                            )
                        }
                    }
                }
            }
            OrchSecondaryButton(
                text = "Sign out",
                onClick = onSignOut,
                icon = Icons.AutoMirrored.Rounded.Logout,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatContext(tokens: Long): String = when {
    tokens >= 1_000_000 && tokens % 1_000_000 == 0L -> "${tokens / 1_000_000}M"
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    else -> "${tokens / 1000}K"
}
