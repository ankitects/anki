// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.compose.theme.widgetBorder

/** The rounded frame a recorder sits in. With [animated] false, for E-ink, ripples are off. */
@Composable
internal fun RecorderCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    animated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.padding(contentPadding).padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = MaterialTheme.widgetBorder,
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            LocalRippleConfiguration provides if (animated) LocalRippleConfiguration.current else null,
        ) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), content = content)
        }
    }
}

/** The status dot, elapsed time and [waveform] for [state], followed by [actions]. */
@Composable
internal fun RecorderBar(
    state: RecorderUiState,
    waveform: WaveformHistory,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    actions: @Composable RowScope.() -> Unit,
) {
    val isRecording = state.status == RecorderStatus.RECORDING
    val recordingRed = colorResource(R.color.audio_recorder_red)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = MaterialTheme.widgetBorder,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 52.dp).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(isRecording = isRecording, recordingColor = recordingRed, pulse = animated)
            Text(
                text = DateUtils.formatElapsedTime(state.elapsedSeconds),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Medium,
            )
            RecordingWaveform(
                history = waveform,
                color = if (isRecording) recordingRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 12.dp),
            )
            actions()
        }
    }
}

/** An [IconButton] tinted like the recorder's other icons, with [description] for TalkBack. */
@Composable
internal fun RecorderIconButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Icon(painterResource(icon), contentDescription = stringResource(description))
    }
}

/** Red while recording, pulsing if [pulse], and grey otherwise. */
@Composable
private fun StatusDot(
    isRecording: Boolean,
    recordingColor: Color,
    pulse: Boolean,
) {
    val alpha =
        if (isRecording && pulse) {
            rememberInfiniteTransition(label = "recording").animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
                label = "recording dot",
            )
        } else {
            null
        }
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha?.value ?: 1f }
            .background(
                if (isRecording) recordingColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                CircleShape,
            ),
    )
}
