// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.widget.TextView
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.common.utils.android.HandlerUtils.newHandler
import com.ichi2.anki.common.android.R as CommonR

/**
 * A visual element in the top bar showing a number of colored dots based on the previous answer
 *
 * Informs a user of their previous answer
 *
 * This is hidden after a timer ([CHOSEN_ANSWER_DURATION_MS])
 */
class PreviousAnswerIndicator(
    private val chosenAnswerText: TextView,
) {
    /** After the indicator is displayed, it is hidden after a timeout */
    private val timerHandler = newHandler()

    /** The action taken after the timer executes */
    private val removeChosenAnswerText: Runnable = Runnable { clear() }

    /**
     * Displays a number of dots based on the answer for [CHOSEN_ANSWER_DURATION_MS]
     *
     * The number of dots is the ordinal of the button
     * The color of the dots is the color of the answer
     *
     * Note that the ordinal does not define the color on its own:
     * in SchedV1, button 2 could be hard or good
     *
     * @param rating The ordinal of the button answered
     */
    fun displayAnswerIndicator(rating: Rating) {
        when (rating) {
            Rating.AGAIN -> {
                chosenAnswerText.text = "\u2022"
                chosenAnswerText.setTextColor(getColor(CommonR.color.material_red_500))
            }
            Rating.HARD -> {
                chosenAnswerText.text = "\u2022\u2022"
                chosenAnswerText.setTextColor(getColor(CommonR.color.material_blue_grey_600))
            }
            Rating.GOOD -> {
                chosenAnswerText.text = "\u2022\u2022\u2022"
                chosenAnswerText.setTextColor(getColor(CommonR.color.material_green_500))
            }
            Rating.EASY -> {
                chosenAnswerText.text = "\u2022\u2022\u2022\u2022"
                chosenAnswerText.setTextColor(getColor(CommonR.color.material_light_blue_500))
            }
            Rating.UNRECOGNIZED -> {}
        }

        // remove chosen answer hint after a while
        timerHandler.removeCallbacks(removeChosenAnswerText)
        timerHandler.postDelayed(removeChosenAnswerText, CHOSEN_ANSWER_DURATION_MS)
    }

    private fun clear() {
        chosenAnswerText.text = ""
    }

    fun setVisibility(visibility: Int) {
        chosenAnswerText.visibility = visibility
    }

    /** Stop the timer which hides the answer indicator */
    fun stopAutomaticHide() {
        timerHandler.removeCallbacks(removeChosenAnswerText)
    }

    private fun getColor(color: Int) = chosenAnswerText.context.getColor(color)

    companion object {
        /** The amount of time to display the answer indicator (2 seconds) */
        private const val CHOSEN_ANSWER_DURATION_MS = 2000L
    }
}
