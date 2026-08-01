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

import com.ichi2.anki.asyncIO
import com.ichi2.anki.libanki.Card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Implementation of the `Auto Advance` deck options
 *
 * A timer (in seconds) can be set to automatically trigger an action after it runs out,
 * either in the question side ([QuestionAction]) or in the answer side ([AnswerAction]).
 *
 * If a timer is set to 0, the corresponding action is not triggered.
 *
 * @see AutoAdvanceSettings
 */
class AutoAdvance(
    private val scope: CoroutineScope,
    private val listener: ActionListener,
    initialCard: Deferred<Card>,
) {
    /**
     * Listens to the `Auto Advance` actions set in Deck options,
     * which can be either a [QuestionAction] or a [AnswerAction].
     */
    fun interface ActionListener {
        suspend fun onAutoAdvanceAction(action: AutoAdvanceAction)
    }

    var isEnabled = false
        set(value) {
            field = value
            if (!value) {
                cancelQuestionAndAnswerActionJobs()
            }
        }
    private var questionActionJob: Job? = null
    private var answerActionJob: Job? = null

    private var settings =
        scope.asyncIO {
            AutoAdvanceSettings.createInstance(initialCard.await().currentDeckId())
        }

    private suspend fun durationToShowQuestionFor() = settings.await().durationToShowQuestionFor

    private suspend fun durationToShowAnswerFor() = settings.await().durationToShowAnswerFor

    private suspend fun questionAction() = settings.await().questionAction

    private suspend fun answerAction() = settings.await().answerAction

    suspend fun shouldWaitForAudio() = settings.await().waitForAudio

    fun cancelQuestionAndAnswerActionJobs() {
        questionActionJob?.cancel()
        answerActionJob?.cancel()
    }

    fun onCardChange(card: Card) {
        cancelQuestionAndAnswerActionJobs()
        settings =
            scope.asyncIO {
                AutoAdvanceSettings.createInstance(card.currentDeckId())
            }
    }

    suspend fun onShowQuestion() {
        answerActionJob?.cancel()
        if (!durationToShowQuestionFor().isPositive() || !isEnabled) return

        questionActionJob =
            scope.launch {
                delay(durationToShowQuestionFor())
                listener.onAutoAdvanceAction(questionAction())
            }
    }

    suspend fun onShowAnswer() {
        questionActionJob?.cancel()
        if (!durationToShowAnswerFor().isPositive() || !isEnabled) return

        answerActionJob =
            scope.launch {
                delay(durationToShowAnswerFor())
                listener.onAutoAdvanceAction(answerAction())
            }
    }
}
