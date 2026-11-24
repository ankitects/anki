// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.account

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.insetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class AccountActivityScreenshotTest : ScreenshotTest() {
    /** 3-button navigation: a tall bottom inset which the form scrolls under but rests above */
    @Test
    fun loggedIn() =
        withLoggedIn { activity ->
            activity.simulateNavigationBar()
            captureScreen("logged_in")
        }

    @Test
    fun loggedOut() =
        withLoggedOut { activity ->
            activity.simulateNavigationBar()
            captureScreen("logged_out")
        }

    /** The AnkiWeb 'remove account' WebView, shown over the logged in screen */
    @Test
    fun removeAccount() =
        withLoggedIn { activity ->
            // show the fragment first: insets are only received by attached views
            activity.findViewById<Button>(R.id.remove_account_button).performClick()
            advanceRobolectricLooper()
            activity.simulateNavigationBar()
            captureScreen("remove_account")
        }

    /** Gesture navigation: a short inset, but larger rounded corners to clear */
    @Test
    fun loggedOutGestureNavigation() =
        withLoggedOut { activity ->
            activity.simulateGestureNavigationBar()
            captureScreen("logged_out_gesture_navigation")
        }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side. The app bar spans both, its content clears them.
     */
    @Test
    fun loggedOutLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withLoggedOut { activity ->
            activity.simulateSideNavigationBar()
            captureScreen("logged_out_landscape")
        }
    }

    private fun withLoggedIn(block: (AccountActivity) -> Unit) {
        Prefs.hkey = "my precious hkey"
        Prefs.username = "lovely@example.com"
        launchAccount(block)
    }

    private fun withLoggedOut(block: (AccountActivity) -> Unit) {
        Prefs.hkey = ""
        launchAccount(block)
    }

    private fun launchAccount(block: (AccountActivity) -> Unit) {
        ActivityScenario.launch<AccountActivity>(AccountActivity.getIntent(targetContext)).use { scenario ->
            scenario.onActivity(block)
        }
    }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a device, and overlay a translucent band where
     * the navigation bar would sit, to show whether anything is drawn underneath it.
     */
    private fun Activity.simulateNavigationBar() {
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
    private fun Activity.simulateGestureNavigationBar() {
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
    private fun Activity.simulateSideNavigationBar() {
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

    private fun Activity.addNavBarOverlay(
        width: Int,
        height: Int,
        gravity: Int,
    ) {
        val decor = window.decorView as ViewGroup
        val overlay = View(this).apply { setBackgroundColor(0x80000000.toInt()) }
        decor.addView(overlay, FrameLayout.LayoutParams(width, height, gravity))
    }
}
