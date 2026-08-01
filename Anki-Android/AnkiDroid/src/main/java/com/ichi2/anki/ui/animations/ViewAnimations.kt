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

package com.ichi2.anki.ui.animations

import android.view.View
import android.view.ViewPropertyAnimator
import com.ichi2.utils.dp

fun View.fadeIn(
    duration: Int,
    translation: Float = 8.dp.toPx(context).toFloat(),
    startAction: Runnable? = Runnable { this.visibility = View.VISIBLE },
): ViewPropertyAnimator {
    this.animate().cancel()
    this.alpha = 0f
    this.translationY = translation
    return this
        .animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(duration.toLong())
        .withStartAction(startAction)
}

fun View.fadeOut(
    duration: Int,
    translation: Float = 8.dp.toPx(context).toFloat(),
    endAction: Runnable? =
        Runnable {
            this.visibility = View.GONE
        },
): ViewPropertyAnimator {
    this.animate().cancel()
    this.alpha = 1f
    this.translationY = 0f
    return this
        .animate()
        .alpha(0f)
        .translationY(translation)
        .setDuration(duration.toLong())
        .withEndAction(endAction)
}
