// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import com.ichi2.anki.reviewer.AutomaticAnswerAction.Companion.answerAction
import com.ichi2.testutils.JvmTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AutomaticAnswerSettingsTest : JvmTest() {
    @Test
    fun default_is_bury() {
        assertThat("no value", createInstance().answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
    }

    @Test
    fun preference_sets_action() {
        setActionType(AutomaticAnswerAction.ANSWER_AGAIN)
        assertThat(createInstance().answerAction, equalTo(AutomaticAnswerAction.ANSWER_AGAIN))
        // reset the value
        resetPrefs()
        assertThat("default", createInstance().answerAction, equalTo(AutomaticAnswerAction.BURY_CARD))
    }

    @Test
    fun `milliseconds are handled`() {
        setShowQuestionDuration(1.5)
        assertThat(createInstance().millisecondsToShowQuestionFor, equalTo(1500))
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

    private fun createInstance() = AutomaticAnswerSettings.createInstance(super.col)
}
