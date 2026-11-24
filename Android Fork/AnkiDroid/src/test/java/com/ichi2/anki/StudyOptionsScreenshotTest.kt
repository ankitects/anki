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
import com.ichi2.testutils.insetsOf
import com.ichi2.testutils.simulateKeyboard
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for [StudyOptionsActivity]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.StudyOptionsScreenshotTest"`
 */
class StudyOptionsScreenshotTest : ScreenshotTest() {
    @Test
    fun studyOptionsWithCounts() =
        withStudyOptions { activity ->
            activity.simulateNavigationBar()
            captureScreen("with_counts")
        }

    @Test
    fun studyOptionsKeyboardOpen() =
        withStudyOptions { activity ->
            activity.simulateKeyboard()
            captureScreen("keyboard_open")
        }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side. The toolbar and content clear both sides.
     */
    @Test
    fun studyOptionsLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withStudyOptions { activity ->
            activity.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    private fun withStudyOptions(block: (StudyOptionsActivity) -> Unit) {
        val deckId = addDeck("Deck 1", setAsSelected = true)
        addNoteToDeck(deckId, count = 5)
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                StudyOptionsActivity::class.java,
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
    private fun StudyOptionsActivity.simulateNavigationBar() {
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
    private fun StudyOptionsActivity.simulateSideNavigationBar() {
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
