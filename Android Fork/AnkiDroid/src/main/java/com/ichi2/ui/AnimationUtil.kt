// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation

object AnimationUtil {
    // please test this on Huawei devices (if possible) and not just on the emulator -
    // having view.setVisibility(View.VISIBLE); on the expand worked fine on the emulator, but looked bad on my phone

    /** This is a fast animation - We don't want the user incorrectly selecting the current position
     * for the next collapse operation  */
    private const val DURATION_MILLIS = 200

    fun collapseView(
        view: View,
        animationEnabled: Boolean,
    ) {
        view.animate().cancel()
        if (!animationEnabled) {
            view.visibility = View.GONE
            return
        }
        val set = AnimationSet(true)
        val expandAnimation =
            ScaleAnimation(
                1f,
                1f,
                1f,
                0.5f,
            )
        expandAnimation.duration = DURATION_MILLIS.toLong()
        expandAnimation.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}

                override fun onAnimationEnd(animation: Animation) {
                    view.visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: Animation) {}
            },
        )
        val alphaAnimation = AlphaAnimation(1.0f, 0f)
        alphaAnimation.duration = DURATION_MILLIS.toLong()
        alphaAnimation.fillAfter = true
        set.addAnimation(expandAnimation)
        set.addAnimation(alphaAnimation)
        view.startAnimation(set)
    }

    fun expandView(
        view: View,
        enableAnimation: Boolean,
    ) {
        view.animate().cancel()
        if (!enableAnimation) {
            view.visibility = View.VISIBLE
            view.alpha = 1.0f
            view.scaleY = 1.0f
            return
        }

        // Sadly this seems necessary - yScale didn't work.
        val set = AnimationSet(true)
        val resetEditTextScale =
            ScaleAnimation(
                1f,
                1f,
                1f,
                1f,
            )
        resetEditTextScale.duration = DURATION_MILLIS.toLong()
        val alphaAnimation = AlphaAnimation(0.0f, 1.0f)
        alphaAnimation.fillAfter = true
        alphaAnimation.duration = DURATION_MILLIS.toLong()
        set.addAnimation(resetEditTextScale)
        set.addAnimation(alphaAnimation)
        view.startAnimation(set)
        view.visibility = View.VISIBLE
    }
}
