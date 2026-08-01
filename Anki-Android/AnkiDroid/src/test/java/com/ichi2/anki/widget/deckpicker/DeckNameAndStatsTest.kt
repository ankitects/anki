/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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

package com.ichi2.anki.widget.deckpicker

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.widget.deckpicker.getDeckNamesAndStats
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DeckNameAndStatsTest : RobolectricTest() {
    @Test
    fun testGetDeckNameAndStats_withTopLevelDecks() =
        runTest {
            val deck1Id = addDeck("Deck 1")
            val deck2Id = addDeck("Deck 2")
            val deckIds = listOf(deck1Id, deck2Id)

            val result = getDeckNamesAndStats(deckIds)

            assertEquals(2, result.size)
            assertEquals("Deck 1", result[0].name)
            assertEquals(deck1Id, result[0].deckId)
            assertEquals("Deck 2", result[1].name)
            assertEquals(deck2Id, result[1].deckId)
        }

    @Test
    fun testGetDeckNameAndStats_ordering() =
        runTest {
            val deckAId = addDeck("Deck A")
            val deckBId = addDeck("Deck B")
            val deckCId = addDeck("Deck C")
            val deckIds = listOf(deckCId, deckAId, deckBId)

            val result = getDeckNamesAndStats(deckIds)

            assertEquals(3, result.size)
            assertEquals("Deck C", result[0].name)
            assertEquals(deckCId, result[0].deckId)
            assertEquals("Deck A", result[1].name)
            assertEquals(deckAId, result[1].deckId)
            assertEquals("Deck B", result[2].name)
            assertEquals(deckBId, result[2].deckId)
        }

    @Test
    fun testGetDeckNameAndStats_withChildDecks() =
        runTest {
            val deck1Id = addDeck("Deck 1")
            val child1Id = addDeck("Deck 1::Child 1")
            val deckIds = listOf(deck1Id, child1Id)

            val result = getDeckNamesAndStats(deckIds)

            assertEquals(2, result.size)
            assertEquals("Deck 1", result[0].name)
            assertEquals(deck1Id, result[0].deckId)
            assertEquals("Child 1", result[1].name) // Changed to truncated name
            assertEquals(child1Id, result[1].deckId)
        }
}
