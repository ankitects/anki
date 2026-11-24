// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.graphics.Path
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardRepository
import com.ichi2.testutils.mockWebResourceRequest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.reflect.jvm.jvmName
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReviewerWhiteboardGestureTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `two finger tap clears the whiteboard in stylus mode`() = checkTapClearsWhiteboard(fingerCount = 2)

    @Test
    fun `three finger tap clears the whiteboard in stylus mode`() = checkTapClearsWhiteboard(fingerCount = 3)

    @Test
    fun `four finger tap clears the whiteboard in stylus mode`() = checkTapClearsWhiteboard(fingerCount = 4)

    @Test
    fun `unbound whiteboard gesture falls back to reviewer`() =
        runTest {
            withWhiteboard(bindClear = false) {
                sendMultiFingerTap(2)
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)
            }
        }

    @Test
    fun `hidden whiteboard does not consume reviewer gestures`() =
        runTest {
            withWhiteboard { whiteboard ->
                viewModel.executeAction(ViewerAction.TOGGLE_WHITEBOARD)
                advanceUntilIdle()
                advanceRobolectricLooper()
                assertTrue(whiteboard.isHidden)

                sendMultiFingerTap(2)
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)
            }
        }

    private fun checkTapClearsWhiteboard(fingerCount: Int) =
        runTest {
            withWhiteboard { whiteboard ->
                whiteboard.viewModel.addPath(Path().apply { lineTo(10f, 10f) })
                assertEquals(1, whiteboard.viewModel.paths.value.size)

                sendMultiFingerTap(fingerCount)
                advanceUntilIdle()

                assertEquals(emptyList(), whiteboard.viewModel.paths.value)
                assertTrue(whiteboard.binding.whiteboardView.isStylusOnlyMode)
                assertFalse(viewModel.showingAnswer.value, "Whiteboard bindings must take priority over reviewer bindings")
            }
        }

    private fun ReviewerFragment.sendMultiFingerTap(fingerCount: Int) {
        val webView = assertIs<WebView>(binding.webViewLayout.getChildAt(0))
        // Robolectric does not execute the touch-detection JavaScript: enter through its URL callback.
        val request = mockWebResourceRequest("gesture://multiFingerTap/?touchCount=$fingerCount")
        assertTrue(shadowOf(webView).webViewClient.shouldOverrideUrlLoading(webView, request))
    }

    private fun configureBindings(bindClear: Boolean) {
        val bindings =
            listOf(Gesture.TWO_FINGER_TAP, Gesture.THREE_FINGER_TAP, Gesture.FOUR_FINGER_TAP)
                .map { ReviewerBinding.fromGesture(it) }
                .toPreferenceString()
        editPreferences {
            putString(ViewerAction.SHOW_ANSWER.preferenceKey, bindings)
            if (bindClear) putString(WhiteboardAction.CLEAR.preferenceKey, bindings)
        }
        StudyScreenRepository().isWhiteboardEnabled = true
        WhiteboardRepository(getPreferences()).stylusOnlyMode = true
    }

    private suspend fun TestScope.withWhiteboard(
        bindClear: Boolean = true,
        block: suspend ReviewerFragment.(WhiteboardFragment) -> Unit,
    ) {
        configureBindings(bindClear)
        addBasicNote()
        val intent = ReviewerFragment.getIntent(targetContext)
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            controller.setup().windowFocusChanged(true)
            advanceUntilIdle()
            advanceRobolectricLooper()
            val reviewer = assertIs<ReviewerFragment>(controller.get().fragment)
            reviewer.viewModel.onPageFinished(false)
            advanceUntilIdle()
            val whiteboard =
                assertIs<WhiteboardFragment>(reviewer.childFragmentManager.findFragmentByTag(WhiteboardFragment::class.jvmName))
            assertTrue(whiteboard.binding.whiteboardView.isStylusOnlyMode)
            assertFalse(reviewer.viewModel.showingAnswer.value)
            reviewer.block(whiteboard)
        }
    }
}
