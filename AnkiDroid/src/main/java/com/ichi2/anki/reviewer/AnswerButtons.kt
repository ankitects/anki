/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.reviewer

import android.content.Context
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.utils.android.getColorsFromAttrs
import com.ichi2.anki.common.utils.android.getResFromAttr

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
            val backgroundIds: IntArray =
                if (ctx.animationEnabled()) {
                    intArrayOf(
                        R.attr.againButtonRippleRef,
                        R.attr.hardButtonRippleRef,
                        R.attr.goodButtonRippleRef,
                        R.attr.easyButtonRippleRef,
                    )
                } else {
                    intArrayOf(
                        R.attr.againButtonRef,
                        R.attr.hardButtonRef,
                        R.attr.goodButtonRef,
                        R.attr.easyButtonRef,
                    )
                }
            return getResFromAttr(ctx, backgroundIds)
        }

        fun getTextColors(ctx: Context): IntArray =
            getColorsFromAttrs(
                ctx,
                intArrayOf(
                    R.attr.againButtonTextColor,
                    R.attr.hardButtonTextColor,
                    R.attr.goodButtonTextColor,
                    R.attr.easyButtonTextColor,
                ),
            )
    }
}
