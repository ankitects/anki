// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.anki.widget.deckpicker

import com.ichi2.testutils.JvmTest
import com.ichi2.widget.deckpicker.getDeckNamesAndStats
import org.junit.Test
import kotlin.test.assertEquals

class DeckNameAndStatsTest : JvmTest() {
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
