/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
