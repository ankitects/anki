// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.InsetDrawable
import androidx.core.content.withStyledAttributes
import com.google.android.material.shape.MaterialShapeDrawable
import com.ichi2.anki.common.android.R

/**
 * Outlines a Material dialog's window with the theme's widget outline (`widgetStrokeWidth`,
 * `widgetStrokeColor`), which only E-ink sets.
 *
 * Material dialogs set their window background in code, and a window then ignores the theme's
 * `android:windowFrame`. So the outline is drawn as the window's foreground instead: above the
 * content, so a dialog's toolbar can't cover it. It follows the background's shape and insets.
 *
 * Does nothing in themes without an outline, or for windows not drawn by a [MaterialShapeDrawable].
 */
fun Dialog.outlineWindow() {
    var strokeWidth = 0f
    var strokeColor = Color.TRANSPARENT
    context.withStyledAttributes(attrs = intArrayOf(R.attr.widgetStrokeWidth, R.attr.widgetStrokeColor)) {
        strokeWidth = getDimension(0, 0f)
        strokeColor = getColor(1, Color.TRANSPARENT)
    }
    if (strokeWidth <= 0f) return

    // Installs the decor first: installing it later would reset the foreground
    val decorView = window?.decorView ?: return
    val insets = Rect()
    val shape =
        when (val background = decorView.background) {
            is MaterialShapeDrawable -> background
            is InsetDrawable -> background.drawable.also { background.getPadding(insets) } as? MaterialShapeDrawable
            else -> null
        } ?: return
    val outline =
        MaterialShapeDrawable(shape.shapeAppearanceModel).apply {
            fillColor =
                android.content.res.ColorStateList
                    .valueOf(Color.TRANSPARENT)
            setStroke(strokeWidth, strokeColor)
        }
    decorView.foreground = InsetDrawable(outline, insets.left, insets.top, insets.right, insets.bottom)
}
