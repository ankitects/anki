// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.ichi2.anki.CommonString

/**
 * https://www.fileformat.info/info/unicode/char/235d/index.htm (similar to a finger)
 * Supported on API 23
 */
const val GESTURE_PREFIX = "\u235D"

/**
 * https://www.fileformat.info/info/unicode/char/1fa87/index.htm (Maracas)
 */
// #17090: maracas emoji is unusable on API 30 or below.
// androidX emoji2 doesn't work by default on an API 30 emulator.
// either requires a GMS dependency, or bloats the APK size by 9.8MB
val SHAKE_GESTURE_PREFIX = if (Build.VERSION.SDK_INT > 30) "\uD83E\uDE87" else ""

fun interface GestureListener {
    fun onGesture(gesture: Gesture)
}

enum class Gesture(
    @get:JvmName("getResourceId") val resourceId: Int,
    private val displayPrefix: String = GESTURE_PREFIX,
) {
    SWIPE_UP(CommonString.gestures_swipe_up),
    SWIPE_DOWN(CommonString.gestures_swipe_down),
    SWIPE_LEFT(CommonString.gestures_swipe_left),
    SWIPE_RIGHT(CommonString.gestures_swipe_right),
    DOUBLE_TAP(CommonString.gestures_double_tap),
    TAP_TOP_LEFT(CommonString.gestures_corner_tap_top_left),
    TAP_TOP(CommonString.gestures_tap_top),
    TAP_TOP_RIGHT(CommonString.gestures_corner_tap_top_right),
    TAP_LEFT(CommonString.gestures_tap_left),
    TAP_CENTER(CommonString.gestures_corner_tap_middle_center),
    TAP_RIGHT(CommonString.gestures_tap_right),
    TAP_BOTTOM_LEFT(CommonString.gestures_corner_tap_bottom_left),
    TAP_BOTTOM(CommonString.gestures_tap_bottom),
    TAP_BOTTOM_RIGHT(CommonString.gestures_corner_tap_bottom_right),
    TWO_FINGER_TAP(CommonString.gestures_two_finger_tap),
    THREE_FINGER_TAP(CommonString.gestures_three_finger_tap),
    FOUR_FINGER_TAP(CommonString.gestures_four_finger_tap),
    SHAKE(CommonString.gestures_shake, SHAKE_GESTURE_PREFIX),
    ;

    fun toDisplayString(context: Context): String =
        if (displayPrefix.isNotEmpty()) {
            displayPrefix + ' ' + context.getString(resourceId)
            // e.g., (maracas emoji) + (space) + "Shake device"
        } else {
            context.getString(resourceId)
            // e.g., "Shake device"
            // (Not only the empty prefix (""), but also the space in the middle (" ") is not shown.)
            // Related to #17090
        }
}

/**
 * How the screen is segmented for tap gestures.
 * The modes are incompatible ([NINE_POINT] defines points which are ambiguous in [FOUR_POINT]).
 * @see FOUR_POINT
 */
enum class TapGestureMode {
    /**
     * The cardinal directions: up, down, left & right.
     * Draw a line from corner to corner diagonally, each touch target fully handles the
     * edge which it is associated with
     * four-point and nine-point are thus incompatible because the four-point center and corners
     * are ambiguous in a nine-point system and thus not interchangeable
     */
    FOUR_POINT,

    /**
     * Divide the screen into 9 equally sized squares for touch targets.
     * Better for tablets
     * See: #7537
     */
    NINE_POINT,

    ;

    companion object {
        // TODO replace with `Prefs.tapGestureMode`
        fun fromPreference(preferences: SharedPreferences): TapGestureMode =
            when (preferences.getBoolean("gestureCornerTouch", false)) {
                true -> NINE_POINT
                false -> FOUR_POINT
            }
    }
}
