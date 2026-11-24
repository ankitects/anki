// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The last [capacity] amplitudes as levels from 0 to 1. [levelAt] reads the newest levels that fit,
 * oldest first, and returns NaN for a gap (a null amplitude) or an empty slot.
 *
 * @param capacity how many bars to keep. Each amplitude is the peak the recorder reports every 50 ms,
 * not a raw audio sample, so 300 is the last 15 seconds, about 1500dp of bars.
 */
class WaveformHistory(
    private val capacity: Int = 300,
) {
    /** A ring buffer of [size] levels, the oldest at index [oldest]. */
    private val levels = FloatArray(capacity)
    private var oldest by mutableIntStateOf(0)

    var size by mutableIntStateOf(0)
        private set

    fun add(amplitude: Int?) {
        val level = amplitude?.let { sqrt(it / MAX_AMPLITUDE).coerceIn(0f, 1f) } ?: Float.NaN
        if (size < capacity) {
            levels[(oldest + size) % capacity] = level
            size++
        } else {
            levels[oldest] = level
            oldest = (oldest + 1) % capacity
        }
    }

    fun clear() {
        oldest = 0
        size = 0
    }

    fun levelAt(
        slot: Int,
        visibleSlots: Int,
    ): Float {
        val shown = min(size, visibleSlots)
        if (slot >= shown) return Float.NaN
        return levels[(oldest + size - shown + slot) % capacity]
    }

    companion object {
        /**
         * The loudest value [android.media.MediaRecorder.getMaxAmplitude] returns (16-bit audio). [add] keeps the square
         * root of each amplitude's share of it, so quiet speech still shows.
         */
        const val MAX_AMPLITUDE = 32767f
    }
}
