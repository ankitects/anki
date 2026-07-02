// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.compat

import android.app.Activity
import android.view.MotionEvent
import androidx.annotation.AnimRes
import androidx.annotation.RequiresApi

@RequiresApi(34)
@Suppress("ktlint:standard:property-naming")
open class CompatV34 : CompatV33() {
    override val AXIS_GESTURE_X_OFFSET = MotionEvent.AXIS_GESTURE_X_OFFSET
    override val AXIS_GESTURE_Y_OFFSET = MotionEvent.AXIS_GESTURE_Y_OFFSET
    override val AXIS_GESTURE_SCROLL_X_DISTANCE = MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE
    override val AXIS_GESTURE_SCROLL_Y_DISTANCE = MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE
    override val AXIS_GESTURE_PINCH_SCALE_FACTOR = MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR

    override fun overrideTransition(
        activity: Activity,
        @AnimRes enter: Int,
        @AnimRes exit: Int,
        open: Boolean,
    ) {
        val ty =
            if (open) {
                Activity.OVERRIDE_TRANSITION_OPEN
            } else {
                Activity.OVERRIDE_TRANSITION_CLOSE
            }
        activity.overrideActivityTransition(ty, enter, exit)
    }
}
