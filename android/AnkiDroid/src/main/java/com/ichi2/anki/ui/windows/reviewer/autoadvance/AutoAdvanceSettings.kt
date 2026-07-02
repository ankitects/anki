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

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AnswerAction.Companion.answerAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.QuestionAction.Companion.questionAction
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class AutoAdvanceSettings(
    val questionAction: QuestionAction,
    val answerAction: AnswerAction,
    val durationToShowQuestionFor: Duration,
    val durationToShowAnswerFor: Duration,
    val waitForAudio: Boolean,
) {
    companion object {
        suspend fun createInstance(deckId: DeckId): AutoAdvanceSettings {
            val config = withCol { decks.configDictForDeckId(deckId) }

            return AutoAdvanceSettings(
                questionAction = config.questionAction,
                answerAction = config.answerAction,
                durationToShowQuestionFor = config.secondsToShowQuestion.toDuration(DurationUnit.SECONDS),
                durationToShowAnswerFor = config.secondsToShowAnswer.toDuration(DurationUnit.SECONDS),
                waitForAudio = config.waitForAudio,
            )
        }
    }
}
