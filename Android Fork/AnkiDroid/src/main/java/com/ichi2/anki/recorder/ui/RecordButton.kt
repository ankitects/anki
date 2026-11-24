// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R

/**
 * Toggles recording on press, so a tap starts, pauses or resumes. [onToggle] returns whether it is
 * now recording, and if so, letting go after a long press calls [onHoldRelease].
 */
@Composable
internal fun RecordButton(
    status: RecorderStatus,
    onToggle: () -> Boolean,
    onHoldRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnHoldRelease by rememberUpdatedState(onHoldRelease)
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colorResource(R.color.audio_recorder_red))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = { onToggle() },
                ).pointerInput(interactionSource) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val press = PressInteraction.Press(down.position)
                        interactionSource.tryEmit(press)
                        val startedRecording = currentOnToggle()
                        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitAllPointersUp() } == null
                        if (held) awaitAllPointersUp()
                        interactionSource.tryEmit(PressInteraction.Release(press))
                        if (startedRecording && held) currentOnHoldRelease()
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (status == RecorderStatus.RECORDING) R.drawable.round_pause_24 else R.drawable.ic_action_mic),
            contentDescription =
                stringResource(
                    when (status) {
                        RecorderStatus.RECORDING -> CommonString.pause_recording
                        RecorderStatus.PAUSED -> CommonString.resume_recording
                        else -> CommonString.start_recording
                    },
                ),
            tint = Color.White,
        )
    }
}

private suspend fun AwaitPointerEventScope.awaitAllPointersUp(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
        if (event.changes.none { it.pressed }) return event.changes.first()
    }
}
