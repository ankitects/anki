// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.widget.Chronometer
import androidx.appcompat.widget.ThemeUtils
import androidx.core.view.isVisible
import com.ichi2.anki.common.android.R as CommonR

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
        val colorAttr = if (isOverLimit) CommonR.attr.maxTimerColor else android.R.attr.textColor
        setTextColor(ThemeUtils.getThemeAttrColor(context, colorAttr))
    }
}
