// SPDX-License-Identifier: GPL-3.0-or-later

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
