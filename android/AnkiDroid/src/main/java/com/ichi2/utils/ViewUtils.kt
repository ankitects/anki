/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.core.view.OnReceiveContentListener
import androidx.draganddrop.DropHelper

/** @see View.performClick */
fun View.performClickIfEnabled() {
    if (isEnabled) performClick()
}

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

/**
 * If possible, configures a [View] for drag and drop operations, including highlighting that
 * indicates the view is a drop target. Sets a listener that enables the view to handle dropped data.
 *
 * @see DropHelper.configureView
 */
fun View.configureView(
    activity: Activity,
    mimeTypes: Array<String>,
    options: DropHelper.Options,
    onReceiveContentListener: OnReceiveContentListener,
) {
    DropHelper.configureView(
        activity,
        this,
        mimeTypes,
        options,
        onReceiveContentListener,
    )
}

/**
 * Sets the relative padding for all dimensions of the view
 *
 * The view may add on the space required to display the scrollbars,
 * depending on the style and visibility of the scrollbars.
 * So the values returned from `getPadding` calls
 * may be different from the values set in this call.
 */
fun View.setPaddingRelative(px: Int) = setPaddingRelative(px, px, px, px)

/**
 * Sets the relative padding for all dimensions of the view
 *
 * The view may add on the space required to display the scrollbars,
 * depending on the style and visibility of the scrollbars.
 * So the values returned from `getPadding` calls
 * may be different from the values set in this call.
 */
@Suppress("unused")
fun View.setPaddingRelative(dp: Dp) = setPaddingRelative(dp.toPx(context))

/**
 * Sets the relative padding
 *
 * The view may add on the space required to display the scrollbars,
 * depending on the style and visibility of the scrollbars.
 * So the values returned from `getPadding` calls
 * may be different from the values set in this call.
 */
// Since we're in Kotlin, this now allows named arguments!
@Suppress("unused")
fun View.setPaddingRelative(
    start: Dp,
    top: Dp,
    end: Dp,
    bottom: Dp,
) = setPaddingRelative(start.toPx(context), top.toPx(context), end.toPx(context), bottom.toPx(context))

/**
 * Updates the relative padding for the [View]
 * This version of the method allows using named parameters to just set one or more axes.
 *
 * @see View.setPaddingRelative
 */
fun View.updatePaddingRelative(
    start: Dp? = null,
    top: Dp? = null,
    end: Dp? = null,
    bottom: Dp? = null,
) {
    setPaddingRelative(
        start?.toPx(context) ?: this.paddingStart,
        top?.toPx(context) ?: this.paddingTop,
        end?.toPx(context) ?: this.paddingEnd,
        bottom?.toPx(context) ?: this.paddingBottom,
    )
}

/** Returns a [Dp] instance equal to this [Int] number of display pixels. */
val Int.dp
    get() = Dp(dp = this.toFloat())

val Float.dp
    get() = Dp(dp = this)

val Double.dp
    get() = Dp(dp = this.toFloat())

/**
 * Helper for 'display pixels' to 'pixels' conversions
 */
@JvmInline
value class Dp(
    val dp: Float,
) {
    // TODO: improve once we have context parameters
    fun toPx(context: Context) = dp.dpToPx(context)
}

private fun Float.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density + 0.5f).toInt()

fun View.isRtl() = isRtl(resources)

fun isRtl(res: Resources): Boolean = res.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
