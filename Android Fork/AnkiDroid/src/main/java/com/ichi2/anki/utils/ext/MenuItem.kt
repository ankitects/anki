// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.content.Context
import android.view.MenuItem
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

// ActionMenuView drawables don't handle directionality by default
fun MenuItem.setIconRes(
    context: Context,
    resId: Int,
) {
    ContextCompat.getDrawable(context, resId)?.mutate()?.let { drawable ->
        DrawableCompat.setLayoutDirection(drawable, context.resources.configuration.layoutDirection)
        setIcon(drawable)
    }
}
