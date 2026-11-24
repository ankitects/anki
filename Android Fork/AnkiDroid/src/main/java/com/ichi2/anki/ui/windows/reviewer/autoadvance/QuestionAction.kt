// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.autoadvance

import com.ichi2.anki.libanki.DeckConfig
import com.ichi2.anki.libanki.DeckConfig.Companion.QUESTION_ACTION

enum class QuestionAction(
    val code: Int,
) : AutoAdvanceAction {
    SHOW_ANSWER(0),
    SHOW_REMINDER(1),
    ;

    companion object {
        fun from(code: Int): QuestionAction = entries.firstOrNull { it.code == code } ?: SHOW_ANSWER

        val DeckConfig.questionAction: QuestionAction
            get() = QuestionAction.from(jsonObject.optInt(QUESTION_ACTION))
    }
}
