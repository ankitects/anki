// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.AnswerButtons.AGAIN
import com.ichi2.anki.reviewer.AnswerButtons.EASY
import com.ichi2.anki.reviewer.AnswerButtons.GOOD
import com.ichi2.anki.reviewer.AnswerButtons.HARD
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AnswerButtonsTest {
    @Test
    fun checkButtons() {
        assertThat(AGAIN.toViewerCommand(), equalTo(ViewerCommand.ANSWER_AGAIN))
        assertThat(HARD.toViewerCommand(), equalTo(ViewerCommand.ANSWER_HARD))
        assertThat(GOOD.toViewerCommand(), equalTo(ViewerCommand.ANSWER_GOOD))
        assertThat(EASY.toViewerCommand(), equalTo(ViewerCommand.ANSWER_EASY))
    }
}
