// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * @param resId must be a [StringRes] or a [PluralsRes]
 */
fun Resources.getFormattedStringOrPlurals(
    resId: Int,
    quantity: Int,
): String =
    when (getResourceTypeName(resId)) {
        "string" -> getString(resId, quantity)
        "plurals" -> getQuantityString(resId, quantity, quantity)
        else -> throw IllegalArgumentException("Provided resId is not a valid @StringRes or @PluralsRes")
    }

/**
 * @see [Resources.getFormattedStringOrPlurals]
 */
fun Context.getFormattedStringOrPlurals(
    resId: Int,
    quantity: Int,
): String = resources.getFormattedStringOrPlurals(resId, quantity)

// https://m3.material.io/foundations/layout/applying-layout/window-size-classes
// adopted smallestScreenWidthDp instead of screenWidthDp
// to avoid layout changes when rotating the device
fun Resources.isWindowCompact() = configuration.smallestScreenWidthDp < 600
