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
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.reviewer.AutomaticAnswerAction.Companion.answerAction
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AutomaticAnswerAndroidTest : RobolectricTest() {
    @Test
    fun default_is_bury() {
        assertThat("no value", createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
        assertThat("default", AutomaticAnswer.defaultInstance(mock()).settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
    }

    @Test
    fun preference_sets_action() {
        setActionType(AutomaticAnswerAction.ANSWER_AGAIN)
        assertThat(createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.ANSWER_AGAIN))
        // reset the value
        resetPrefs()
        assertThat("default", createInstance().settings.answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
    }

    @Test
    fun `milliseconds are handled`() {
        setShowQuestionDuration(1.5)
        assertThat(createInstance().settings.millisecondsToShowQuestionFor, equalTo(1500))
    }

    private fun resetPrefs() {
        val conf =
            col.decks.configDictForDeckId(col.decks.selected()).apply {
                removeAnswerAction()
            }
        col.decks.save(conf)
    }

    @Suppress("SameParameterValue")
    private fun setActionType(value: AutomaticAnswerAction) {
        val conf =
            col.decks.configDictForDeckId(col.decks.selected()).apply {
                answerAction = value
            }
        col.decks.save(conf)
    }

    @Suppress("SameParameterValue")
    private fun setShowQuestionDuration(value: Double) {
        val conf =
            col.decks.configDictForDeckId(col.decks.selected()).apply {
                secondsToShowQuestion = value
            }
        col.decks.save(conf)
    }

    private fun createInstance() = AutomaticAnswer.createInstance(mock(), super.col)
}
