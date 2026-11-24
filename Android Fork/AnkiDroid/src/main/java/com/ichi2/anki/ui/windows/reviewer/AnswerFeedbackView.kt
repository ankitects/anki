// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.R
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.utils.ext.setDuration
import com.ichi2.anki.utils.postDelayed
import kotlin.time.Duration.Companion.milliseconds

class AnswerFeedbackView : AppCompatImageView {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val fadeOut =
        Runnable {
            if (!Animations.areAnimationsEnabled(context)) {
                visibility = GONE
                return@Runnable
            }
            animate()
                .alpha(0f)
                .setDuration(FADE_OUT_DURATION)
                .setInterpolator(FastOutLinearInInterpolator())
                .withEndAction { visibility = GONE }
                .start()
        }

    /**
     * Shows the icon of [rating] for 800ms
     * with a quick fade in, brief hold, then gentle fade out.
     */
    fun show(rating: Rating) {
        setImageResource(
            when (rating) {
                Rating.AGAIN -> R.drawable.ic_ease_again
                Rating.HARD -> R.drawable.ic_ease_hard
                Rating.GOOD -> R.drawable.ic_ease_good
                Rating.EASY -> R.drawable.ic_ease_easy
                Rating.UNRECOGNIZED -> throw IllegalArgumentException("Invalid rating")
            },
        )
        animate().cancel()
        removeCallbacks(fadeOut)
        visibility = VISIBLE

        if (!Animations.areAnimationsEnabled(context)) {
            alpha = 1f
            postDelayed(fadeOut, FADE_IN_DURATION + HOLD_DURATION + FADE_OUT_DURATION)
            return
        }

        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(FADE_IN_DURATION)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction { postDelayed(fadeOut, HOLD_DURATION) }
            .start()
    }

    companion object {
        private val FADE_IN_DURATION = 150.milliseconds
        private val HOLD_DURATION = 400.milliseconds
        private val FADE_OUT_DURATION = 250.milliseconds
    }
}
