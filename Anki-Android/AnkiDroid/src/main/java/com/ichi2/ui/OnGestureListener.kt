/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.ui

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.reviewer.GestureMapper
import java.util.function.Consumer

/** Simplifies and adapts a [android.view.GestureDetector.SimpleOnGestureListener]
 * to a consumer accepting a [com.ichi2.anki.cardviewer.Gesture]  */
class OnGestureListener(
    private val view: View,
    private val gestureMapper: GestureMapper,
    private val consumer: Consumer<Gesture>,
) : GestureDetector.SimpleOnGestureListener() {
    fun getTapGestureMode() = gestureMapper.tapGestureMode

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onDoubleTap(e: MotionEvent): Boolean {
        consumer.accept(Gesture.DOUBLE_TAP)
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        if (e1 != null) {
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            val gesture =
                gestureMapper.gesture(
                    dx,
                    dy,
                    velocityX,
                    velocityY,
                    isSelecting = false,
                    isXScrolling = false,
                    isYScrolling = false,
                )
            if (gesture != null) {
                consumer.accept(gesture)
            }
        }
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        // TODO: There is visual latency here. We can do better.
        // It's better UX if we detect a single tap and it turns into a double tap later
        // But, the order on my Android 11 is: down, double tap, down
        // and the events aren't equal, which makes this difficult.
        val height = view.height
        val width = view.width
        val gesture = gestureMapper.gesture(height, width, e.x, e.y)
        if (gesture != null) {
            consumer.accept(gesture)
        }
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        // selecting stuff in a WebView takes priority over gesture detection
        // so, do nothing in the method
    }

    companion object {
        fun createInstance(
            view: View,
            consumer: Consumer<Gesture>,
        ): OnGestureListener {
            val gestureMapper = GestureMapper()
            gestureMapper.init(view.context.sharedPrefs())
            return OnGestureListener(view, gestureMapper, consumer)
        }
    }
}
