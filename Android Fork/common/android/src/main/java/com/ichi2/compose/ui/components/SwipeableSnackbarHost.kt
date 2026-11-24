// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

/** A snackbar host that also supports dismissing messages with a swipe in either direction. */
@Composable
fun SwipeableSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val touch = remember(hostState.currentSnackbarData) { mutableStateOf<SnackbarTouch?>(null) }
    val settling = remember(hostState.currentSnackbarData) { mutableStateOf(false) }
    SnackbarHost(hostState, modifier, touch = touch, timeoutPaused = settling.value) { data ->
        key(data) {
            fun isPressed() = hostState.currentSnackbarData === data && touch.value?.pressed == true

            val state =
                remember {
                    AnchoredDraggableState(
                        initialValue = SnackbarPosition.Visible,
                        anchors = DraggableAnchors { SnackbarPosition.Visible at 0f },
                    )
                }
            var snackbarWidth by remember { mutableIntStateOf(0) }
            val margin = 12.dp
            val marginPx = with(LocalDensity.current) { margin.roundToPx() }
            // Match SensibleSwipeDismissBehavior: half the width, or a deliberate fling.
            val flingThreshold = with(LocalDensity.current) { 1000.dp.toPx() }
            val minimumFlingVelocity = ViewConfiguration.get(LocalContext.current).scaledMinimumFlingVelocity
            val snapLayout =
                remember(state, flingThreshold, minimumFlingVelocity) {
                    object : SnapLayoutInfoProvider {
                        override fun calculateApproachOffset(
                            velocity: Float,
                            decayOffset: Float,
                        ) = 0f

                        override fun calculateSnapOffset(velocity: Float): Float {
                            val offset = state.requireOffset()
                            // ViewDragHelper treats velocities below the platform minimum as stationary.
                            val movingOutwards = velocity.absoluteValue < minimumFlingVelocity || velocity.sign == offset.sign
                            val direction =
                                when {
                                    velocity.absoluteValue > flingThreshold -> velocity.sign
                                    offset.absoluteValue > snackbarWidth / 2f && movingOutwards -> offset.sign
                                    else -> return -offset
                                }
                            val target = if (direction > 0f) SnackbarPosition.Right else SnackbarPosition.Left
                            return state.anchors.positionOf(target) - offset
                        }
                    }
                }
            val snapFling = rememberSnapFlingBehavior(snapLayout)
            val flingBehavior = remember(snapFling, settling) { TimeoutPausingFlingBehavior(snapFling, settling) }
            if (state.settledValue == SnackbarPosition.Visible) {
                // The custom fling does not set isAnimationRunning, so explicitly allow a touch to recapture it.
                @Suppress("DEPRECATION") // anchoredDraggable overload with startDragImmediately
                val dragModifier =
                    Modifier.anchoredDraggable(
                        state = state,
                        orientation = Orientation.Horizontal,
                        reverseDirection = false,
                        startDragImmediately = settling.value || state.isAnimationRunning,
                        flingBehavior = flingBehavior,
                    )
                Box(
                    // Keep the transparent margin outside the gesture handlers.
                    modifier =
                        Modifier
                            .onGloballyPositioned { coordinates ->
                                val width = coordinates.size.width
                                val contentWidth = (width - 2 * marginPx).coerceAtLeast(0)
                                snackbarWidth = contentWidth
                                val halfWidth = contentWidth / 2f
                                // The host scales around this center during its entrance animation.
                                val centerX = coordinates.localToRoot(Offset(width / 2f, 0f)).x
                                val viewportWidth = coordinates.findRootCoordinates().size.width
                                state.updateAnchors(
                                    DraggableAnchors {
                                        SnackbarPosition.Visible at 0f
                                        SnackbarPosition.Left at -(centerX + halfWidth)
                                        SnackbarPosition.Right at viewportWidth - centerX + halfWidth
                                    },
                                )
                            }.padding(margin)
                            .absoluteOffset {
                                // Keep the drag handler stationary while it tracks the finger.
                                // Once the finger lifts, move its hit area with the settling snackbar.
                                if (isPressed()) IntOffset.Zero else IntOffset(state.requireOffset().roundToInt(), 0)
                            }.pointerInput(data, touch) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    val press = SnackbarTouch(pressed = true)
                                    try {
                                        if (hostState.currentSnackbarData === data) touch.value = press
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                        } while (event.changes.any { it.pressed })
                                    } finally {
                                        if (touch.value === press) touch.value = SnackbarTouch(pressed = false)
                                    }
                                }
                            }.then(dragModifier),
                ) {
                    AnkiDroidSnackbar(
                        data,
                        Modifier.absoluteOffset {
                            if (isPressed()) IntOffset(state.requireOffset().roundToInt(), 0) else IntOffset.Zero
                        },
                    )
                }
            } else {
                // Material scales the outgoing snackbar towards the center during its fade.
                LaunchedEffect(data) { data.dismiss() }
            }
        }
    }
}

private enum class SnackbarPosition { Visible, Left, Right }

/** Pauses the snackbar timeout while the delegated fling settles. */
private class TimeoutPausingFlingBehavior(
    private val delegate: FlingBehavior,
    private val settling: MutableState<Boolean>,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        settling.value = true
        try {
            return with(delegate) { performFling(initialVelocity) }
        } finally {
            // View snackbars restart their full timeout when settling becomes idle.
            settling.value = false
        }
    }
}
