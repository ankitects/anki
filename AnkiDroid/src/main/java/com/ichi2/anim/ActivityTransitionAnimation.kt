//noinspection MissingCopyrightHeader #8659

package com.ichi2.anim

import android.app.Activity
import android.content.Context
import android.os.Parcelable
import android.util.LayoutDirection
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import com.ichi2.anki.R
import kotlinx.parcelize.Parcelize

object ActivityTransitionAnimation {
    @Suppress("DEPRECATION", "deprecated in API34 for predictive back, must plumb through new open/close parameter")
    fun slide(
        activity: Activity,
        direction: Direction,
    ) {
        when (direction) {
            Direction.START ->
                if (isRightToLeft(activity)) {
                    activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out)
                } else {
                    activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out)
                }
            Direction.END ->
                if (isRightToLeft(activity)) {
                    activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out)
                } else {
                    activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out)
                }
            Direction.RIGHT -> activity.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_right_out)
            Direction.LEFT -> activity.overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out)
            Direction.FADE -> activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            Direction.UP -> activity.overridePendingTransition(R.anim.slide_up_in, R.anim.slide_up_out)
            Direction.DOWN -> activity.overridePendingTransition(R.anim.slide_down_in, R.anim.slide_down_out)
            Direction.NONE -> activity.overridePendingTransition(R.anim.none, R.anim.none)
            Direction.DEFAULT -> {
            }
        }
    }

    private fun isRightToLeft(c: Context): Boolean = c.resources.configuration.layoutDirection == LayoutDirection.RTL

    @Parcelize
    enum class Direction : Parcelable {
        START,
        END,
        FADE,
        UP,
        DOWN,
        RIGHT,
        LEFT,
        DEFAULT,
        NONE,
        ;

        /** @see getInverseTransition */
        fun invert(): Direction = getInverseTransition(this)
    }

    /**
     * @return inverse transition of [direction]
     * if there isn't one, return the same [direction]
     */
    fun getInverseTransition(direction: Direction): Direction =
        when (direction) {
            // Directional transitions which should return their opposites
            Direction.RIGHT -> Direction.LEFT
            Direction.LEFT -> Direction.RIGHT
            Direction.UP -> Direction.DOWN
            Direction.DOWN -> Direction.UP
            Direction.START -> Direction.END
            Direction.END -> Direction.START
            // Non-directional transitions which should return themselves
            Direction.FADE -> Direction.FADE
            Direction.DEFAULT -> Direction.DEFAULT
            Direction.NONE -> Direction.NONE
        }
}
