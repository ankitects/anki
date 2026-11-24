// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectableDeckTest : InMemoryAnkiTest() {
    @Test
    fun canChangeDeckToRegularDeck() {
        addDeck("Hello")
        repeat(5) { addBasicNote() }

        assertTrue(availableDecks().any { it.name == "Hello" })
    }

    @Test
    fun cannotChangeDeckToDynamicDeck() {
        addDynamicDeck("World")
        repeat(5) { addBasicNote() }

        assertTrue(availableDecks().none { it.name == "World" })
        assertTrue(SelectableDeck.fromCollection(col, includeFiltered = true).any { it.name == "World" })
    }

    @Test
    fun newlyCreatedDeckIsShownAsOptionInBrowser() {
        val deckOneId = addDeck("one")
        assertEquals(2, availableDecks().size)
        assertEquals(1, availableDecks().count { it.deckId == deckOneId })

        val deckTwoId = addDeck("two")

        assertEquals(3, availableDecks().size)
        assertEquals(1, availableDecks().count { it.deckId == deckOneId })
        assertEquals(1, availableDecks().count { it.deckId == deckTwoId })
    }

    @Test
    fun `hiding an empty default deck preserves a parent with subdecks`() {
        assertEquals(listOf(DEFAULT_DECK_ID), availableDecks().map { it.deckId })
        assertTrue(availableDecks(skipEmptyDefault = true).isEmpty())

        val child = addDeck("Default::Child")

        assertEquals(setOf(DEFAULT_DECK_ID, child), availableDecks(skipEmptyDefault = true).map { it.deckId }.toSet())
    }

    private fun availableDecks(skipEmptyDefault: Boolean = false) =
        SelectableDeck.fromCollection(col, includeFiltered = false, skipEmptyDefault = skipEmptyDefault)
}
