// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.Context
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.android.animationEnabled
import com.ichi2.anki.common.utils.android.getColorsFromAttrs
import com.ichi2.anki.common.utils.android.getResFromAttr
import com.ichi2.anki.common.android.R as CommonR

/**
 * Handles mapping from an answer button to its position.
 *
 * Anki accepts the ordinal of the answer button, therefore
 * each button can represent multiple answers
 */
enum class AnswerButtons {
    /**
     * Again represents a failure.
     * In Step-Mode, a card is sent to the first step.
     * In Review-Mode, a card is moved to relearning and placed in the learn-queue or learn-day-queue.
     */
    AGAIN,

    /**
     * Hard represents a correct answer that was difficult to recall.
     *
     * In step mode, we stay on the same step and interpolates between the current and next interval.
     *
     * In Review mode, hard uses a lower ease factor and reduces the ease by 15%
     *
     * ## Interpolation
     *
     * Example:
     *
     * * Steps of `[1, 1000]` give a hard interval of 500 minutes (8.3 hours)
     * * Steps of `[100]` give a hard interval of 150 minutes: (100 + (100 * 2))/2 - double the current step if there is only one step
     * * Steps of `[100, 50]` give a hard interval of 100 minutes: (100 + 100) / 2 - use the current step if it is larger than the next step
     * * * This leads to: `Again: 1.7, Hard: 2.5, Good: 1.7` - hard being greater than good
     * * * See: [Anki - Hard/good interval is longer than good/easy](https://faqs.ankiweb.net/hard-good-interval-longer-than-good-easy.html)
     */
    HARD,

    /**
     * In Step-Mode, Good moves to the next step, or performs a regular graduation
     *
     * Good represents a correct answer. It does not affect the ease factor
     */
    GOOD,

    /**
     * In Step-Mode, Easy performs an early graduation
     *
     * In Review mode, easy adds additional bonuses to the interval and increases the ease by 15%
     */
    EASY,

    ;

    fun toViewerCommand(): ViewerCommand =
        when (this) {
            AGAIN -> ViewerCommand.ANSWER_AGAIN
            HARD -> ViewerCommand.ANSWER_HARD
            GOOD -> ViewerCommand.ANSWER_GOOD
            EASY -> ViewerCommand.ANSWER_EASY
        }

    companion object {
        fun getBackgroundColors(ctx: AnkiActivity): IntArray {
            // One drawable for every ease: it takes the color from the button theme
            val attr = if (ctx.animationEnabled()) CommonR.attr.answerButtonRippleRef else CommonR.attr.answerButtonRef
            val background = getResFromAttr(ctx, attr)
            return IntArray(entries.size) { background }
        }

        fun getTextColors(ctx: Context): IntArray =
            getColorsFromAttrs(
                ctx,
                intArrayOf(
                    CommonR.attr.againButtonTextColor,
                    CommonR.attr.hardButtonTextColor,
                    CommonR.attr.goodButtonTextColor,
                    CommonR.attr.easyButtonTextColor,
                ),
            )
    }
}
