// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.common.utils.android

import android.content.Context

// TODO: replace this with the Dp class

/** Scales [value] (in dp) to pixels using the device's display density. */
fun getDensityAdjustedValue(
    context: Context,
    value: Float,
): Float = context.resources.displayMetrics.density * value
