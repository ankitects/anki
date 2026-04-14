//noinspection MissingCopyrightHeader #8659

package com.ichi2.anim

import android.app.Activity
import android.content.Context
import android.os.Parcelable
import android.util.LayoutDirection
import androidx.annotation.AnimRes
import com.ichi2.anim.ActivityTransitionAnimation.getInverseTransition
import com.ichi2.anki.R
import com.ichi2.anki.compat.CompatHelper
import kotlinx.parcelize.Parcelize

object ActivityTransitionAnimation {
    /**
     * @param open when `true`, overrides the animation for entering this activity.
     * When `false`, overrides the animation for closing this activity
     */
    fun slide(
        activity: Activity,
        direction: Direction,
        open: Boolean,
    ) {
        fun overrideTransition(
            @AnimRes enter: Int,
            @AnimRes exit: Int,
        ) {
            CompatHelper.compat.overrideTransition(activity, enter, exit, open)
        }

        when (direction) {
            Direction.START ->
                if (isRightToLeft(activity)) {
                    overrideTransition(R.anim.slide_right_in, R.anim.slide_right_out)
                } else {
                    overrideTransition(R.anim.slide_left_in, R.anim.slide_left_out)
                }
            Direction.END ->
                if (isRightToLeft(activity)) {
                    overrideTransition(R.anim.slide_left_in, R.anim.slide_left_out)
                } else {
                    overrideTransition(R.anim.slide_right_in, R.anim.slide_right_out)
                }

            Direction.RIGHT -> overrideTransition(R.anim.slide_right_in, R.anim.slide_right_out)
            Direction.LEFT -> overrideTransition(R.anim.slide_left_in, R.anim.slide_left_out)
            Direction.FADE -> overrideTransition(R.anim.fade_in, R.anim.fade_out)
            Direction.UP -> overrideTransition(R.anim.slide_up_in, R.anim.slide_up_out)
            Direction.DOWN -> overrideTransition(R.anim.slide_down_in, R.anim.slide_down_out)
            Direction.NONE -> overrideTransition(R.anim.none, R.anim.none)
            Direction.DEFAULT -> { }
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
