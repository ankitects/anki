// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View

/**
 * Performs a hit test using the raw coordinates of the provided [MotionEvent]
 */
fun View.rawHitTest(event: MotionEvent): Boolean {
    val location = IntArray(2)
    getLocationOnScreen(location)

    val rect = Rect()
    getHitRect(rect)

    rect.left += location[0]
    rect.top += location[1]
    rect.right += location[0]
    rect.bottom += location[1]

    return rect.contains(event.rawX.toInt(), event.rawY.toInt())
}
