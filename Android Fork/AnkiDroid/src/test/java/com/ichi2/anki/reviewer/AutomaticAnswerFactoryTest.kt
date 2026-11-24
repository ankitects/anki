// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.reviewer.AutomaticAnswerAction.Companion.answerAction
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class AutomaticAnswerFactoryTest : JvmTest() {
    @Test
    fun `factory loads saved automatic answer settings`() {
        val config =
            col.decks.configDictForDeckId(col.decks.selected()).apply {
                answerAction = AutomaticAnswerAction.ANSWER_AGAIN
                secondsToShowQuestion = 1.5
                secondsToShowAnswer = 2.5
            }
        col.decks.save(config)

        val answer = AutomaticAnswer.createInstance(mock(), col)

        assertEquals(AutomaticAnswerAction.ANSWER_AGAIN, answer.settings.answerAction)
        assertEquals(1500L, answer.settings.millisecondsToShowQuestionFor)
        assertEquals(2500L, answer.settings.millisecondsToShowAnswerFor)
    }
}
