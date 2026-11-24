// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.view.MotionEvent
import kotlin.math.abs

/**
 * Detects multi-finger touch and scroll gestures and triggers a callback with the vertical delta.
 */
class MultiTouchDetector(
    /** Distance in pixels a touch can wander before we think the user is scrolling */
    private val touchSlop: Int,
) {
    private var startX: Float = 0f
    private var startY: Float = 0f
    private var currentX: Float = 0f
    private var currentY: Float = 0f

    private var startX0: Float = 0f
    private var startY0: Float = 0f
    private var startX1: Float = 0f
    private var startY1: Float = 0f

    private var isWithinTapTolerance: Boolean = false
    private var onScrollByListener: OnScrollByListener? = null
    private var onMultiTouchListener: OnMultiTouchListener? = null

    fun setOnScrollByListener(listener: OnScrollByListener) {
        onScrollByListener = listener
    }

    fun setOnMultiTouchListener(listener: OnMultiTouchListener) {
        onMultiTouchListener = listener
    }

    /**
     * Processes the motion event.
     * @return True if the event was handled (consumed), False otherwise.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount < 2) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                reinitialize(event)
                true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (isWithinTapTolerance) {
                    onMultiTouchListener?.onMultiTouch(event.pointerCount)
                    // Prevent cascading events (e.g., 3-finger tap triggering 3 then 2)
                    isWithinTapTolerance = false
                }
                true
            }
            MotionEvent.ACTION_MOVE -> tryScroll(event)
            else -> false
        }
    }

    private fun reinitialize(event: MotionEvent) {
        isWithinTapTolerance = true

        startX0 = event.getX(0)
        startY0 = event.getY(0)
        startX1 = event.getX(1)
        startY1 = event.getY(1)

        startX = (startX0 + startX1) / 2f
        startY = (startY0 + startY1) / 2f
    }

    private fun updatePositions(event: MotionEvent) {
        // Check if any individual finger exceeded the touch slop
        if (isWithinTapTolerance) {
            val dx0 = abs(startX0 - event.getX(0))
            val dy0 = abs(startY0 - event.getY(0))
            val dx1 = abs(startX1 - event.getX(1))
            val dy1 = abs(startY1 - event.getY(1))

            if (dx0 >= touchSlop || dy0 >= touchSlop || dx1 >= touchSlop || dy1 >= touchSlop) {
                isWithinTapTolerance = false
            }
        }

        currentX = (event.getX(0) + event.getX(1)) / 2f
        currentY = (event.getY(0) + event.getY(1)) / 2f
    }

    private fun tryScroll(event: MotionEvent): Boolean {
        updatePositions(event)
        if (isWithinTapTolerance) {
            return false
        }
        val dy = (startY - currentY).toInt()
        if (dy != 0) {
            onScrollByListener?.onVerticalScrollBy(dy)
            startX = currentX
            startY = currentY
        }
        return true
    }
}

fun interface OnScrollByListener {
    /**
     * @param y the amount of pixels to scroll vertically.
     * @see [android.view.View.scrollBy]
     */
    fun onVerticalScrollBy(y: Int)
}

fun interface OnMultiTouchListener {
    /**
     * @param pointerCount the amount of simultaneous touches
     */
    fun onMultiTouch(pointerCount: Int)
}
