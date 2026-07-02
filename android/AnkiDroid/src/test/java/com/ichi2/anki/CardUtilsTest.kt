/*
 Copyright (c) 2026 Ayush <ayushdevraj9@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardUtilsTest : RobolectricTest() {
    @Test
    fun getDeckIdForCard_regularDeck_returnsDid() {
        val note = addBasicNote("Front", "Back")
        val card = note.firstCard()

        assertThat("Card should be in a regular deck", card.oDid, equalTo(0L))

        val deckId = CardUtils.getDeckIdForCard(card)

        assertThat("Should return the card's did", deckId, equalTo(card.did))
    }

    @Test
    fun getDeckIdForCard_cramDeck_returnsODid() {
        val note = addBasicNote("Front", "Back")
        val card = note.firstCard()

        val filteredDid = addDynamicDeck("Filtered")
        col.sched.rebuildFilteredDeck(filteredDid)
        card.load()

        assertThat("Card should have oDid set in filtered deck", card.oDid != 0L, equalTo(true))

        val deckId = CardUtils.getDeckIdForCard(card)

        assertThat("Should return the original deck ID (oDid)", deckId, equalTo(card.oDid))
    }

    @Test
    fun getDeckIdForCard_zeroODid_returnsDid() {
        val note = addBasicNote("Front", "Back")
        val card = note.firstCard()

        assertThat("Card should be in a regular deck", card.oDid, equalTo(0L))

        val deckId = CardUtils.getDeckIdForCard(card)

        assertThat(deckId, equalTo(card.did))
    }
}
