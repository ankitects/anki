// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.Path
import android.view.InputDevice
import android.view.MotionEvent
import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.preferences.reviewer.WhiteboardAction
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.testutils.launchFragmentInContainer
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WhiteboardFragmentTest : RobolectricTest() {
    @Test
    fun `two finger tap clears the whiteboard with finger drawing`() = checkTapClearsWhiteboard(Gesture.TWO_FINGER_TAP, fingerCount = 2)

    @Test
    fun `three finger tap clears the whiteboard with finger drawing`() = checkTapClearsWhiteboard(Gesture.THREE_FINGER_TAP, fingerCount = 3)

    @Test
    fun `four finger tap clears the whiteboard with finger drawing`() = checkTapClearsWhiteboard(Gesture.FOUR_FINGER_TAP, fingerCount = 4)

    @Test
    fun `single finger input does not draw in stylus mode`() {
        WhiteboardRepository(getPreferences()).stylusOnlyMode = true
        withWhiteboard { fragment ->
            fragment.dispatchFingerTouch(MotionEvent.ACTION_DOWN, 1)
            fragment.dispatchFingerTouch(MotionEvent.ACTION_MOVE, 1)
            fragment.dispatchFingerTouch(MotionEvent.ACTION_UP, 1)
            assertEquals(emptyList(), fragment.viewModel.paths.value)
        }
    }

    @Test
    fun `hiding toolbar does not crash after view is destroyed`() {
        changeToolbarVisibilityBeforeDestroyingView(isShown = false)
    }

    @Test
    fun `showing toolbar does not crash after view is destroyed`() {
        changeToolbarVisibilityBeforeDestroyingView(isShown = true)
    }

    @Test
    fun `toolbar visibility is no longer observed after view is destroyed`() {
        withWhiteboard { fragment ->
            val viewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
            fragment.parentFragmentManager.commitNow { detach(fragment) }
            assertNull(fragment.view)

            viewModel.setIsToolbarShown(!viewModel.isToolbarShown.value)
        }
    }

    @Test
    fun `recreated view restores brush when stylus button is no longer pressed`() {
        launchFragmentInContainer<WhiteboardFragment>().use { scenario ->
            lateinit var retainedViewModel: WhiteboardViewModel
            scenario.onFragment { fragment ->
                retainedViewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
                val view = fragment.binding.whiteboardView
                view.dispatchStylusTouch(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_STYLUS_PRIMARY)
                view.dispatchStylusTouch(MotionEvent.ACTION_UP, MotionEvent.BUTTON_STYLUS_PRIMARY)
                assertIs<WhiteboardTool.Eraser>(retainedViewModel.activeTool.value)
            }

            // The button is released while the old view is unavailable to receive the event.
            scenario.recreate()
            scenario.onFragment { fragment ->
                assertSame(retainedViewModel, ViewModelProvider(fragment)[WhiteboardViewModel::class.java])
                val view = fragment.binding.whiteboardView
                view.dispatchStylusTouch(MotionEvent.ACTION_DOWN)
                assertIs<WhiteboardTool.Brush>(view.activeTool)
            }
        }
    }

    @Test
    fun `recreated view restores brush when stylus button is released mid stroke`() {
        launchFragmentInContainer<WhiteboardFragment>().use { scenario ->
            lateinit var retainedViewModel: WhiteboardViewModel
            scenario.onFragment { fragment ->
                retainedViewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
                val view = fragment.binding.whiteboardView
                view.dispatchStylusTouch(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_STYLUS_PRIMARY)
                view.dispatchStylusTouch(MotionEvent.ACTION_UP, MotionEvent.BUTTON_STYLUS_PRIMARY)
                assertIs<WhiteboardTool.Eraser>(retainedViewModel.activeTool.value)
            }

            // The button stays held while the activity is recreated and the next stroke begins.
            scenario.recreate()
            scenario.onFragment { fragment ->
                assertSame(retainedViewModel, ViewModelProvider(fragment)[WhiteboardViewModel::class.java])
                val view = fragment.binding.whiteboardView
                view.dispatchStylusTouch(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_STYLUS_PRIMARY)
                assertIs<WhiteboardTool.Eraser>(view.activeTool)

                view.dispatchStylusTouch(MotionEvent.ACTION_MOVE)
                assertIs<WhiteboardTool.Brush>(view.activeTool, "Releasing the button should restore the brush during this stroke")
            }
        }
    }

    @Test
    fun `selecting an identical brush updates toolbar selection`() {
        launchFragmentInContainer<WhiteboardFragment>().use { scenario ->
            scenario.onFragment { fragment ->
                val viewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
                viewModel.addBrush(viewModel.currentBrushColor)
                assertEquals(viewModel.brushes.value[0], viewModel.brushes.value[1])
            }
            advanceRobolectricLooper()
            scenario.onFragment { fragment ->
                ViewModelProvider(fragment)[WhiteboardViewModel::class.java].setActiveBrush(0)
            }
            advanceRobolectricLooper()
            scenario.onFragment { fragment ->
                assertTrue(fragment.isBrushSelected(0), "The first brush should initially be selected")
                ViewModelProvider(fragment)[WhiteboardViewModel::class.java].setActiveBrush(1)
            }
            advanceRobolectricLooper()
            scenario.onFragment { fragment ->
                assertTrue(fragment.isBrushSelected(1), "The second brush should now be selected")
                assertFalse(fragment.isBrushSelected(0), "The first brush should no longer be selected")
            }
        }
    }

    private fun checkTapClearsWhiteboard(
        gesture: Gesture,
        fingerCount: Int,
    ) {
        editPreferences {
            putString(WhiteboardAction.CLEAR.preferenceKey, listOf(ReviewerBinding.fromGesture(gesture)).toPreferenceString())
        }
        WhiteboardRepository(getPreferences()).stylusOnlyMode = false
        withWhiteboard { fragment ->
            val view = fragment.binding.whiteboardView
            assertFalse(view.isStylusOnlyMode)
            fragment.viewModel.addPath(Path().apply { lineTo(10f, 10f) })
            assertEquals(1, fragment.viewModel.paths.value.size)

            fragment.tapWithFingers(fingerCount)

            assertEquals(emptyList(), fragment.viewModel.paths.value)
            assertFalse(view.isStylusOnlyMode)
            view.dispatchStylusTouch(MotionEvent.ACTION_DOWN)
            view.dispatchStylusTouch(MotionEvent.ACTION_UP)
            assertEquals(1, fragment.viewModel.paths.value.size, "Stylus drawing should still work")
        }
    }

    private fun WhiteboardFragment.tapWithFingers(fingerCount: Int) {
        dispatchFingerTouch(MotionEvent.ACTION_DOWN, 1)
        for (count in 2..fingerCount) {
            dispatchFingerTouch(
                MotionEvent.ACTION_POINTER_DOWN or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                count,
            )
        }
        for (count in fingerCount downTo 2) {
            dispatchFingerTouch(
                MotionEvent.ACTION_POINTER_UP or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                count,
            )
        }
        dispatchFingerTouch(MotionEvent.ACTION_UP, 1)
    }

    private fun WhiteboardFragment.dispatchFingerTouch(
        action: Int,
        pointerCount: Int,
    ) {
        val event =
            MotionEvent.obtain(
                0L,
                0L,
                action,
                pointerCount,
                Array(pointerCount) { index ->
                    MotionEvent.PointerProperties().apply {
                        id = index
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    }
                },
                Array(pointerCount) { index ->
                    MotionEvent.PointerCoords().apply {
                        x = 50f + index * 20f
                        y = 50f
                    }
                },
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        try {
            // Use the parent: rejecting ACTION_DOWN prevents later pointer events reaching the whiteboard.
            binding.root.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun WhiteboardFragment.isBrushSelected(index: Int): Boolean {
        val recycler = binding.whiteboardToolbar.findViewById<RecyclerView>(R.id.brush_recycler_view)
        val adapter = recycler.adapter as BrushAdapter
        // Bind a fresh holder so this assertion does not depend on RecyclerView having laid out.
        val holder = adapter.createViewHolder(recycler, adapter.getItemViewType(index))
        adapter.bindViewHolder(holder, index)
        return (holder.itemView as MaterialButton).isChecked
    }

    private fun WhiteboardView.dispatchStylusTouch(
        action: Int,
        buttonState: Int = 0,
    ) {
        val properties =
            arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_STYLUS
                },
            )
        val coordinates =
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    x = 50f
                    y = 50f
                },
            )
        val event =
            MotionEvent.obtain(
                0L,
                0L,
                action,
                1,
                properties,
                coordinates,
                0,
                buttonState,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_STYLUS,
                0,
            )
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun changeToolbarVisibilityBeforeDestroyingView(isShown: Boolean) {
        WhiteboardRepository(getPreferences()).isToolbarShown = !isShown
        var toolbarUpdated = false
        withWhiteboard { fragment ->
            fragment.binding.whiteboardToolbar.onToolbarVisibilityChanged = { toolbarUpdated = true }
            val viewModel = ViewModelProvider(fragment)[WhiteboardViewModel::class.java]
            viewModel.setIsToolbarShown(isShown)

            // Destroy the view before the posted toolbar update can run.
            fragment.parentFragmentManager.commitNow { detach(fragment) }
            assertNull(fragment.view)
        }
        assertFalse(toolbarUpdated, "the destroyed toolbar should not be updated")
    }

    private fun withWhiteboard(block: (WhiteboardFragment) -> Unit) {
        launchFragmentInContainer<WhiteboardFragment>().use { scenario ->
            scenario.onFragment { fragment -> block(fragment) }
            advanceRobolectricLooper()
        }
    }
}
