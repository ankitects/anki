// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Danilo Mendes <danilodanicomendes@gmail.com>

package com.ichi2.anki.utils.ext

import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.color.MaterialColors

/**
 * Vertically centers this view on [textView]'s first line.
 * Call after layout, with both views in the same view hierarchy.
 */
fun View.alignWithFirstLineOf(textView: TextView) {
    val root = rootView as ViewGroup
    val textBounds = Rect(0, 0, textView.width, textView.height)
    val viewBounds = Rect(0, 0, width, height)
    root.offsetDescendantRectToMyCoords(textView, textBounds)
    root.offsetDescendantRectToMyCoords(this, viewBounds)

    val font = textView.paint.fontMetrics
    val firstLineCenter = textBounds.top + textView.baseline + (font.ascent + font.descent) / 2
    translationY = firstLineCenter - viewBounds.exactCenterY()
}

/**
 * Runs [action] on each global layout until [lifecycleOwner] is destroyed.
 * Use a fragment's `viewLifecycleOwner` when observing its views.
 */
fun View.onGlobalLayout(
    lifecycleOwner: LifecycleOwner,
    action: () -> Unit,
) {
    if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
    val listener = OnGlobalLayoutListener(action)
    viewTreeObserver.addOnGlobalLayoutListener(listener)
    lifecycleOwner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                // The observer is shared with the window and can outlive the lifecycle owner.
                viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(listener)
            }
        },
    )
}

fun View.isTouchWithinBounds(event: MotionEvent): Boolean {
    val rect = Rect().apply { getDrawingRect(this) }
    return rect.contains((left + event.x).toInt(), (top + event.y).toInt())
}

/** Tints the background to [color] at the given opacity (0f..1f) */
fun View.setBackgroundTint(
    color: Int,
    alpha: Float,
) {
    backgroundTintList =
        ColorStateList.valueOf(
            MaterialColors.compositeARGBWithAlpha(color, (alpha * 255).toInt()),
        )
}
