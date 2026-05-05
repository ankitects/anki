// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.android.view

import android.view.View

/** Wrapper around the `IntArray` returned by [View.getLocationOnScreen]. */
@JvmInline
value class ViewLocation(
    val data: IntArray,
) {
    val x: Int get() = data[0]
    val y: Int get() = data[1]
}

/** The receiver's position on the device screen. */
fun View.locationOnScreen(scratch: IntArray = IntArray(2)): ViewLocation {
    getLocationOnScreen(scratch)
    return ViewLocation(scratch)
}

/** The receiver's position relative to its window. */
fun View.locationInWindow(scratch: IntArray = IntArray(2)): ViewLocation {
    getLocationInWindow(scratch)
    return ViewLocation(scratch)
}
