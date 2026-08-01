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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.reviewer.AutomaticAnswer.AutomaticallyAnswered
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class AutomaticAnswerTest : JvmTest() {
    @Test
    fun disableWorks() {
        val answer = validAnswer(automaticallyAnsweredMock())
        answer.enable()

        answer.delayedShowQuestion(0)
        answer.delayedShowAnswer(0)

        assertThat("it should have messages", answer.timeoutHandler.hasMessages(0), equalTo(true))
        assertThat("answer should be enabled", answer.isDisabled, equalTo(false))

        answer.disable()

        assertThat("it should not have messages", answer.timeoutHandler.hasMessages(0), equalTo(false))
        assertThat("answer should be disabled", answer.isDisabled, equalTo(true))
    }

    @Test
    fun noExecutionIfTimerIsZero_issue8923() {
        val answer =
            AutomaticAnswer(
                target = automaticallyAnsweredMock(),
                settings =
                    AutomaticAnswerSettings(
                        secondsToShowQuestionFor = 0.0,
                        secondsToShowAnswerFor = 0.0,
                    ),
            )

        answer.scheduleAutomaticDisplayQuestion(10)

        assertThat("no messages even if delay provided", answer.timeoutHandler.hasMessages(0), equalTo(false))

        answer.scheduleAutomaticDisplayAnswer(10)

        assertThat("no messages even if delay provided", answer.timeoutHandler.hasMessages(0), equalTo(false))
    }

    @Test
    fun testEnableDisable() {
        val answer = validAnswer(automaticallyAnsweredMock(), enable = false)
        assertThat("answer should be disabled", answer.isDisabled, equalTo(true))
        answer.enable()
        assertThat("answer should be enabled", answer.isDisabled, equalTo(false))
        answer.disable()
        assertThat("answer should be disabled", answer.isDisabled, equalTo(true))
    }

    /** Ensures [disableStopsImmediateCallAnswer] can fail */
    @Test
    fun immediateCall() {
        val answerValue = AutoAnswerMock()
        val answer = validAnswer(answerValue)

        answer.scheduleAutomaticDisplayAnswer()
        waitForTaskCompletion()
        assertThat("answer should be shown", answerValue.answerShown)

        answer.scheduleAutomaticDisplayQuestion()
        waitForTaskCompletion()
        assertThat("question should be shown", answerValue.questionShown)
    }

    @Test
    fun disableStopsImmediateCallAnswer() {
        val answerValue = AutoAnswerMock()
        val answer = validAnswer(answerValue)
        answer.scheduleAutomaticDisplayAnswer()
        answer.disable()
        assertThat("call did not complete early", answerValue.answerShown, equalTo(false))
        waitForTaskCompletion()
        assertThat("call not executed due to disable", answerValue.answerShown, equalTo(false))
    }

    @Test
    fun disableStopsImmediateCallQuestion() {
        val answerValue = AutoAnswerMock()
        val answer = validAnswer(answerValue)
        answer.scheduleAutomaticDisplayQuestion()
        answer.disable()
        assertThat("call did not complete early", answerValue.questionShown, equalTo(false))
        waitForTaskCompletion()
        assertThat("call not executed due to disable", answerValue.questionShown, equalTo(false))
    }

    private fun waitForTaskCompletion() {
        runUiThreadTasksIncludingDelayedTasks()
    }

    private fun validAnswer(
        automaticallyAnswered: AutomaticallyAnswered? = null,
        enable: Boolean = true,
    ): AutomaticAnswer {
        var automaticAnswerHandle: AutomaticAnswer? = null

        val automaticAnswerHandler =
            object : AutomaticallyAnswered {
                override fun automaticShowAnswer() {
                    automaticAnswerHandle?.simulateCardFlip()
                    automaticallyAnswered?.automaticShowAnswer()
                }

                override fun automaticShowQuestion(action: AutomaticAnswerAction) {
                    automaticAnswerHandle?.simulateCardFlip()
                    automaticallyAnswered?.automaticShowQuestion(action)
                }
            }
        return AutomaticAnswer(
            target = automaticAnswerHandler,
            settings =
                AutomaticAnswerSettings(
                    secondsToShowQuestionFor = 10.0,
                    secondsToShowAnswerFor = 10.0,
                ),
        ).apply {
            automaticAnswerHandle = this
            if (enable) {
                this.enable()
            }
        }
    }

    private class AutoAnswerMock(
        var answerShown: Boolean = false,
        var questionShown: Boolean = false,
    ) : AutomaticallyAnswered {
        override fun automaticShowAnswer() {
            answerShown = true
        }

        override fun automaticShowQuestion(action: AutomaticAnswerAction) {
            questionShown = true
        }
    }

    private fun automaticallyAnsweredMock(): AutomaticallyAnswered = mock()
}
