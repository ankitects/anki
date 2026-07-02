/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.cardviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Reviewer.Companion.EXTRA_DECK_ID
import com.ichi2.anki.libanki.Card
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

// PERF: remove AndroidApplication
@RunWith(AndroidJUnit4::class)
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

    @Ignore("not implemented")
    @Test
    fun `cards with the same deck options are equal`() {
    }

    private suspend fun createCardSoundConfig(card: Card) = withCol { CardSoundConfig.create(this@withCol, card) }
}
