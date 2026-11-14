package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
import com.example.ui.components.OrchSheetHeader
import com.example.ui.components.OrchTextField
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchMonoLabel

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

    val currentLocation = if (files.currentPath.isEmpty()) repoName else "$repoName/${files.currentPath}"

    OrchBottomSheet(onDismiss = onDismiss) {
        OrchSheetHeader(
            title = "Files",
            subtitle = currentLocation,
            onClose = onDismiss
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrchDimens.ScreenPadding)
                .padding(top = OrchDimens.ItemGap, bottom = OrchDimens.ScreenPadding)
        ) {
            OrchTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search files",
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
            if (query.isBlank() && (files.currentPath.isNotEmpty() || files.treeTruncated)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = OrchDimens.TouchTarget)
                        .padding(top = OrchDimens.TightGap)
                ) {
                    if (files.currentPath.isNotEmpty()) {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Parent folder",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(OrchDimens.TightGap))
                    }
                    Text(
                        text = currentLocation,
                        style = OrchMonoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (files.treeTruncated) {
                        Text(
                            text = "Truncated",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 180.dp, max = 420.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = OrchDimens.TouchTarget)
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.path,
                                        style = OrchMonoLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = OrchDimens.TouchTarget)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        if (entry.isDir) {
                                            onBrowse(entry.path, "")
                                        } else {
                                            onDismiss()
                                            onOpen(entry.path)
                                        }
                                    }
                                    .padding(horizontal = OrchDimens.ItemGap, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (entry.isDir) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (entry.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (entry.isDir) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
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
        OrchSheetHeader(
            title = "Select model",
            onClose = onDismiss
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = OrchDimens.ScreenPadding)
        ) {
            if (models.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    OrchEmptyMessage(text = "No models available")
                }
            } else {
                models.forEach { model ->
                    val selected = model.id == selectedId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable {
                                onSelectModel(model.id)
                                if (!model.supportsThinking) onDismiss()
                            }
                            .padding(horizontal = OrchDimens.ScreenPadding, vertical = OrchDimens.CardPaddingV)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                model.badge?.takeIf { it.isNotBlank() }?.let { badge ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        shape = MaterialTheme.shapes.extraSmall,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (model.contextWindow > 0) {
                                Text(
                                    text = "${formatContext(model.contextWindow)} context",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            if (selectedModel?.supportsThinking == true) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = OrchDimens.ItemGap)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
                    modifier = Modifier.padding(horizontal = OrchDimens.ScreenPadding, vertical = OrchDimens.ItemGap)
                ) {
                    Text(
                        text = "Thinking effort",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
        OrchSheetHeader(
            title = "Account",
            onClose = onDismiss
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(OrchDimens.SectionGap),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(OrchDimens.ScreenPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrchDimens.CardPaddingH)
            ) {
                OrchIconBadge(size = OrchDimens.AvatarLarge) {
                    Text(
                        text = (name ?: email ?: "?").take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = name ?: "Signed in",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    email?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (budget != null) {
                val ratio = if (budget.limitUsd > 0) (budget.costUsd / budget.limitUsd).toFloat().coerceIn(0f, 1f) else 0f
                OrchCard {
                    Column(verticalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Monthly usage",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$%.2f / $%.2f".format(budget.costUsd, budget.limitUsd),
                                style = OrchMonoLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = if (ratio > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            trackColor = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (budget.resetsOn.isNotBlank()) {
                            Text(
                                text = "Resets ${budget.resetsOn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

