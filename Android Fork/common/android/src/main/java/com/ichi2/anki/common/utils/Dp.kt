// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils

import android.content.Context

/** Returns a [Dp] instance equal to this [Int] number of display pixels. */
val Int.dp
    get() = Dp(dp = this.toFloat())

val Float.dp
    get() = Dp(dp = this)

val Double.dp
    get() = Dp(dp = this.toFloat())

/**
 * Helper for 'display pixels' to 'pixels' conversions
 */
@JvmInline
value class Dp(
    val dp: Float,
) : Comparable<Dp> {
    // TODO: improve once we have context parameters
    fun toPx(context: Context) = dp.dpToPx(context)

    operator fun plus(other: Dp) = Dp(dp = dp + other.dp)

    override fun compareTo(other: Dp) = dp.compareTo(other.dp)
}

private fun Float.dpToPx(context: Context): Int = (this * context.resources.displayMetrics.density + 0.5f).toInt()
