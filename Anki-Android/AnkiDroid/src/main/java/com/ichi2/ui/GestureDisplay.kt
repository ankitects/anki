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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.Gesture.SWIPE_DOWN
import com.ichi2.anki.cardviewer.Gesture.SWIPE_LEFT
import com.ichi2.anki.cardviewer.Gesture.SWIPE_RIGHT
import com.ichi2.anki.cardviewer.Gesture.SWIPE_UP
import com.ichi2.anki.cardviewer.Gesture.TAP_BOTTOM
import com.ichi2.anki.cardviewer.Gesture.TAP_BOTTOM_LEFT
import com.ichi2.anki.cardviewer.Gesture.TAP_BOTTOM_RIGHT
import com.ichi2.anki.cardviewer.Gesture.TAP_CENTER
import com.ichi2.anki.cardviewer.Gesture.TAP_LEFT
import com.ichi2.anki.cardviewer.Gesture.TAP_RIGHT
import com.ichi2.anki.cardviewer.Gesture.TAP_TOP
import com.ichi2.anki.cardviewer.Gesture.TAP_TOP_LEFT
import com.ichi2.anki.cardviewer.Gesture.TAP_TOP_RIGHT
import com.ichi2.anki.cardviewer.GestureListener
import com.ichi2.anki.cardviewer.TapGestureMode
import com.ichi2.anki.databinding.ViewGestureDisplayBinding
import com.ichi2.anki.settings.Prefs
import timber.log.Timber

/** Allows selection, and display of a single gesture on a square grid
 * Supports swipes and a 9-point touch mode
 *
 * Note: Swipes are not displayed on < API 25 due to issues with <layer-list> display.
 *
 * Currently used by [GesturePicker]
 */
class GestureDisplay
    @JvmOverloads // fixes: Error inflating class com.ichi2.ui.GestureDisplay
    constructor(
        context: Context,
        attributeSet: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : ConstraintLayout(context, attributeSet, defStyleAttr) {
        private val binding = ViewGestureDisplayBinding.inflate(LayoutInflater.from(context), this)

        /** Converts a touch event into a call to [setGesture] */
        private val detector: GestureDetector

        /** "Gesture Changed" callback, invoked if the gesture is changed and non-null */
        private var onGestureChangeListener: GestureListener? = null

        /** see [TapGestureMode] */
        private val tapGestureMode: TapGestureMode

        /** The last recorded gesture (null if no gestures provided, or if explicitly set)  */
        private var gesture: Gesture? = null

        init {
            val listener = OnGestureListener.createInstance(this, this::setGesture)
            detector = GestureDetector(context, listener)
            tapGestureMode = listener.getTapGestureMode()
            setTapGestureMode(tapGestureMode)
            // if we don't call mutate, state is persisted outside the dialog when we call .setImageLevel
            binding.swipeView.drawable?.mutate()
        }

        /** Lists all selectable gestures from this view (excludes null) */
        fun availableValues(): List<Gesture> =
            Gesture.entries
                .filter {
                    (tapGestureMode == TapGestureMode.NINE_POINT || !NINE_POINT_TAP_GESTURES.contains(it)) &&
                        (Prefs.isNewStudyScreenEnabled || !MULTI_FINGER_GESTURES.contains(it))
                }

        /** Sets a callback which is called when the gesture is changed, and non-null */
        fun setGestureChangedListener(listener: GestureListener) {
            onGestureChangeListener = listener
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event) || super.onTouchEvent(event)

        fun getGesture() = gesture

        /** Updates the UI from a new gesture
         * fires the "Gesture Changed" event if the gesture has changed and is non-null
         */
        fun setGesture(newGesture: Gesture?) {
            Timber.d("gesture: %s", newGesture?.toDisplayString(context))

            if (gesture == newGesture) {
                Timber.d("Ignoring nop gesture change")
                return
            }

            handleTapChange(newGesture, gesture)
            handleSwipeChange(newGesture)

            this.gesture = newGesture

            if (newGesture == null) return

            onGestureChangeListener?.onGesture(newGesture)
        }

        /**
         * Sets the "swipe" view to the provided swipe (or none if the gesture is null or non-swipe])
         * Only works on API 25+ due to issues with layer-list
         */
        private fun handleSwipeChange(gesture: Gesture?) {
            val level =
                when (gesture) {
                    SWIPE_UP -> 1
                    SWIPE_DOWN -> 2
                    SWIPE_LEFT -> 3
                    SWIPE_RIGHT -> 4
                    else -> 0
                }
            binding.swipeView.setImageLevel(level)
        }

        /**
         * Updates the tap UI (via <selector> and android_selected)
         */
        private fun handleTapChange(
            gesture: Gesture?,
            oldGesture: Gesture?,
        ) {
            // revert the old change, and implement the new change
            // does nothing if neither are taps
            binding.tapGestureToView(oldGesture)?.isSelected = false
            binding.tapGestureToView(gesture)?.isSelected = true
        }

        /**
         * Maps from a [Gesture] to an [ImageView].
         * @return The associated [ImageView], or null if input is null, or isn't a tap gesture
         */
        private fun ViewGestureDisplayBinding.tapGestureToView(gesture: Gesture?): ImageView? =
            when (gesture) {
                TAP_TOP_LEFT -> topLeft
                TAP_TOP -> topCenter
                TAP_TOP_RIGHT -> topRight
                TAP_LEFT -> left
                TAP_CENTER -> center
                TAP_RIGHT -> right
                TAP_BOTTOM_LEFT -> bottomLeft
                TAP_BOTTOM -> bottomCenter
                TAP_BOTTOM_RIGHT -> bottomRight
                else -> null
            }

        /**
         * If we are using 4-point (corner to corner) gestures, hide the 9-point (square-based) gestures
         */
        private fun setTapGestureMode(tapGestureMode: TapGestureMode) {
            val ninePointVisibility =
                when (tapGestureMode) {
                    TapGestureMode.FOUR_POINT -> View.GONE
                    TapGestureMode.NINE_POINT -> View.VISIBLE
                }

            NINE_POINT_TAP_GESTURES.forEach { gesture ->
                binding.tapGestureToView(gesture)?.visibility = ninePointVisibility
            }
        }

        companion object {
            val MULTI_FINGER_GESTURES = listOf(Gesture.TWO_FINGER_TAP, Gesture.THREE_FINGER_TAP, Gesture.FOUR_FINGER_TAP)

            val NINE_POINT_TAP_GESTURES = listOf(TAP_TOP_LEFT, TAP_TOP_RIGHT, TAP_CENTER, TAP_BOTTOM_LEFT, TAP_BOTTOM_RIGHT)
        }
    }
