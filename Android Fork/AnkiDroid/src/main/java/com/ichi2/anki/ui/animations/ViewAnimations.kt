// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.animations

import android.view.View
import android.view.ViewPropertyAnimator
import com.ichi2.anki.common.utils.dp

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
