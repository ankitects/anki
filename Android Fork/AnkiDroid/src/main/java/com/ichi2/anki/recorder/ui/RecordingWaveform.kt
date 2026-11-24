// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Draws the newest levels of [history] as bars, newest at the end. Gaps and empty slots are dots, and old bars fade once it overflows. */
@Composable
internal fun RecordingWaveform(
    history: WaveformHistory,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val barWidth = 3.dp.toPx()
        val step = barWidth + 2.dp.toPx()
        val slots = max(1, ((size.width + step - barWidth) / step).toInt())
        val fadeSlots = if (history.size > slots) slots * 0.35f else 0f
        val centerY = size.height / 2
        val usableHeight = size.height - barWidth
        for (slot in 0 until slots) {
            val fromStart = barWidth / 2 + slot * step
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - fromStart else fromStart
            val fade = if (slot < fadeSlots) 0.2f + 0.8f * slot / fadeSlots else 1f
            val level = history.levelAt(slot, slots)
            if (level.isNaN()) {
                drawCircle(color.copy(alpha = 0.38f * fade), radius = barWidth / 2, center = Offset(x, centerY))
                continue
            }
            val halfHeight = level * usableHeight / 2
            drawLine(
                color = color.copy(alpha = color.alpha * fade),
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Preview(widthDp = 240)
@Composable
private fun RecordingWaveformPreview() {
    fun amplitude(i: Int) = (abs(sin(i * 0.9f)) * WaveformHistory.MAX_AMPLITUDE).toInt()
    val partial = remember { WaveformHistory().apply { repeat(24) { add(if (it in 10..12) null else amplitude(it)) } } }
    val overflowing = remember { WaveformHistory().apply { repeat(100) { add(amplitude(it)) } } }
    AnkiDroidPreview {
        Column {
            RecordingWaveform(partial, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.fillMaxWidth().height(28.dp))
            RecordingWaveform(overflowing, colorResource(R.color.audio_recorder_red), Modifier.fillMaxWidth().height(28.dp))
        }
    }
}
