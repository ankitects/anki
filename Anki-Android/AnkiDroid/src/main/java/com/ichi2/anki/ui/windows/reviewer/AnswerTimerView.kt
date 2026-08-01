/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.widget.Chronometer
import androidx.appcompat.widget.ThemeUtils
import androidx.core.view.isVisible
import com.ichi2.anki.R

class AnswerTimerView(
    context: Context,
    attributeSet: AttributeSet?,
) : Chronometer(context, attributeSet) {
    private var limitInMs: Int = Int.MAX_VALUE
    private var isRunning = false

    init {
        setOnChronometerTickListener {
            val elapsed = SystemClock.elapsedRealtime() - base
            if (elapsed >= limitInMs) {
                updateTextColor(true)
                stop()
            }
        }
    }

    override fun start() {
        super.start()
        isRunning = true
    }

    override fun stop() {
        super.stop()
        isRunning = false
    }

    fun setup(state: AnswerTimerState) {
        isVisible = state !is AnswerTimerState.Hidden

        when (state) {
            is AnswerTimerState.Hidden -> {
                stop()
            }
            is AnswerTimerState.Running -> {
                this.limitInMs = state.limitMs
                if (this.base != state.baseTime) {
                    this.base = state.baseTime
                }

                val elapsed = SystemClock.elapsedRealtime() - base
                if (elapsed >= limitInMs) {
                    // Already passed limit, render static and ensure stopped
                    updateTextColor(true)
                    if (isRunning) stop()
                } else {
                    // Under limit, ensure running
                    updateTextColor(false)
                    if (!isRunning) start()
                }
            }
            is AnswerTimerState.Stopped -> {
                stopAndUpdateTextColor(state.elapsedTimeMs, state.limitMs)
            }
            is AnswerTimerState.Paused -> {
                stopAndUpdateTextColor(state.elapsedTimeMs, state.limitMs)
            }
        }
    }

    private fun stopAndUpdateTextColor(
        elapsedTimeMs: Long,
        limitMs: Int,
    ) {
        this.limitInMs = limitMs
        this.base = SystemClock.elapsedRealtime() - elapsedTimeMs
        stop()
        updateTextColor(elapsedTimeMs >= limitMs)
    }

    private fun updateTextColor(isOverLimit: Boolean) {
        val colorAttr = if (isOverLimit) R.attr.maxTimerColor else android.R.attr.textColor
        setTextColor(ThemeUtils.getThemeAttrColor(context, colorAttr))
    }
}
