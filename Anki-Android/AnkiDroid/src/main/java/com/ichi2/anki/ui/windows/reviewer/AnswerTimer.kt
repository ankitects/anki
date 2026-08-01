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

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AnswerTimer : DefaultLifecycleObserver {
    val state: StateFlow<AnswerTimerState>
        field = MutableStateFlow<AnswerTimerState>(AnswerTimerState.Hidden)

    fun configureForCard(
        shouldShow: Boolean,
        limitMs: Int,
    ) {
        if (!shouldShow) {
            state.value = AnswerTimerState.Hidden
        } else {
            state.value =
                AnswerTimerState.Running(
                    baseTime = SystemClock.elapsedRealtime(),
                    limitMs = limitMs,
                )
        }
    }

    /** Permanently stops the timer. */
    fun stop() {
        when (val currentState = state.value) {
            is AnswerTimerState.Running -> {
                val elapsed = (SystemClock.elapsedRealtime() - currentState.baseTime).coerceAtMost(currentState.limitMs.toLong())

                state.value =
                    AnswerTimerState.Stopped(
                        elapsedTimeMs = elapsed,
                        limitMs = currentState.limitMs,
                    )
            }
            is AnswerTimerState.Paused -> {
                state.value =
                    AnswerTimerState.Stopped(
                        elapsedTimeMs = currentState.elapsedTimeMs,
                        limitMs = currentState.limitMs,
                    )
            }
            AnswerTimerState.Hidden, is AnswerTimerState.Stopped -> return
        }
    }

    /** Temporarily pauses the timer. */
    override fun onPause(owner: LifecycleOwner) {
        val currentState = state.value
        if (currentState is AnswerTimerState.Running) {
            val rawElapsed = SystemClock.elapsedRealtime() - currentState.baseTime
            // If the timer has a limit and we've passed it, clamp the elapsed time
            // to the limit. This matches the UI behavior where the timer visually stops.
            val effectiveElapsed = rawElapsed.coerceAtMost(currentState.limitMs.toLong())

            state.value =
                AnswerTimerState.Paused(
                    elapsedTimeMs = effectiveElapsed,
                    limitMs = currentState.limitMs,
                )
        }
    }

    /** Resumes the timer if it was paused and the limit hasn't been exceeded. */
    override fun onResume(owner: LifecycleOwner) {
        val currentState = state.value
        if (currentState is AnswerTimerState.Paused) {
            if (currentState.elapsedTimeMs < currentState.limitMs) {
                state.value =
                    AnswerTimerState.Running(
                        baseTime = SystemClock.elapsedRealtime() - currentState.elapsedTimeMs,
                        limitMs = currentState.limitMs,
                    )
            } else {
                // Limit reached while paused, permanently stop it.
                state.value =
                    AnswerTimerState.Stopped(
                        elapsedTimeMs = currentState.elapsedTimeMs,
                        limitMs = currentState.limitMs,
                    )
            }
        }
    }

    fun hide() {
        state.value = AnswerTimerState.Hidden
    }
}
