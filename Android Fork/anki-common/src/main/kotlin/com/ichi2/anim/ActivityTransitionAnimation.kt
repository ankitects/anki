// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anim

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.LayoutDirection
import androidx.annotation.AnimRes
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.ankicommon.R
import com.ichi2.anki.common.android.animationDisabled
import com.ichi2.anki.common.ui.TransitionDirection
import com.ichi2.anki.common.ui.TransitionDirection.NONE
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper

object ActivityTransitionAnimation {
    /**
     * @param open when `true`, overrides the animation for entering this activity.
     * When `false`, overrides the animation for closing this activity
     */
    fun slide(
        activity: Activity,
        direction: TransitionDirection,
        open: Boolean,
    ) {
        fun overrideTransition(
            @AnimRes enter: Int,
            @AnimRes exit: Int,
        ) {
            CompatHelper.compat.overrideTransition(activity, enter, exit, open)
        }

        when (direction) {
            TransitionDirection.START ->
                if (isRightToLeft(activity)) {
                    overrideTransition(R.anim.slide_right_in, R.anim.slide_right_out)
                } else {
                    overrideTransition(R.anim.slide_left_in, R.anim.slide_left_out)
                }
            TransitionDirection.END ->
                if (isRightToLeft(activity)) {
                    overrideTransition(R.anim.slide_left_in, R.anim.slide_left_out)
                } else {
                    overrideTransition(R.anim.slide_right_in, R.anim.slide_right_out)
                }
            TransitionDirection.RIGHT -> overrideTransition(R.anim.slide_right_in, R.anim.slide_right_out)
            TransitionDirection.LEFT -> overrideTransition(R.anim.slide_left_in, R.anim.slide_left_out)
            TransitionDirection.FADE -> overrideTransition(R.anim.fade_in, R.anim.fade_out)
            TransitionDirection.UP -> overrideTransition(R.anim.slide_up_in, R.anim.slide_up_out)
            TransitionDirection.DOWN -> overrideTransition(R.anim.slide_down_in, R.anim.slide_down_out)
            TransitionDirection.NONE -> overrideTransition(R.anim.none, R.anim.none)
            TransitionDirection.DEFAULT -> { }
        }
    }

    private fun isRightToLeft(c: Context): Boolean = c.resources.configuration.layoutDirection == LayoutDirection.RTL
}

private fun disableIntentAnimation(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
}

/**
 * @param open when `true`, overrides the animation for entering this activity.
 * When `false`, overrides the animation for closing this activity
 */
private fun FragmentActivity.disableActivityAnimation(open: Boolean) {
    ActivityTransitionAnimation.slide(this, NONE, open)
}

@KotlinCleanup("Maybe rename this? This only disables the animation conditionally")
fun FragmentActivity.enableIntentAnimation(intent: Intent) {
    if (animationDisabled()) {
        disableIntentAnimation(intent)
    }
}

/**
 * @param open when `true`, overrides the animation for entering this activity.
 * When `false`, overrides the animation for closing this activity
 */
fun FragmentActivity.enableActivityAnimation(
    animation: TransitionDirection,
    open: Boolean,
) {
    if (animationDisabled()) {
        disableActivityAnimation(open)
    } else {
        ActivityTransitionAnimation.slide(this, animation, open)
    }
}
