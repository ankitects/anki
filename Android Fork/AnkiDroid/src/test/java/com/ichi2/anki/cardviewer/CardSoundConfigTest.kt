// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Reviewer.Companion.EXTRA_DECK_ID
import com.ichi2.anki.libanki.Card
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class CardSoundConfigTest : JvmTest() {
    @Test
    fun `default values`() =
        runTest {
            // defaults as-of Anki Desktop 23.10 (51a10f09)
            val note = addBasicNote()
            val card = note.firstCard()
            createCardSoundConfig(card).run {
                assertThat(EXTRA_DECK_ID, deckId, equalTo(card.did))
                // Anki Desktop: "Skip question when replaying answer" -> false
                // our variable is reversed, so true
                assertThat("replayQuestion", replayQuestion)
                // Anki Desktop: "Don't play audio automatically" -> false
                // our variable is reversed, so true
                assertThat("autoPlay", autoplay)
            }
        }

    @Test
    fun `cards from the same note are equal`() =
        runTest {
            val note = addBasicAndReversedNote()
            val (card1, card2) = note.cards()
            createCardSoundConfig(card1).run {
                assertThat("same note", this.appliesTo(card2))
            }
        }

    @Test
    fun `cards from the same deck are equal`() =
        runTest {
            val (note1, note2) = addNotes(count = 2)
            createCardSoundConfig(note1.firstCard()).run {
                assertThat("same note", this.appliesTo(note2.firstCard()))
            }
        }

    private suspend fun createCardSoundConfig(card: Card) = withCol { CardSoundConfig.create(this@withCol, card) }
}
