// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import com.ichi2.anki.libanki.Card
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertNotNull

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

    @Test
    fun `chained modifiers are parsed`() =
        runTest {
            val card = addClozeNote("{{c1::hello}} world").firstCard()

            val typeAnswer = TypeAnswer.getInstance(card, "[[type:cloze:nc:Text]]")

            assertNotNull(typeAnswer, "chained 'cloze' and 'nc' modifiers")
            assertThat(typeAnswer.expectedAnswer, equalTo("hello"))
        }

    /** [Issue #10352](https://github.com/ankidroid/Anki-Android/issues/10352) */
    @Test
    fun `noSuggest is false when nosuggest modifier is absent`() =
        runTest {
            val card = addBasicWithTypingNote("front", "back").firstCard()
            val typeAnswer = requireNotNull(TypeAnswer.getInstance(card, "[[type:Back]]"))
            assertThat(typeAnswer.noSuggest, equalTo(false))
        }

    @Test
    fun `noSuggest is true when nosuggest modifier is present`() =
        runTest {
            val card = addBasicWithTypingNote("front", "back").firstCard()
            val typeAnswer = requireNotNull(TypeAnswer.getInstance(card, "[[type:nosuggest:Back]]"))
            assertThat(typeAnswer.noSuggest, equalTo(true))
        }

    @Test
    fun `nosuggest composes with nc modifier`() =
        runTest {
            val card = addBasicWithTypingNote("front", "back").firstCard()
            val typeAnswer = requireNotNull(TypeAnswer.getInstance(card, "[[type:nosuggest:nc:Back]]"))
            assertThat(typeAnswer.noSuggest, equalTo(true))
        }

    @Test
    fun `combining is true when nc modifier is absent`() =
        runTest {
            val card = addBasicWithTypingNote("front", "back").firstCard()
            val typeAnswer = requireNotNull(TypeAnswer.getInstance(card, "[[type:Back]]"))
            assertThat(typeAnswer.combining, equalTo(true))
        }

    @Test
    fun `combining is false when nc modifier is present`() =
        runTest {
            val card = addBasicWithTypingNote("front", "back").firstCard()
            val typeAnswer = requireNotNull(TypeAnswer.getInstance(card, "[[type:nc:Back]]"))
            assertThat(typeAnswer.combining, equalTo(false))
        }

    @Test
    fun `nosuggest composes with cloze modifier`() =
        runTest {
            val card = addClozeNote("{{c1::hello}} world").firstCard()
            val typeAnswer = requireNotNull(TypeAnswer.getInstance(card, "[[type:nosuggest:cloze:Text]]"))
            assertThat(typeAnswer.noSuggest, equalTo(true))
            assertThat(typeAnswer.expectedAnswer, equalTo("hello"))
        }

    @Test
    fun `nosuggest composes with cloze and nc modifiers`() =
        runTest {
            val card = addClozeNote("{{c1::hello}} world").firstCard()
            val typeAnswer = requireNotNull(TypeAnswer.getInstance(card, "[[type:nosuggest:cloze:nc:Text]]"))
            assertThat(typeAnswer.noSuggest, equalTo(true))
            assertThat(typeAnswer.combining, equalTo(false))
            assertThat(typeAnswer.expectedAnswer, equalTo("hello"))
        }

    @Test
    fun `cloze type-in-answer extracts expected answer for each card ordinal`() =
        runTest {
            val note = addClozeNote("{{c1::alpha}} and {{c2::beta}}")
            val (card1, card2) = note.cards()

            val typeAnswerCard1 = requireNotNull(TypeAnswer.getInstance(card1, "[[type:cloze:Text]]"))
            assertThat(typeAnswerCard1.expectedAnswer, equalTo("alpha"))

            val typeAnswerCard2 = requireNotNull(TypeAnswer.getInstance(card2, "[[type:cloze:Text]]"))
            assertThat(typeAnswerCard2.expectedAnswer, equalTo("beta"))
        }

    @Test
    fun `cloze type-in-answer extracts expected answer with hint`() =
        runTest {
            val card = addClozeNote("{{c1::alpha::hint for alpha}}").firstCard()
            val typeAnswer = requireNotNull(TypeAnswer.getInstance(card, "[[type:cloze:Text]]"))
            assertThat(typeAnswer.expectedAnswer, equalTo("alpha"))
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
