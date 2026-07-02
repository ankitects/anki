/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
