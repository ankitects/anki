// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.view.View
import com.ichi2.anki.common.utils.Dp

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
