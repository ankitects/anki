// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.annotation.DrawableRes

/**
 * Sets the [Drawables][Drawable] (if any) to appear to the start of, above, to the end
 * of, and below the text. Use `null` if you do not want a Drawable
 * there. The Drawables' bounds will be set to their intrinsic bounds.
 *
 * Calling this method will overwrite any Drawables previously set using
 * [TextView.setCompoundDrawables] or related methods.
 *
 * @param start Resource identifier of the start Drawable.
 * @param top Resource identifier of the top Drawable.
 * @param end Resource identifier of the end Drawable.
 * @param bottom Resource identifier of the bottom Drawable.
 *
 * @see `android.R.styleable.TextView_drawableStart`
 * @see `android.R.styleable.TextView_drawableTop`
 * @see `android.R.styleable.TextView_drawableEnd`
 * @see `android.R.styleable.TextView_drawableBottom`
 */
// Kt = Kotlin-based extension supporting nullable arguments, and named arguments
fun TextView.setCompoundDrawablesRelativeWithIntrinsicBoundsKt(
    @DrawableRes start: Int = 0,
    @DrawableRes top: Int = 0,
    @DrawableRes end: Int = 0,
    @DrawableRes bottom: Int = 0,
) {
    this.setCompoundDrawablesRelativeWithIntrinsicBounds(
        start,
        top,
        end,
        bottom,
    )
}
