/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.GestureMapper
import com.ichi2.anki.reviewer.ReviewerBinding

class GestureProcessor(
    private val processor: ViewerCommand.CommandProcessor?,
) {
    companion object {
        const val PREF_KEY = "gestures"
    }

    private var gestureDoubleTap: ViewerCommand? = null
    private var gestureSwipeUp: ViewerCommand? = null
    private var gestureSwipeDown: ViewerCommand? = null
    private var gestureSwipeLeft: ViewerCommand? = null
    private var gestureSwipeRight: ViewerCommand? = null
    private var gestureTapLeft: ViewerCommand? = null
    private var gestureTapRight: ViewerCommand? = null
    private var gestureTapTop: ViewerCommand? = null
    private var gestureTapBottom: ViewerCommand? = null
    private var gestureTapTopLeft: ViewerCommand? = null
    private var gestureTapTopRight: ViewerCommand? = null
    private var gestureTapCenter: ViewerCommand? = null
    private var gestureTapBottomLeft: ViewerCommand? = null
    private var gestureTapBottomRight: ViewerCommand? = null
    private var gestureShake: ViewerCommand? = null
    private val gestureMapper = GestureMapper()

    /**
     * Whether the class has been enabled.
     * This requires the "gestures" preference is enabled,
     * and [GestureProcessor.init] has been called
     */
    var isEnabled = false
        private set

    fun init(preferences: SharedPreferences) {
        isEnabled = preferences.getBoolean(PREF_KEY, false)

        val associatedCommands = HashMap<Gesture, ViewerCommand>()
        for (command in ViewerCommand.entries) {
            for (mappableBinding in ReviewerBinding.fromPreference(preferences, command)) {
                if (mappableBinding.binding is Binding.GestureInput) {
                    associatedCommands[mappableBinding.binding.gesture] = command
                }
            }
        }
        gestureDoubleTap = associatedCommands[Gesture.DOUBLE_TAP]
        gestureSwipeUp = associatedCommands[Gesture.SWIPE_UP]
        gestureSwipeDown = associatedCommands[Gesture.SWIPE_DOWN]
        gestureSwipeLeft = associatedCommands[Gesture.SWIPE_LEFT]
        gestureSwipeRight = associatedCommands[Gesture.SWIPE_RIGHT]
        gestureMapper.init(preferences)
        gestureTapLeft = associatedCommands[Gesture.TAP_LEFT]
        gestureTapRight = associatedCommands[Gesture.TAP_RIGHT]
        gestureTapTop = associatedCommands[Gesture.TAP_TOP]
        gestureTapBottom = associatedCommands[Gesture.TAP_BOTTOM]
        gestureShake = associatedCommands[Gesture.SHAKE]

        val useCornerTouch = preferences.getBoolean("gestureCornerTouch", false)
        if (useCornerTouch) {
            gestureTapTopLeft = associatedCommands[Gesture.TAP_TOP_LEFT]
            gestureTapTopRight = associatedCommands[Gesture.TAP_TOP_RIGHT]
            gestureTapCenter = associatedCommands[Gesture.TAP_CENTER]
            gestureTapBottomLeft = associatedCommands[Gesture.TAP_BOTTOM_LEFT]
            gestureTapBottomRight = associatedCommands[Gesture.TAP_BOTTOM_RIGHT]
        }
    }

    fun onTap(
        height: Int,
        width: Int,
        posX: Float,
        posY: Float,
    ): Boolean? {
        val gesture = gestureMapper.gesture(height, width, posX, posY) ?: return false
        return execute(gesture)
    }

    fun onDoubleTap(): Boolean? = execute(Gesture.DOUBLE_TAP)

    fun onFling(
        dx: Float,
        dy: Float,
        velocityX: Float,
        velocityY: Float,
        isSelecting: Boolean,
        isXScrolling: Boolean,
        isYScrolling: Boolean,
    ): Boolean? {
        val gesture = gestureMapper.gesture(dx, dy, velocityX, velocityY, isSelecting, isXScrolling, isYScrolling)
        return execute(gesture)
    }

    fun onShake(): Boolean? = execute(Gesture.SHAKE)

    private fun execute(gesture: Gesture?): Boolean? {
        val command = gesture?.let { mapGestureToCommand(it) } ?: return false
        return processor?.executeCommand(command, gesture)
    }

    private fun mapGestureToCommand(gesture: Gesture): ViewerCommand? =
        when (gesture) {
            Gesture.SWIPE_UP -> gestureSwipeUp
            Gesture.SWIPE_DOWN -> gestureSwipeDown
            Gesture.SWIPE_LEFT -> gestureSwipeLeft
            Gesture.SWIPE_RIGHT -> gestureSwipeRight
            Gesture.TAP_TOP -> gestureTapTop
            Gesture.TAP_TOP_LEFT -> gestureTapTopLeft
            Gesture.TAP_TOP_RIGHT -> gestureTapTopRight
            Gesture.TAP_LEFT -> gestureTapLeft
            Gesture.TAP_CENTER -> gestureTapCenter
            Gesture.TAP_RIGHT -> gestureTapRight
            Gesture.TAP_BOTTOM -> gestureTapBottom
            Gesture.TAP_BOTTOM_LEFT -> gestureTapBottomLeft
            Gesture.TAP_BOTTOM_RIGHT -> gestureTapBottomRight
            Gesture.DOUBLE_TAP -> gestureDoubleTap
            Gesture.SHAKE -> gestureShake
            // Restricted to the new study screen
            Gesture.TWO_FINGER_TAP -> null
            Gesture.THREE_FINGER_TAP -> null
            Gesture.FOUR_FINGER_TAP -> null
        }

    /**
     * Whether one of the provided gestures is bound
     * @param gestures the gestures to check
     * @return `false` if none of the gestures are bound. `true` otherwise
     */
    fun isBound(vararg gestures: Gesture): Boolean {
        if (!isEnabled) return false

        return gestures.any { mapGestureToCommand(it) != null }
    }
}
