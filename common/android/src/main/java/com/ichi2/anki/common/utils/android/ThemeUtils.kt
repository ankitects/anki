// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.common.utils.android

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.withStyledAttributes

fun getResFromAttr(
    context: Context,
    resAttr: Int,
): Int {
    val attrs = intArrayOf(resAttr)
    return getResFromAttr(context, attrs)[0]
}

/**
 * NOTE: dangerous function, it mutates the input array and returns it!
 */
fun getResFromAttr(
    context: Context,
    attrs: IntArray,
): IntArray {
    context.withStyledAttributes(attrs = attrs) {
        for (i in attrs.indices) {
            attrs[i] = getResourceId(i, 0)
        }
    }
    return attrs
}

fun systemIsInNightMode(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
