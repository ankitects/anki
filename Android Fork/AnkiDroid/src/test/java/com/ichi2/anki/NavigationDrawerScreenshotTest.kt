// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.insetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import com.ichi2.anki.common.android.R as CommonR

/**
 * Screenshot tests for the navigation drawer of [NavigationDrawerActivity]
 *
 * [DeckPicker] is used as the host activity
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.NavigationDrawerScreenshotTest"`
 */
class NavigationDrawerScreenshotTest : ScreenshotTest() {
    @Test
    fun navigationDrawer_edgeToEdge() =
        withDeckPicker(deckCount = 5) { deckPicker ->
            openDrawer(deckPicker)
            deckPicker.simulateEdgeToEdge()
            captureScreen("navigationDrawer")
        }

    /**
     * Landscape with 3-button navigation on the same side as the drawer: the drawer's
     * content must clear the navigation bar rather than sliding underneath it.
     */
    @Test
    fun navigationDrawer_sideNavigationBar() {
        RuntimeEnvironment.setQualifiers("+land")
        withDeckPicker(deckCount = 5) { deckPicker ->
            openDrawer(deckPicker)
            deckPicker.simulateLeftNavigationBar()
            captureScreen("sideNavigationBar")
        }
    }

    private fun openDrawer(deckPicker: DeckPicker) {
        deckPicker.drawerLayout.openDrawer(GravityCompat.START, false)
        advanceRobolectricLooper()
    }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a real device, and overlay a translucent band
     * where the nav bar would sit to see if content is drawn underneath it.
     */
    private fun DeckPicker.simulateLeftNavigationBar() {
        val navBarWidth = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(left = navBarWidth))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                // the navigationBarColor of the app's themes
                setBackgroundResource(CommonR.color.semi_transparent_black)
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                navBarWidth.toPx(targetContext),
                FrameLayout.LayoutParams.MATCH_PARENT,
                // windowInsets does not have a concept of "START"
                @SuppressLint("RtlHardcoded")
                Gravity.LEFT,
            ),
        )
    }
}
