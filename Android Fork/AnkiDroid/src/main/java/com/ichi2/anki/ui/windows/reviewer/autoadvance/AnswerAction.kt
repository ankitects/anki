// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.autoadvance

import com.ichi2.anki.libanki.DeckConfig
import com.ichi2.anki.libanki.DeckConfig.Companion.ANSWER_ACTION

enum class AnswerAction(
    val code: Int,
) : AutoAdvanceAction {
    BURY_CARD(0),
    ANSWER_AGAIN(1),
    ANSWER_GOOD(2),
    ANSWER_HARD(3),
    SHOW_REMINDER(4),
    ;

    companion object {
        fun from(code: Int): AnswerAction = AnswerAction.entries.firstOrNull { it.code == code } ?: BURY_CARD

        val DeckConfig.answerAction: AnswerAction
            get() = AnswerAction.from(jsonObject.optInt(ANSWER_ACTION))
    }
}
