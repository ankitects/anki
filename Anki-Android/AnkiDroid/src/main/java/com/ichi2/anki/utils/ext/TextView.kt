/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
