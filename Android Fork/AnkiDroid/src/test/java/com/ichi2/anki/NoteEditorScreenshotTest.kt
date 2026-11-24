// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.insetsOf
import com.ichi2.testutils.simulateKeyboard
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for [NoteEditorActivity]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.NoteEditorScreenshotTest"`
 */
class NoteEditorScreenshotTest : ScreenshotTest() {
    /** 3-button navigation: a tall bottom inset which the formatting toolbar rests above */
    @Test
    fun noteEditorPortrait() =
        withNoteEditor { activity ->
            activity.simulateNavigationBar()
            captureScreen("portrait")
        }

    /** Gesture navigation: a short inset, but larger rounded corners to clear */
    @Test
    fun noteEditorGestureNavigation() =
        withNoteEditor { activity ->
            activity.simulateGestureNavigationBar()
            captureScreen("gesture_navigation")
        }

    /** As [noteEditorPortrait], but landscape: a side navigation bar and an opposite cutout */
    @Test
    fun noteEditorLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withNoteEditor { activity ->
            activity.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    @Test
    fun `gesture navigation corners do not leak into landscape insets`() {
        RuntimeEnvironment.setQualifiers("+land")
        withNoteEditor { activity ->
            // WindowInsets.Builder shares rounded corners: model a gesture test running first.
            activity.simulateGestureNavigationBar()
            activity.simulateSideNavigationBar()

            val toolbar = activity.noteEditorFragment.binding.editorToolbar.scrollView
            assertThat(toolbar.paddingLeft, equalTo(0))
            assertThat(toolbar.paddingRight, equalTo(0))
        }
    }

    /** The two-pane tablet layout: the editor beside the card previewer */
    @Test
    fun noteEditorTablet() {
        setTabletQualifiers()
        withNoteEditor { activity ->
            activity.simulateNavigationBar()
            captureScreen("tablet")
        }
    }

    /** The formatting toolbar rides above the keyboard */
    @Test
    fun noteEditorKeyboard() =
        withNoteEditor { activity ->
            activity.simulateKeyboard()
            captureScreen("keyboard")
        }

    /** As [noteEditorKeyboard], but two-pane: 'Show answer' also stays above the keyboard */
    @Test
    fun noteEditorTabletKeyboard() {
        setTabletQualifiers()
        withNoteEditor { activity ->
            activity.simulateKeyboard()
            captureScreen("tablet_keyboard")
        }
    }

    @Test
    fun `tablet preview tabs are ready before capture`() {
        setTabletQualifiers()
        withNoteEditor { activity ->
            val tabs = activity.binding.previewerTabLayout!!
            assertThat(tabs.tabCount, equalTo(1))
            assertThat(tabs.getTabAt(0)?.text.toString(), equalTo("Card 1 (empty)"))
            assertThat(tabs.selectedTabPosition, equalTo(0))
        }
    }

    private fun withNoteEditor(block: (NoteEditorActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.AddNote().toIntent(targetContext),
            )
        advanceRobolectricLooper()
        activity.waitForPreviewTabs()
        block(activity)
    }

    private fun NoteEditorActivity.waitForPreviewTabs() {
        if (!isPreviewerVisible) return
        val tabs = binding.previewerTabLayout!!
        // wait for the background card rendering to complete
        advanceRobolectricLooperUntil(
            lazyMessage = { "Note editor preview tabs did not finish loading" },
        ) {
            tabs.tabCount == 1 && tabs.selectedTabPosition == 0
        }
    }

    private fun NoteEditorActivity.simulateNavigationBar() {
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
        addOverlay(FrameLayout.LayoutParams.MATCH_PARENT, navBarHeight.toPx(targetContext), Gravity.BOTTOM)
    }

    /** As [simulateNavigationBar], but gesture navigation: a short inset, larger rounded corners */
    private fun NoteEditorActivity.simulateGestureNavigationBar() {
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
        addOverlay(FrameLayout.LayoutParams.MATCH_PARENT, navBarHeight.toPx(targetContext), Gravity.BOTTOM)
    }

    /** As [simulateNavigationBar], but landscape: a side navigation bar and an opposite cutout */
    private fun NoteEditorActivity.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        // The shared helper also resets rounded corners leaked by other tests.
        dispatchInsets(navBarRight = navBarWidth, cutoutLeft = 32.dp)
        addOverlay(navBarWidth.toPx(targetContext), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END)
    }

    private fun NoteEditorActivity.addOverlay(
        width: Int,
        height: Int,
        gravity: Int,
    ) {
        val decor = window.decorView as ViewGroup
        val overlay = View(this).apply { setBackgroundColor(0x80000000.toInt()) }
        decor.addView(overlay, FrameLayout.LayoutParams(width, height, gravity))
    }
}
