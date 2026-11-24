// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.recorder.ui.RecordButton
import com.ichi2.anki.recorder.ui.RecorderBar
import com.ichi2.anki.recorder.ui.RecorderCard
import com.ichi2.anki.recorder.ui.RecorderIconButton
import com.ichi2.anki.recorder.ui.RecorderStatus
import com.ichi2.anki.recorder.ui.RecorderUiState
import com.ichi2.anki.recorder.ui.WaveformHistory
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The note editor's recorder for [fieldName]: a header with a close button, then a [RecorderBar] with the controls for [state]. */
@Composable
internal fun InlineAudioRecorderCard(
    state: RecorderUiState,
    waveform: WaveformHistory,
    fieldName: String,
    onToggleRecording: () -> Boolean,
    onHoldRelease: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onInsert: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
) {
    RecorderCard(modifier = modifier, contentPadding = contentPadding, animated = animated) {
        Row(
            modifier = Modifier.heightIn(min = 40.dp).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_action_mic),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = fieldName,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RecorderIconButton(R.drawable.close_icon, CommonString.close, onClose)
        }
        RecorderBar(state = state, waveform = waveform, animated = animated) {
            if (state.status == RecorderStatus.RECORDED) {
                RecorderIconButton(R.drawable.ic_delete, CommonString.delete_audio_button_description, onDiscard)
                FilledIconButton(onClick = onInsert) {
                    Icon(
                        painter = painterResource(R.drawable.ic_done),
                        contentDescription = stringResource(CommonString.insert_recording),
                    )
                }
            } else {
                if (state.status != RecorderStatus.READY) {
                    RecorderIconButton(R.drawable.ic_stop, CommonString.save_voice, onSave)
                }
                RecordButton(status = state.status, onToggle = onToggleRecording, onHoldRelease = onHoldRelease)
            }
        }
    }
}

/** [InlineAudioRecorderCard] in every [RecorderStatus], for the previews and screenshot test. */
@Composable
internal fun InlineAudioRecorderStates(
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val empty = remember { WaveformHistory() }
    val waveform = remember { WaveformHistory().apply { previewLevels.forEach { add((it * it * WaveformHistory.MAX_AMPLITUDE).toInt()) } } }
    Column(modifier.background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp)) {
        for (status in RecorderStatus.entries) {
            val isReady = status == RecorderStatus.READY
            InlineAudioRecorderCard(
                state = RecorderUiState(status, elapsedSeconds = if (isReady) 0 else PREVIEW_ELAPSED_SECONDS),
                waveform = if (isReady) empty else waveform,
                fieldName = "Front",
                onToggleRecording = { false },
                onHoldRelease = {},
                onSave = {},
                onDiscard = {},
                onInsert = {},
                onClose = {},
                animated = animated,
            )
        }
    }
}

private const val PREVIEW_ELAPSED_SECONDS = 7L
private val previewLevels = List(44) { abs(sin(it * 0.9f) * cos(it * 0.31f)) }

@Preview
@Composable
private fun InlineAudioRecorderPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) {
    AnkiDroidPreview(theme = theme) {
        InlineAudioRecorderStates(animated = theme != DayTheme.EINK)
    }
}

@Preview(name = "Large text", widthDp = 320, fontScale = 2f)
@Composable
private fun InlineAudioRecorderLargeTextPreview() {
    AnkiDroidPreview {
        InlineAudioRecorderStates(animated = true)
    }
}
