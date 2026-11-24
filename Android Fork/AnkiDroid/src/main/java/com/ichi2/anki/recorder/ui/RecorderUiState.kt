// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import com.ichi2.anki.recorder.RecorderState

enum class RecorderStatus {
    READY,
    RECORDING,
    PAUSED,
    RECORDED,
}

data class RecorderUiState(
    val status: RecorderStatus = RecorderStatus.READY,
    val elapsedSeconds: Long = 0,
)

fun RecorderState.toStatus(): RecorderStatus =
    when (this) {
        RecorderState.Idle -> RecorderStatus.READY
        RecorderState.Recording -> RecorderStatus.RECORDING
        RecorderState.Paused -> RecorderStatus.PAUSED
        is RecorderState.Recorded -> RecorderStatus.RECORDED
    }
