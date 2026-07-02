/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.previewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.Card
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypeAnswerTest : JvmTest() {
    /** [Issue #20575](https://github.com/ankidroid/Anki-Android/issues/20575) */
    @Test
    fun `answerFilter escapes Regex`() =
        runTest {
            val card = addBasicWithTypingNote("List directory contents.", "$ ls").firstCard()

            val typeAnswer = TypeAnswer.createInstance(card)

            val result = assertDoesNotThrow { typeAnswer.answerFilter("") }
            assertThat(result, containsString("$ ls"))
            assertThat(result, not(containsString("[[type:Back]]")))
        }

    companion object {
        suspend fun TypeAnswer.Companion.createInstance(card: Card) = requireNotNull(TypeAnswer.getInstance(card, VALID_CARD_TEXT))

        const val VALID_CARD_TEXT = """<style>.card {
    font-family: arial;
    font-size: 20px;
    line-height: 1.5;
    text-align: center;
    color: black;
    background-color: white;
}
</style>List directory contents.

<hr id=answer>

[[type:Back]]"""
    }
}
