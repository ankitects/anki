// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import kotlin.math.ceil
import kotlin.math.sqrt

/** [Insets.of], with named arguments */
fun insetsOf(
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
): Insets = Insets.of(left, top, right, bottom)

/**
 * The radius, in pixels, of the larger of the two bottom corner radii.
 */
val WindowInsetsCompat.bottomRoundedCornerRadius: Int
    get() =
        maxOf(
            getRoundedCorner(RoundedCornerCompat.POSITION_BOTTOM_LEFT)?.radius ?: 0,
            getRoundedCorner(RoundedCornerCompat.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
        )

/**
 * The vertical clearance, in pixels, which keeps end-aligned content clear of the
 *  bottom rounded display corners.
 *
 * This takes insets into account: for example in landscape, the 3-button nav bar means very little
 *  if no clearance from the corner is needed, as all content is shifted left.
 *
 * @param view supplies the layout direction: the end-side inset is the left system-bar inset
 * in RTL, otherwise the right
 */
fun WindowInsetsCompat.bottomCornerClearance(view: View): Int {
    val bars = getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    val endInset = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) bars.left else bars.right
    // The subtraction over-approximates the arc: content `d` inboard only needs
    // `r - sqrt(r² - (r - d)²)` of vertical clearance, and `r - d` is never less (a chord vs the
    // arc), so the content never dips into the corner.
    return (bottomRoundedCornerRadius - endInset).coerceAtLeast(0)
}

/**
 * The horizontal clearance, per side, which keeps the ends of a bottom-anchored row clear of the
 *  bottom rounded display corners.
 *
 * A row resting [bottomInset] above the screen bottom ends `r - bottomInset` below where a
 *  corner's arc begins, and there the arc reaches `r - sqrt(r² - (r - bottomInset)²)` in from the
 *  screen edge. Content at least that far inboard clears the arc at every height it occupies.
 *
 * The clearance is measured from the screen edge: subtract any side inset already applied to
 *  the row.
 */
fun WindowInsetsCompat.bottomCornerSideClearance(bottomInset: Int): Insets {
    fun clearance(position: Int): Int {
        val radius = getRoundedCorner(position)?.radius ?: 0
        val depth = radius - bottomInset
        if (depth <= 0) return 0
        return ceil(radius - sqrt((radius.toDouble() * radius) - (depth.toDouble() * depth))).toInt()
    }
    return insetsOf(
        left = clearance(RoundedCornerCompat.POSITION_BOTTOM_LEFT),
        right = clearance(RoundedCornerCompat.POSITION_BOTTOM_RIGHT),
    )
}

/**
 * A view's padding and margins from when [doOnApplyWindowInsets] was called.
 */
class InitialSpacing(
    val padding: Insets,
    val margins: Insets,
)

/**
 * Sets a window insets listener which is also supplied with the view's [InitialSpacing].
 *
 * Inset handlers usually add an inset to a padding or margin from the layout file. Deriving the
 * new value from the view compounds it when the insets are dispatched more than once, so [block]
 * is given the values from when the listener was set and sets absolute values based on them.
 *
 * The insets are returned unconsumed, so sibling views also receive them.
 */
fun View.doOnApplyWindowInsets(block: (view: View, insets: WindowInsetsCompat, initial: InitialSpacing) -> Unit) {
    val initial =
        InitialSpacing(
            padding = Insets.of(paddingLeft, paddingTop, paddingRight, paddingBottom),
            margins = Insets.of(marginLeft, marginTop, marginRight, marginBottom),
        )
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        block(view, insets, initial)
        insets
    }
}
