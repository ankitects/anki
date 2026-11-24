// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.ichi2.anki.utils.runWithOOMCheck

/**
 * [ImageView.setImageDrawable] guarded against [OutOfMemoryError] (#6608).
 *
 * @return `true` if the drawable was applied, `false` if an [OutOfMemoryError] occurred
 */
fun ImageView.setImageDrawableSafe(
    drawable: Drawable?,
    onError: (OutOfMemoryError) -> Unit = {},
): Boolean =
    runWithOOMCheck(
        action = {
            setImageDrawable(drawable)
            true
        },
        onError = onError,
    ) ?: false
