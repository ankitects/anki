// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiTouchDetectorTest {
    private lateinit var detector: MultiTouchDetector
    private val mockScrollListener: OnScrollByListener = mock()
    private val mockTouchListener: OnMultiTouchListener = mock()

    @Before
    fun setup() {
        detector = MultiTouchDetector(touchSlop = TOUCH_SLOP)
        detector.setOnScrollByListener(mockScrollListener)
        detector.setOnMultiTouchListener(mockTouchListener)
    }

    @Test
    fun `should ignore single touch events`() {
        val event =
            createMotionEvent(
                action = MotionEvent.ACTION_DOWN,
                count = 1,
                y1 = 100f,
                y2 = 0f,
            )

        val handled = detector.onTouchEvent(event)

        assertFalse("Detector should return false for single pointer", handled)
        verify(mockScrollListener, never()).onVerticalScrollBy(any())
        verify(mockTouchListener, never()).onMultiTouch(any())
    }

    @Test
    fun `should detect multi-touch tap when movement is within slop`() {
        val downEvent = createMotionEvent(MotionEvent.ACTION_POINTER_DOWN, 2, 100f, 100f)
        detector.onTouchEvent(downEvent)

        // move within slop of 10px so scroll isn't triggered
        val moveEvent = createMotionEvent(MotionEvent.ACTION_MOVE, 2, 105f, 105f)
        detector.onTouchEvent(moveEvent)

        val upEvent = createMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 105f, 105f)
        val handled = detector.onTouchEvent(upEvent)

        assertTrue(handled)
        verify(mockScrollListener, never()).onVerticalScrollBy(any())
        verify(mockTouchListener).onMultiTouch(2)
    }

    @Test
    fun `should detect vertical scroll when movement exceeds slop`() {
        val downEvent = createMotionEvent(MotionEvent.ACTION_POINTER_DOWN, 2, 100f, 100f)
        detector.onTouchEvent(downEvent)

        val moveEvent = createMotionEvent(MotionEvent.ACTION_MOVE, 2, 50f, 50f)
        val handled = detector.onTouchEvent(moveEvent)

        assertTrue(handled)
        verify(mockScrollListener).onVerticalScrollBy(50)

        val upEvent = createMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 50f, 50f)
        detector.onTouchEvent(upEvent)

        verify(mockTouchListener, never()).onMultiTouch(any())
    }

    @Test
    fun `should reset tap tolerance when a 3rd finger is pressed`() {
        val event2Fingers = createMotionEvent(MotionEvent.ACTION_POINTER_DOWN, 2, 100f, 100f)
        detector.onTouchEvent(event2Fingers)

        val event3Fingers = createMotionEvent(MotionEvent.ACTION_POINTER_DOWN, 3, 100f, 100f)
        val handled = detector.onTouchEvent(event3Fingers)

        assertTrue(handled)

        val upEvent = createMotionEvent(MotionEvent.ACTION_POINTER_UP, 3, 100f, 100f)
        detector.onTouchEvent(upEvent)

        verify(mockTouchListener).onMultiTouch(3)
    }

    @Test
    fun `should not cascade events on pointer up`() {
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_POINTER_DOWN, 2, 100f, 100f))
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 100f, 100f))
        detector.onTouchEvent(createMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 100f, 100f))

        verify(mockTouchListener, org.mockito.Mockito.times(1)).onMultiTouch(any())
    }

    private fun createMotionEvent(
        action: Int,
        count: Int,
        y1: Float,
        y2: Float,
    ): MotionEvent {
        val p1 =
            PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        val p2 =
            PointerProperties().apply {
                id = 1
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        val p3 =
            PointerProperties().apply {
                id = 2
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }

        val c1 =
            PointerCoords().apply {
                x = 10f
                y = y1
                pressure = 1f
                size = 1f
            }
        val c2 =
            PointerCoords().apply {
                x = 20f
                y = y2
                pressure = 1f
                size = 1f
            }
        val c3 =
            PointerCoords().apply {
                x = 10f
                y = y1
                pressure = 1f
                size = 1f
            }

        val properties = arrayOf(p1, p2, p3).take(count).toTypedArray()
        val coordinates = arrayOf(c1, c2, c3).take(count).toTypedArray()

        return MotionEvent.obtain(
            0L,
            0L,
            action,
            count,
            properties,
            coordinates,
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            0,
            0,
        )
    }

    companion object {
        private const val TOUCH_SLOP = 10
    }
}
