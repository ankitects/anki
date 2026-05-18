/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.AppCompatImageView
import com.ichi2.anki.R
import com.ichi2.anki.utils.AnimUtils
import com.ichi2.anki.utils.postDelayed
import kotlin.time.Duration.Companion.milliseconds

class AnswerFeedbackView : AppCompatImageView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var fadeOutRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Shows the feedback for one second
     * with a quick fade in, brief hold, then gentle fade out.
     */
    fun toggle() {
        clearAnimation()

        fadeOutRunnable?.let {
            handler.removeCallbacks(it)
            fadeOutRunnable = null
        }

        if (!AnimUtils.areAnimationsEnabled(context)) {
            visibility = VISIBLE
            fadeOutRunnable =
                Runnable {
                    visibility = GONE
                    fadeOutRunnable = null
                }.also {
                    handler.postDelayed(it, 800.milliseconds)
                }
            return
        }

        val fadeIn = AnimationUtils.loadAnimation(context, R.anim.answer_feedback_fade_in)
        val fadeOut = AnimationUtils.loadAnimation(context, R.anim.answer_feedback_fade_out)

        fadeIn.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    visibility = VISIBLE
                }

                override fun onAnimationEnd(animation: Animation) {
                    fadeOutRunnable =
                        Runnable {
                            startAnimation(fadeOut)
                        }.also {
                            handler.postDelayed(it, 400)
                        }
                }

                override fun onAnimationRepeat(animation: Animation) {}
            },
        )
        fadeOut.setAnimationListener(
            object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}

                override fun onAnimationEnd(animation: Animation) {
                    visibility = GONE
                    fadeOutRunnable = null
                }

                override fun onAnimationRepeat(animation: Animation) {}
            },
        )
        startAnimation(fadeIn)
    }
}
