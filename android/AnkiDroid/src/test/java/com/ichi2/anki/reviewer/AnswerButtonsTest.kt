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
