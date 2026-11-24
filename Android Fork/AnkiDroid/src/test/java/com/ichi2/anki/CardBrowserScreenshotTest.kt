// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

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
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.browser.CardBrowserColumn.ANSWER
import com.ichi2.anki.browser.setColumn
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.ui.RecyclerFastScroller
import com.ichi2.testutils.HIDDEN_GESTURE_BAR
import com.ichi2.testutils.insetsOf
import com.ichi2.testutils.scrollToEnd
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.testutils.simulateSystemBars
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for [CardBrowser]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.CardBrowserScreenshotTest"`
 */
class CardBrowserScreenshotTest : ScreenshotTest() {
    @Test
    fun `long browser cells wrap when collapsed and expanded - Issue 22827`() =
        runTest {
            val text = "A long paragraph of card text. ".repeat(2000)
            addBasicNote(text, text)
            val browser = getBrowserWithNotes(noteCount = 0)
            browser.viewModel.setColumn(1, ANSWER)
            advanceRobolectricLooper()
            browser.waitForSearchResults()

            for ((truncated, state) in listOf(true to "collapsed", false to "expanded", true to "collapsed_again")) {
                browser.viewModel.setTruncated(truncated).join()
                advanceRobolectricLooper()
                captureScreen("long_cells_$state")
            }
        }

    @Test
    fun cardBrowserWith30Notes() =
        withCardBrowser(noteCount = 50) { browser ->
            browser.simulateNavigationBar()
            captureScreen("30_notes")
        }

    /**
     * When fully scrolled: the last row, the bottom of the scroll track and the bottom of the
     * scroll handle all rest on the same line, above the navigation bar/rounded corners.
     *
     * Screenshot counterpart of [CardBrowserInsetsTest]
     * `handle, track and last row rest on the same line when fully scrolled`, which asserts the
     * same geometry programmatically.
     */
    @Test
    fun cardBrowserScrolledToBottom() =
        withCardBrowser(noteCount = 50) { browser ->
            browser.simulateNavigationBar()

            val list = browser.findViewById<RecyclerView>(R.id.card_browser_list)
            list.scrollToEnd()

            // keep the auto-hiding fast scroller visible for the capture
            browser.findViewById<RecyclerFastScroller>(R.id.browser_scroller).show(animate = false)
            advanceRobolectricLooper()

            captureScreen("scrolled_to_bottom")
        }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side. The toolbar and content clear both sides, no bottom buffer
     * is reserved (the side inset already clears the rounded corner) and the scroll track runs to
     * the bottom edge. [CardBrowserInsetsTest] asserts the same geometry programmatically.
     */
    @Test
    fun cardBrowserLandscapeScrolledToBottom() {
        RuntimeEnvironment.setQualifiers("+land")
        withCardBrowser(noteCount = 50) { browser ->
            browser.simulateSideNavigationBar()

            val list = browser.findViewById<RecyclerView>(R.id.card_browser_list)
            list.scrollToEnd()

            // keep the auto-hiding fast scroller visible for the capture
            browser.findViewById<RecyclerFastScroller>(R.id.browser_scroller).show(animate = false)
            advanceRobolectricLooper()

            captureScreen("landscape_scrolled_to_bottom")
        }
    }

    /**
     * Landscape with gesture navigation: no side inset, so when scrolled to the bottom, the
     *  final row and scrollbar should rest above the corner, to allow interaction.
     *
     * Screenshot counterpart of [CardBrowserInsetsTest]
     * `rounded display corners are cleared when larger than the navigation bar`.
     */
    @Test
    fun cardBrowserLandscapeGestureNavigationScrolledToBottom() {
        RuntimeEnvironment.setQualifiers("+land")
        withCardBrowser(noteCount = 50) { browser ->
            browser.simulateGestureNavigationBar()

            val list = browser.findViewById<RecyclerView>(R.id.card_browser_list)
            list.scrollToEnd()

            // keep the auto-hiding fast scroller visible for the capture
            browser.findViewById<RecyclerFastScroller>(R.id.browser_scroller).show(animate = false)
            advanceRobolectricLooper()

            captureScreen("landscape_gesture_scrolled_to_bottom")
        }
    }

    @Test
    fun cardBrowserGestureBarHiddenScrolledToBottom() =
        withCardBrowser(noteCount = 50) { browser ->
            browser.simulateSystemBars(navBarBottom = HIDDEN_GESTURE_BAR, bottomCornerRadius = 34.dp)

            val list = browser.findViewById<RecyclerView>(R.id.card_browser_list)
            list.scrollToEnd()

            // keep the auto-hiding fast scroller visible for the capture
            browser.findViewById<RecyclerFastScroller>(R.id.browser_scroller).show(animate = false)
            advanceRobolectricLooper()

            captureScreen("gesture_bar_hidden_scrolled_to_bottom")
        }

    @Test
    fun cardBrowserKeyboardScrolledToBottom() =
        withCardBrowser(noteCount = 50) { browser ->
            requireNotNull(browser.cardBrowserFragment.searchItem).expandActionView()
            advanceRobolectricLooper()
            browser.simulateKeyboard()

            val list = browser.findViewById<RecyclerView>(R.id.card_browser_list)
            list.scrollToEnd()

            // keep the auto-hiding fast scroller visible for the capture
            browser.findViewById<RecyclerFastScroller>(R.id.browser_scroller).show(animate = false)
            advanceRobolectricLooper()

            captureScreen("keyboard_scrolled_to_bottom")
        }

    @Test
    fun multiselect() =
        runTest {
            val browser = getBrowserWithNotes(noteCount = 1)
            browser.longClickRowAtPosition(0).join()
            advanceRobolectricLooper()

            captureScreen("multiselect")
        }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a real device, and overlay a translucent band
     * where the nav bar would sit to see if content is drawn underneath it.
     */
    private fun CardBrowser.simulateNavigationBar() {
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
     * As [simulateNavigationBar], but for landscape with gesture navigation: a short bottom
     * inset, with rounded display corners larger than it.
     */
    private fun CardBrowser.simulateGestureNavigationBar() {
        val navBarHeight = 24.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .apply {
                        val radius = 34.dp.toPx(targetContext)
                        // only the radius is read by the implementation; the center is unused
                        setRoundedCorner(
                            RoundedCornerCompat.POSITION_BOTTOM_LEFT,
                            RoundedCornerCompat(RoundedCornerCompat.POSITION_BOTTOM_LEFT, radius, radius, radius),
                        )
                    }.build()
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
     * is a side inset (wider than the rounded corner it clears), with the camera cutout on the
     * opposite side.
     */
    private fun CardBrowser.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(left = 32.dp))
                    .apply {
                        val radius = 34.dp.toPx(targetContext)
                        // only the radius is read by the implementation; the center is unused
                        setRoundedCorner(
                            RoundedCornerCompat.POSITION_BOTTOM_LEFT,
                            RoundedCornerCompat(RoundedCornerCompat.POSITION_BOTTOM_LEFT, radius, radius, radius),
                        )
                    }.build()
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
