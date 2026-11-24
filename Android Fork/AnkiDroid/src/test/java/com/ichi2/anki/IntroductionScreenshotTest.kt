// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.insetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for [IntroductionActivity]
 *
 * The top gradient is expected to run under the status bar: it is decorative, and the buttons
 * below it are the only touch targets which need to clear the system bars.
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.IntroductionScreenshotTest"`
 */
class IntroductionScreenshotTest : ScreenshotTest() {
    /** 3-button navigation: a tall bottom inset which the buttons rest above */
    @Test
    fun introductionPortrait() =
        withIntroduction { activity ->
            activity.simulateNavigationBar()
            captureScreen("portrait")
        }

    /** Gesture navigation: a short inset, but larger rounded corners to clear */
    @Test
    fun introductionGestureNavigation() =
        withIntroduction { activity ->
            activity.simulateGestureNavigationBar()
            captureScreen("gesture_navigation")
        }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side. The content clears both sides.
     */
    @Test
    fun introductionLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withIntroduction { activity ->
            activity.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    private fun withIntroduction(block: (IntroductionActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                IntroductionActivity::class.java,
                Intent(),
            )
        advanceRobolectricLooper()
        block(activity)
    }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a real device, and overlay a translucent band
     * where the nav bar would sit to see if content is drawn underneath it.
     */
    private fun IntroductionActivity.simulateNavigationBar() {
        val navBarHeight = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
        addNavBarOverlay(FrameLayout.LayoutParams.MATCH_PARENT, navBarHeight.toPx(targetContext), Gravity.BOTTOM)
    }

    /** As [simulateNavigationBar], but gesture navigation: a short inset, larger rounded corners */
    private fun IntroductionActivity.simulateGestureNavigationBar() {
        val navBarHeight = 24.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .apply {
                        val radius = 34.dp.toPx(targetContext)
                        setRoundedCorner(
                            RoundedCornerCompat.POSITION_BOTTOM_LEFT,
                            RoundedCornerCompat(RoundedCornerCompat.POSITION_BOTTOM_LEFT, radius, radius, radius),
                        )
                    }.build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
        addNavBarOverlay(FrameLayout.LayoutParams.MATCH_PARENT, navBarHeight.toPx(targetContext), Gravity.BOTTOM)
    }

    /** As [simulateNavigationBar], but landscape: a side navigation bar and an opposite cutout */
    private fun IntroductionActivity.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(left = 32.dp))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
        addNavBarOverlay(navBarWidth.toPx(targetContext), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END)
    }

    private fun IntroductionActivity.addNavBarOverlay(
        width: Int,
        height: Int,
        gravity: Int,
    ) {
        val decor = window.decorView as ViewGroup
        val overlay = View(this).apply { setBackgroundColor(0x80000000.toInt()) }
        decor.addView(overlay, FrameLayout.LayoutParams(width, height, gravity))
    }
}
