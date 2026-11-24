// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.withCardBrowser
import com.ichi2.testutils.scrollToEnd
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.testutils.windowInsetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Edge-to-edge regressions for the full-screen Manage columns dialog (Issue 22092).
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.browser.BrowserColumnSelectionFragmentScreenshotTest"`
 */
class BrowserColumnSelectionFragmentScreenshotTest : ScreenshotTest() {
    @Test
    fun portrait() =
        captureColumns(
            "portrait",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp) },
        )

    @Test
    fun gestureNavigation() =
        captureColumns(
            "gesture_navigation",
            with(targetContext) { windowInsetsOf(navBarBottom = 24.dp) },
        )

    @Test
    fun portraitCutout() =
        captureColumns(
            "portrait_cutout",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp, cutoutTop = 40.dp) },
        )

    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        captureColumns(
            "landscape",
            with(targetContext) { windowInsetsOf(navBarRight = 48.dp, cutoutLeft = 32.dp) },
        )
    }

    /** Captures the toolbar on opening, then the final column's clearance when fully scrolled. */
    private fun captureColumns(
        name: String,
        insets: WindowInsetsCompat,
    ) = withCardBrowser(noteCount = 1) { browser ->
        // The backend uses the real clock; stabilize the created and modified column previews.
        val timestamp = 1_594_105_200L
        col.db.execute("update notes set id = ?, mod = ?", timestamp * 1000, timestamp)
        col.db.execute("update cards set nid = ?, mod = ?", timestamp * 1000, timestamp)

        val fragment = BrowserColumnSelectionFragment.createInstance(CardsOrNotes.CARDS)
        fragment.showNow(browser.supportFragmentManager, "columns")
        advanceRobolectricLooper()
        try {
            fragment.requireDialog().window!!.simulateSystemBars(insets)
            captureScreen(name)

            fragment.requireView().findViewById<RecyclerView>(R.id.recycler_view).scrollToEnd()
            captureScreen("${name}_scrolled_to_bottom")
        } finally {
            fragment.dismissNow()
        }
    }
}
