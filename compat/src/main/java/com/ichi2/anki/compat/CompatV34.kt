// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.compat

import android.view.MotionEvent
import androidx.annotation.RequiresApi

@RequiresApi(34)
@Suppress("ktlint:standard:property-naming")
open class CompatV34 : CompatV33() {
    override val AXIS_GESTURE_X_OFFSET = MotionEvent.AXIS_GESTURE_X_OFFSET
    override val AXIS_GESTURE_Y_OFFSET = MotionEvent.AXIS_GESTURE_Y_OFFSET
    override val AXIS_GESTURE_SCROLL_X_DISTANCE = MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE
    override val AXIS_GESTURE_SCROLL_Y_DISTANCE = MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE
    override val AXIS_GESTURE_PINCH_SCALE_FACTOR = MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR
}
