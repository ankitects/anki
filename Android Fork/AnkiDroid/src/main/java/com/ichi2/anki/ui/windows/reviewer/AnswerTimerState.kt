// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

sealed interface AnswerTimerState {
    data object Hidden : AnswerTimerState

    data class Running(
        val baseTime: Long,
        val limitMs: Int,
    ) : AnswerTimerState

    data class Paused(
        val elapsedTimeMs: Long,
        val limitMs: Int,
    ) : AnswerTimerState

    data class Stopped(
        val elapsedTimeMs: Long,
        val limitMs: Int,
    ) : AnswerTimerState
}
