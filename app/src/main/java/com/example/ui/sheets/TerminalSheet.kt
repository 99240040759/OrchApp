package com.example.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.terminal.TerminalStatus
import com.example.terminal.TerminalUi
import com.example.ui.components.OrchActionChip
import com.example.ui.components.OrchBanner
import com.example.ui.components.OrchBottomSheet
import com.example.ui.components.OrchSheetHeader
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchAdded
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary

private data class QuickKey(val label: String, val sequence: String)

private val QUICK_KEYS = listOf(
    QuickKey("Ctrl+C", "\u0003"),
    QuickKey("Ctrl+D", "\u0004"),
    QuickKey("Ctrl+L", "\u000C"),
    QuickKey("Tab", "\t"),
    QuickKey("Esc", "\u001B"),
    QuickKey("↑", "\u001B[A"),
    QuickKey("↓", "\u001B[B"),
    QuickKey("Enter", "\r")
)

@Composable
fun TerminalSheet(
    repoName: String,
    workspace: String,
    terminal: TerminalUi,
    onConnect: (workspace: String, cols: Int, rows: Int) -> Unit,
    onInput: (String) -> Unit,
    onResize: (cols: Int, rows: Int) -> Unit,
    onClear: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var command by rememberSaveable { mutableStateOf("") }
    var measuredCols by remember { mutableIntStateOf(80) }
    var measuredRows by remember { mutableIntStateOf(24) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val termStyle = remember {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            color = OrchTextPrimary
        )
    }

    DisposableEffect(workspace) {
        onDispose { onDisconnect() }
    }

    LaunchedEffect(terminal.output) {
        verticalScroll.scrollTo(verticalScroll.maxValue)
    }

    OrchBottomSheet(
        onDismiss = onDismiss,
        fullScreen = true
    ) {
        val dotColor = when (terminal.status) {
            TerminalStatus.Connected -> OrchAdded
            TerminalStatus.Connecting -> OrchAccentCoral
            TerminalStatus.Exited, TerminalStatus.Disconnected -> OrchTextSecondary
        }
        val statusLabel = when (terminal.status) {
            TerminalStatus.Connected -> "Connected"
            TerminalStatus.Connecting -> "Connecting"
            TerminalStatus.Exited -> "Exited"
            TerminalStatus.Disconnected -> "Disconnected"
        }

        OrchSheetHeader(
            title = "Terminal · $repoName",
            subtitleContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = OrchTextSecondary)
                }
            },
            onClose = onDismiss,
            actions = {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.DeleteSweep, "Clear output", tint = OrchTextSecondary)
                }
                if (terminal.status != TerminalStatus.Connected && terminal.status != TerminalStatus.Connecting) {
                    IconButton(onClick = { onConnect(workspace, measuredCols, measuredRows) }) {
                        Icon(Icons.Rounded.Refresh, "Reconnect", tint = OrchTextPrimary)
                    }
                }
            }
        )

        terminal.error?.let { err ->
            OrchBanner(
                text = err,
                isError = true,
                modifier = Modifier.padding(horizontal = OrchDimens.ScreenPadding, vertical = 6.dp)
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(OrchSurface)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val cell = remember(termStyle, density) { measurer.measure("M", termStyle).size }
            val cols = remember(maxWidth, cell, density) {
                val widthPx = with(density) { maxWidth.toPx() }
                (widthPx / cell.width.coerceAtLeast(1)).toInt().coerceIn(20, 240)
            }
            val rows = remember(maxHeight, cell, density) {
                val heightPx = with(density) { maxHeight.toPx() }
                (heightPx / cell.height.coerceAtLeast(1)).toInt().coerceIn(8, 100)
            }

            LaunchedEffect(cols, rows) {
                measuredCols = cols
                measuredRows = rows
            }
            LaunchedEffect(workspace) {
                onConnect(workspace, cols, rows)
            }
            LaunchedEffect(cols, rows, terminal.status) {
                if (terminal.status == TerminalStatus.Connected) {
                    onResize(cols, rows)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .horizontalScroll(horizontalScroll)
            ) {
                SelectionContainer {
                    Text(
                        text = terminal.output.ifEmpty {
                            when (terminal.status) {
                                TerminalStatus.Connecting -> "Connecting to cloud sandbox..."
                                TerminalStatus.Connected -> "Shell ready."
                                TerminalStatus.Exited -> "Session exited. Tap refresh to reconnect."
                                TerminalStatus.Disconnected -> "Disconnected."
                            }
                        },
                        style = termStyle,
                        color = if (terminal.output.isEmpty()) OrchTextSecondary else OrchTextPrimary,
                        softWrap = false
                    )
                }
            }
        }

        HorizontalDivider(color = OrchBorder)

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            QUICK_KEYS.forEach { key ->
                OrchActionChip(
                    label = key.label,
                    enabled = terminal.status == TerminalStatus.Connected,
                    onClick = { onInput(key.sequence) }
                )
            }
        }

        Surface(
            color = OrchSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, OrchBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (command.isEmpty()) {
                        Text(
                            text = "Run command in sandbox...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = OrchTextSecondary
                        )
                    }
                    BasicTextField(
                        value = command,
                        onValueChange = { command = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = OrchTextPrimary
                        ),
                        cursorBrush = SolidColor(OrchAccentCoral),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (terminal.status == TerminalStatus.Connected) {
                                    onInput(command + "\r")
                                    command = ""
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(
                    enabled = terminal.status == TerminalStatus.Connected,
                    onClick = {
                        onInput(command + "\r")
                        command = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardReturn,
                        contentDescription = "Run command",
                        tint = if (terminal.status == TerminalStatus.Connected) OrchAccentCoral else OrchTextSecondary
                    )
                }
            }
        }
    }
}
