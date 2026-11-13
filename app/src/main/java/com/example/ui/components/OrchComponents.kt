package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchSurfaceElevated
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrchBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showDragHandle: Boolean = true,
    fullScreen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OrchBackground,
        contentColor = OrchTextPrimary,
        shape = if (fullScreen) RectangleShape else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = if (showDragHandle && !fullScreen) {
            {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(OrchBorder)
                    )
                }
            }
        } else {
            null
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (fullScreen) Modifier.fillMaxSize().statusBarsPadding()
                    else Modifier.fillMaxWidth()
                )
                .navigationBarsPadding()
                .imePadding(),
            content = content
        )
    }
}

@Composable
fun OrchSheetHeader(
    title: String,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = OrchDimens.ScreenPadding, end = 4.dp, top = 4.dp, bottom = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = OrchTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitleContent != null) {
                    subtitleContent()
                } else if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = OrchTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, "Close", tint = OrchTextSecondary)
            }
        }
        HorizontalDivider(color = OrchBorder)
    }
}

@Composable
fun OrchCard(
    modifier: Modifier = Modifier,
    color: Color = OrchSurface,
    border: BorderStroke? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = OrchDimens.CardPaddingH, vertical = OrchDimens.CardPaddingV),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = color,
        shape = shape,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun OrchIconBadge(
    size: Dp = 38.dp,
    containerColor: Color = OrchSurface,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
    ) {
        content()
    }
}

@Composable
fun OrchPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = OrchTextPrimary,
            contentColor = OrchBackground,
            disabledContainerColor = OrchBorder,
            disabledContentColor = OrchTextSecondary
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier.height(OrchDimens.ButtonHeight)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = OrchBackground
            )
        } else {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun OrchSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, OrchBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = OrchSurface,
            contentColor = OrchTextPrimary,
            disabledContentColor = OrchTextSecondary
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = modifier.height(OrchDimens.ButtonHeight)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = OrchTextPrimary)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OrchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OrchTextPrimary),
        placeholder = { Text(placeholder, color = OrchTextSecondary, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        supportingText = supportingText?.let { msg ->
            { Text(msg, color = OrchAccentCoral, style = MaterialTheme.typography.labelMedium) }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = OrchSurface,
            unfocusedContainerColor = OrchSurface,
            focusedBorderColor = OrchTextSecondary,
            unfocusedBorderColor = OrchBorder,
            errorBorderColor = OrchAccentCoral,
            cursorColor = OrchAccentCoral
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun OrchBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (isError) OrchAccentCoral.copy(alpha = 0.12f) else OrchSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (isError) {
            Icon(Icons.Rounded.WarningAmber, null, tint = OrchAccentCoral, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) OrchAccentCoral else OrchTextSecondary
        )
    }
}

@Composable
fun OrchCenteredSpinner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = OrchAccentCoral
        )
    }
}

@Composable
fun OrchEmptyMessage(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) OrchAccentCoral else OrchTextSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = OrchTextPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun OrchAlertDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmColor: Color = OrchAccentCoral,
    dismissLabel: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OrchSurface,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = OrchTextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) },
        confirmButton = {
            if (confirmLabel != null && onConfirm != null) {
                TextButton(onClick = onConfirm) {
                    Text(confirmLabel, color = confirmColor, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = OrchTextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun OrchActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    Surface(
        color = if (selected) OrchSurfaceElevated else OrchSurface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (selected) OrchAccentCoral else OrchBorder),
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (!enabled) OrchTextSecondary.copy(alpha = 0.5f) else if (selected) OrchTextPrimary else OrchTextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
