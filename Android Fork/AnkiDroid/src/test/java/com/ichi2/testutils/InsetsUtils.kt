// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.common.utils.Dp
import com.ichi2.anki.common.utils.dp

/**
 * The bottom navigation bar height when the gesture navigation indicator is hidden
 * ('Hide full screen indicator' on MIUI): no inset is reported and nothing is drawn at the
 * bottom of the screen, but the display corners are still rounded.
 */
val HIDDEN_GESTURE_BAR = 0.dp

/**
 * Helper to build [Insets] using [Dp]
 *
 * Default parameters allow for succinct code:
 *
 * ```kt
 * insetsOf(top = 24.dp)
 * ```
 *
 * @see Insets.of
 */
context(context: Context)
fun insetsOf(
    left: Dp = 0.dp,
    top: Dp = 0.dp,
    right: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Insets =
    Insets.of(
        left.toPx(context),
        top.toPx(context),
        right.toPx(context),
        bottom.toPx(context),
    )

/**
 * Builds realistic window insets, for Robolectric.
 *
 * A 24dp status bar is always present, others are optional:
 *
 * ```kt
 * windowInsetsOf(navBarRight = 48.dp, cutoutLeft = 32.dp)
 * ```
 *
 * The 'stable' insets ([WindowInsetsCompat.getInsetsIgnoringVisibility]) report the bars'
 * regions whether or not the bars are currently visible: [navBarStableBottom] stays reported
 * while immersive mode hides the bars themselves.
 *
 * @param navBarBottom a navigation bar along the bottom edge. Always appears in portrait,
 * on tablets, or if gesture navigation is enabled.
 * @param navBarRight a navigation bar along the right edge (phones in landscape mode with 3-button
 * navigation)
 * @param navBarStableBottom the bottom navigation bar's region, as still reported while it is hidden
 * @param cutoutLeft a display cutout on the left edge (landscape phone with a notch)
 * @param cutoutTop a display cutout on the top edge (portrait phone with a notch)
 * @param bottomCornerRadius the radius of both bottom rounded display corners
 * @param imeBottom the height of the on-screen keyboard
 * @param imeVisible whether the keyboard is shown. Defaults to whether it has a height: a physical
 * keyboard may show an IME with only a navigation strip, or no height at all
 * @param barsVisible whether the status and navigation bars are shown; `false` in immersive mode
 */
context(context: Context)
fun windowInsetsOf(
    navBarBottom: Dp = 0.dp,
    navBarRight: Dp = 0.dp,
    navBarStableBottom: Dp = navBarBottom,
    cutoutLeft: Dp = 0.dp,
    cutoutTop: Dp = 0.dp,
    bottomCornerRadius: Dp = 0.dp,
    imeBottom: Dp = 0.dp,
    imeVisible: Boolean = imeBottom.dp > 0f,
    barsVisible: Boolean = true,
): WindowInsetsCompat =
    WindowInsetsCompat
        .Builder()
        .setInsets(statusBars(), insetsOf(top = if (barsVisible) 24.dp else 0.dp))
        .setInsetsIgnoringVisibility(statusBars(), insetsOf(top = 24.dp))
        .setInsets(navigationBars(), insetsOf(right = navBarRight, bottom = navBarBottom))
        .setInsetsIgnoringVisibility(
            navigationBars(),
            insetsOf(right = navBarRight, bottom = navBarStableBottom),
        ).setInsets(displayCutout(), insetsOf(left = cutoutLeft, top = cutoutTop))
        .setInsetsIgnoringVisibility(displayCutout(), insetsOf(left = cutoutLeft, top = cutoutTop))
        .setInsets(ime(), insetsOf(bottom = imeBottom))
        .setVisible(ime(), imeVisible)
        .setVisible(statusBars() or navigationBars(), barsVisible)
        .apply {
            // set even when zero: Robolectric's WindowInsets.Builder leaks rounded corners between tests
            val radius = bottomCornerRadius.toPx(context)
            for (position in intArrayOf(RoundedCornerCompat.POSITION_BOTTOM_LEFT, RoundedCornerCompat.POSITION_BOTTOM_RIGHT)) {
                setRoundedCorner(position, RoundedCornerCompat(position, radius, radius, radius))
            }
        }.build()

/**
 * Dispatches [windowInsetsOf] at the decor, as an edge-to-edge device does.
 *
 * Robolectric otherwise reports all insets as zero.
 */
fun Activity.dispatchInsets(
    navBarBottom: Dp = 0.dp,
    navBarRight: Dp = 0.dp,
    navBarStableBottom: Dp = navBarBottom,
    cutoutLeft: Dp = 0.dp,
    cutoutTop: Dp = 0.dp,
    bottomCornerRadius: Dp = 0.dp,
    imeBottom: Dp = 0.dp,
    imeVisible: Boolean = imeBottom.dp > 0f,
    barsVisible: Boolean = true,
) {
    val insets =
        windowInsetsOf(
            navBarBottom = navBarBottom,
            navBarRight = navBarRight,
            navBarStableBottom = navBarStableBottom,
            cutoutLeft = cutoutLeft,
            cutoutTop = cutoutTop,
            bottomCornerRadius = bottomCornerRadius,
            imeBottom = imeBottom,
            imeVisible = imeVisible,
            barsVisible = barsVisible,
        )
    ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
}

/**
 * Injects insets to simulate an edge-to-edge on a real device.
 *
 * Overlays are displayed as translucent bands so content can be drawn behind them.
 *
 * @param cutoutLeft simulates a display cutout on the left edge, as when a phone with a
 * top notch is rotated to landscape.
 * @param navBarBottom the height of a navigation bar along the bottom edge:
 * * 48dp for 3-button
 * * less for gesture navigation.
 * * [HIDDEN_GESTURE_BAR] if the gesture indicator is hidden, in which case no band is drawn:
 *   nothing marks the area on a real device either.
 * @param bottomCornerRadius the radius of both bottom rounded display corners
 */
fun Activity.simulateSystemBars(
    cutoutLeft: Dp = 0.dp,
    navBarBottom: Dp = 48.dp,
    bottomCornerRadius: Dp = 0.dp,
) {
    val statusBarHeight = 24.dp
    val context: Context = this
    val insets =
        WindowInsetsCompat
            .Builder()
            .setInsets(statusBars(), insetsOf(top = statusBarHeight))
            // workaround for 'systemWindowInsets', so snackbars match a real device
            .setInsets(navigationBars(), insetsOf(left = cutoutLeft, bottom = navBarBottom))
            .setInsets(displayCutout(), insetsOf(left = cutoutLeft))
            .apply {
                // set even when zero: Robolectric's WindowInsets.Builder leaks rounded corners between tests
                val radius = bottomCornerRadius.toPx(context)
                for (position in intArrayOf(RoundedCornerCompat.POSITION_BOTTOM_LEFT, RoundedCornerCompat.POSITION_BOTTOM_RIGHT)) {
                    setRoundedCorner(position, RoundedCornerCompat(position, radius, radius, radius))
                }
            }.build()
    window.simulateSystemBars(insets, insetTarget = findViewById(android.R.id.content))
}

/**
 * Injects [insets] into an activity or dialog window and marks the system bars and display cutout
 * with translucent bands. Use [windowInsetsOf] to build the insets.
 *
 * @param insetTarget defaults to the decor; use the content view to bypass platform decor handling.
 */
@SuppressLint("RtlHardcoded") // insets and cutouts are physical: not layout-direction relative
fun Window.simulateSystemBars(
    insets: WindowInsetsCompat,
    insetTarget: View = decorView,
) {
    ViewCompat.dispatchApplyWindowInsets(insetTarget, insets)

    val decor = decorView as ViewGroup
    val bars = insets.getInsets(systemBars() or displayCutout())
    val bands =
        listOf(
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, bars.top, Gravity.TOP),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, bars.bottom, Gravity.BOTTOM),
            FrameLayout.LayoutParams(bars.left, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT),
            FrameLayout.LayoutParams(bars.right, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.RIGHT),
        )
    bands.filter { it.width != 0 && it.height != 0 }.forEach { params ->
        decor.addView(View(context).apply { setBackgroundColor(0x80000000.toInt()) }, params)
    }
    advanceRobolectricLooper()
}

/**
 * Injects insets to simulate the keyboard open over the navigation bar, as on an edge-to-edge
 * device.
 *
 * A translucent band marks where the keyboard would sit, so content drawn underneath it can be
 * seen.
 *
 * @param keyboardHeight the height of the keyboard, measured from the bottom of the screen
 * @param navBarBottom the height of the navigation bar, which the keyboard covers
 * @return the keyboard overlay, which can be removed before dispatching keyboard-hidden insets
 */
fun Activity.simulateKeyboard(
    keyboardHeight: Dp = 300.dp,
    navBarBottom: Dp = 48.dp,
): View {
    dispatchInsets(navBarBottom = navBarBottom, imeBottom = keyboardHeight)

    val decor = window.decorView as ViewGroup
    val overlay = View(this).apply { setBackgroundColor(0x80000000.toInt()) }
    decor.addView(
        overlay,
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, keyboardHeight.toPx(this), Gravity.BOTTOM),
    )
    advanceRobolectricLooper()
    return overlay
}
