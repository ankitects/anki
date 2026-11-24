// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.ui.windows.permissions.AllPermissionsExplanationActivity
import com.ichi2.testutils.insetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for [AllPermissionsExplanationActivity]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.AllPermissionsExplanationScreenshotTest"`
 */
class AllPermissionsExplanationScreenshotTest : ScreenshotTest() {
    @Test
    fun allPermissionsExplanation() =
        withAllPermissionsExplanation { activity ->
            activity.simulateNavigationBar()
            captureScreen("portrait")
        }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side. The headline and permissions clear both sides.
     */
    @Test
    fun allPermissionsExplanationLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withAllPermissionsExplanation { activity ->
            activity.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    private fun withAllPermissionsExplanation(block: (AllPermissionsExplanationActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                AllPermissionsExplanationActivity::class.java,
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
    private fun AllPermissionsExplanationActivity.simulateNavigationBar() {
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

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                navBarHeight.toPx(targetContext),
                Gravity.BOTTOM,
            ),
        )
    }

    /**
     * As [simulateNavigationBar], but for landscape with 3-button navigation: the navigation bar
     * is a side inset, with the camera cutout on the opposite side.
     */
    private fun AllPermissionsExplanationActivity.simulateSideNavigationBar() {
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

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                navBarWidth.toPx(targetContext),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END,
            ),
        )
    }
}
