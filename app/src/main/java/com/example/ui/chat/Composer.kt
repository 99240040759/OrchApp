package com.example.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.example.ui.PendingAttachment
import com.example.ui.theme.OrchAccentCoral
import com.example.ui.theme.OrchBackground
import com.example.ui.theme.OrchBorder
import com.example.ui.theme.OrchDimens
import com.example.ui.theme.OrchSurface
import com.example.ui.theme.OrchSurfaceElevated
import com.example.ui.theme.OrchTextPrimary
import com.example.ui.theme.OrchTextSecondary
import com.example.voice.VoicePromptRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Composer(
    placeholder: String,
    streaming: Boolean,
    canSend: Boolean,
    attachments: List<PendingAttachment>,
    modelLabel: String,
    contextRatio: Float,
    onSend: (String) -> Boolean,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onPickModel: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    transcribe: suspend (String, String) -> String?,
    modifier: Modifier = Modifier
) {
    val state = rememberTextFieldState()
    val hasText by remember { derivedStateOf { state.text.isNotBlank() } }
    val hasContent = hasText || attachments.isNotEmpty()
    val sendEnabled = canSend && hasContent
    var micActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OrchBackground)
            .padding(horizontal = OrchDimens.ScreenPadding, vertical = OrchDimens.ItemGap)
    ) {
        Surface(
            color = OrchSurface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, OrchBorder)
        ) {
            Column(
                modifier = Modifier.padding(
                    start = OrchDimens.CardPaddingH,
                    end = 8.dp,
                    top = OrchDimens.CardPaddingV,
                    bottom = 8.dp
                )
            ) {
                if (attachments.isNotEmpty()) {
                    AttachmentStrip(attachments, onRemoveAttachment)
                }
                BasicTextField(
                    state = state,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = OrchTextPrimary),
                    cursorBrush = SolidColor(OrchAccentCoral),
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 6),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorator = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (state.text.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OrchTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("composer_input")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    IconButton(
                        onClick = onAttach,
                        modifier = Modifier.size(32.dp).testTag("composer_attach_button")
                    ) {
                        Icon(Icons.Outlined.Add, "Attach", tint = OrchTextSecondary, modifier = Modifier.size(20.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClick = onPickModel)
                                .padding(horizontal = 6.dp, vertical = 5.dp)
                                .testTag("composer_model_button")
                        ) {
                            Text(
                                text = modelLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = OrchTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Select model",
                                tint = OrchTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (contextRatio > 0f) {
                        CircularProgressIndicator(
                            progress = { contextRatio },
                            modifier = Modifier.padding(end = 4.dp).size(15.dp),
                            strokeWidth = 2.dp,
                            color = if (contextRatio > 0.85f) OrchAccentCoral else OrchTextSecondary,
                            trackColor = OrchBorder
                        )
                    }
                    if (!streaming && (micActive || !hasContent)) {
                        MicButton(
                            transcribe = transcribe,
                            onBusyChange = { micActive = it },
                            onText = { text ->
                                val current = state.text
                                val separator = if (current.isNotEmpty() && !current.last().isWhitespace()) " " else ""
                                state.edit { append(separator + text) }
                            }
                        )
                    } else {
                        SendButton(
                            streaming = streaming,
                            enabled = sendEnabled,
                            onClick = {
                                if (streaming) {
                                    onStop()
                                } else {
                                    if (onSend(state.text.toString())) state.clearText()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentStrip(attachments: List<PendingAttachment>, onRemove: (Uri) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(OrchDimens.ItemGap),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp)
    ) {
        attachments.forEach { attachment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(OrchSurfaceElevated)
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
            ) {
                if (attachment.isImage) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(MaterialTheme.shapes.extraSmall)
                    )
                }
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = OrchTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp).widthIn(max = 160.dp)
                )
                IconButton(onClick = { onRemove(attachment.uri) }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Rounded.Close, "Remove ${attachment.name}", tint = OrchTextSecondary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SendButton(streaming: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val active = streaming || enabled
    IconButton(
        onClick = onClick,
        enabled = active,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (active) OrchTextPrimary else OrchSurfaceElevated)
            .testTag("composer_send_button")
    ) {
        Icon(
            imageVector = if (streaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
            contentDescription = if (streaming) "Stop" else "Send",
            tint = if (active) OrchBackground else OrchTextSecondary,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun MicButton(
    transcribe: suspend (String, String) -> String?,
    onBusyChange: (Boolean) -> Unit,
    onText: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember(context) { VoicePromptRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    val latestTranscribe by rememberUpdatedState(transcribe)
    val latestOnText by rememberUpdatedState(onText)

    LaunchedEffect(recording, transcribing) {
        onBusyChange(recording || transcribing)
    }

    DisposableEffect(recorder) {
        onDispose {
            recorder.cancel()
            onBusyChange(false)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (recording) {
            recorder.cancel()
            recording = false
        }
    }

    LaunchedEffect(recording) {
        elapsedSeconds = 0
        while (recording) {
            delay(1000)
            elapsedSeconds++
        }
    }

    fun finish() {
        if (!recording) return
        recording = false
        scope.launch {
            transcribing = true
            try {
                recorder.stop()
                    .onSuccess { (audio, format) -> latestTranscribe(audio, format)?.let(latestOnText) }
                    .onFailure { Toast.makeText(context, it.message ?: "Recording failed", Toast.LENGTH_SHORT).show() }
            } finally {
                transcribing = false
            }
        }
    }

    fun begin() {
        recorder.start(onLimit = ::finish)
            .onSuccess { recording = true }
            .onFailure { Toast.makeText(context, it.message ?: "Microphone unavailable", Toast.LENGTH_SHORT).show() }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin()
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (recording) {
            Text(
                text = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                style = MaterialTheme.typography.labelMedium,
                color = OrchAccentCoral
            )
        }
        IconButton(
            enabled = !transcribing,
            onClick = {
                when {
                    recording -> finish()
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> begin()
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (recording) OrchAccentCoral.copy(alpha = 0.16f) else OrchSurfaceElevated)
                .testTag("composer_mic_button")
        ) {
            if (transcribing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OrchTextSecondary)
            } else {
                Icon(
                    imageVector = if (recording) Icons.Rounded.Stop else Icons.Outlined.Mic,
                    contentDescription = if (recording) "Stop recording" else "Dictate",
                    tint = if (recording) OrchAccentCoral else OrchTextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
