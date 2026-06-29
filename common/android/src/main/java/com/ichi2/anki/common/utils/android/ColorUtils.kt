// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.android

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.common.utils.ext.clamp

@ColorInt
fun getColorFromAttr(
    context: Context,
    attr: Int,
): Int = MaterialColors.getColor(context, attr, 0)

/**
 * NOTE: dangerous function, it mutates the input array and returns it!
 */
@ColorInt
fun getColorsFromAttrs(
    context: Context,
    attrs: IntArray,
): IntArray {
    for (i in attrs.indices) {
        attrs[i] = getColorFromAttr(context, attrs[i])
    }
    return attrs
}

/**
 * Darkens the provided ARGB color by a provided [factor]
 *
 * @param argb The ARGB color to transform
 * @param factor Amount to darken, between 1.0f (no change) and 0.0f (black)
 * @return The darkened color in ARGB
 */
@ColorInt
fun darkenColor(
    @ColorInt argb: Int,
    factor: Float,
): Int {
    val hsv = argb.toHSV()
    // https://en.wikipedia.org/wiki/HSL_and_HSV
    // The third component is the 'value', or 'lightness/darkness'
    hsv[2] = (hsv[2] * factor).clamp(0f, 1f)
    return Color.HSVToColor(hsv)
}

/**
 * Lightens the provided ARGB color by a provided [amount]
 *
 * @param argb The ARGB color to transform
 * @param amount Amount to lighten, between 0.0f (no change) and 1.0f (100% brightness)
 * @return The lightened color in ARGB
 */
@ColorInt
fun lightenColorAbsolute(
    @ColorInt argb: Int,
    amount: Float,
): Int {
    val hsv = argb.toHSV()
    // https://en.wikipedia.org/wiki/HSL_and_HSV
    // The third component is the 'value', or 'lightness/darkness'
    hsv[2] = (hsv[2] + amount).clamp(0f, 1f)
    return Color.HSVToColor(hsv)
}

/**
 * Converts an ARGB color to an array of its HSV components
 *
 * [0] is Hue: `[0..360[`
 * [1] is Saturation: `[0...1]`
 * [2] is Value: `[0...1]`
 */
private fun Int.toHSV(): FloatArray =
    FloatArray(3).also { arr ->
        Color.colorToHSV(this, arr)
    }
