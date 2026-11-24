// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import anki.decks.SetDeckCollapsedRequest
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckDeletionTest : JvmTest() {
    private val noFilter = deckFilter()

    private fun deckFilter(search: String = "") = DeckFilters.create(search)

    private fun deleteDeck(
        deckId: DeckId,
        visibleDeckIds: List<DeckId>,
        filter: DeckFilters = noFilter,
    ) = with(col) {
        removeDeckAndSelectAdjacent(deckId, visibleDeckIds, filter)
    }

    @Test
    fun `deleting a non selected deck preserves the selection`() {
        val selected = addDeck(deckName = "A", setAsSelected = true)
        val deleted = addDeck(deckName = "B")

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(selected, deleted))

        assertEquals(selected, col.decks.selected())
        assertFalse(col.decks.have(deleted))
    }

    @Test
    fun `deleting the selected deck selects the next visible deck`() {
        val first = addDeck(deckName = "A")
        val deleted = addDeck(deckName = "B", setAsSelected = true)
        val next = addDeck(deckName = "C")

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(first, deleted, next))

        assertEquals(next, col.decks.selected())
    }

    @Test
    fun `deleting a child of an expanded deck selects the next visible sibling`() {
        val parent = addDeck(deckName = "Parent")
        val deleted = addDeck(deckName = "Parent::A", setAsSelected = true)
        val next = addDeck(deckName = "Parent::B")
        val unrelated = addDeck(deckName = "Z")
        col.decks.setCollapsed(parent, collapsed = false, SetDeckCollapsedRequest.Scope.REVIEWER)

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(parent, deleted, next, unrelated))

        assertEquals(next, col.decks.selected(), "Selected deck: ${col.decks.current().name}")
    }

    @Test
    fun `deleting the last visible deck selects the previous deck`() {
        val first = addDeck(deckName = "A")
        val previous = addDeck(deckName = "B")
        val deleted = addDeck(deckName = "C", setAsSelected = true)

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(first, previous, deleted))

        assertEquals(previous, col.decks.selected())
    }

    @Test
    fun `deleting a parent selects the deck after its subtree`() {
        val first = addDeck(deckName = "A")
        val deleted = addDeck(deckName = "B")
        val child = addDeck(deckName = "B::Child", setAsSelected = true)
        val grandchild = addDeck(deckName = "B::Child::Grandchild")
        val sibling = addDeck(deckName = "B::Sibling")
        val next = addDeck(deckName = "C")

        deleteDeck(
            deckId = deleted,
            visibleDeckIds = listOf(first, deleted, child, grandchild, sibling, next),
        )

        assertEquals(next, col.decks.selected())
        listOf(deleted, child, grandchild, sibling).forEach { assertFalse(col.decks.have(it)) }
    }

    @Test
    fun `replacement follows visible search results`() {
        addDeck(deckName = "A")
        val deleted = addDeck(deckName = "B match", setAsSelected = true)
        addDeck(deckName = "C hidden")
        val next = addDeck(deckName = "D match")

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(deleted, next), filter = deckFilter("match"))

        assertEquals(next, col.decks.selected())
    }

    @Test
    fun `deleting a collapsed parent replaces a selected hidden child`() {
        val first = addDeck(deckName = "A")
        val deleted = addDeck(deckName = "B")
        addDeck(deckName = "B::Child", setAsSelected = true)
        val next = addDeck(deckName = "C")
        col.decks.setCollapsed(deleted, collapsed = true, SetDeckCollapsedRequest.Scope.REVIEWER)

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(first, deleted, next))

        assertEquals(next, col.decks.selected())
    }

    @Test
    fun `replacement skips a parent that only matched through its deleted child`() {
        val previous = addDeck(deckName = "A match")
        val parent = addDeck(deckName = "B")
        val deleted = addDeck(deckName = "B::Child match", setAsSelected = true)

        deleteDeck(
            deckId = deleted,
            visibleDeckIds = listOf(previous, parent, deleted),
            filter = deckFilter("match"),
        )

        assertEquals(previous, col.decks.selected())
    }

    @Test
    fun `deleting the only visible result falls back to another deck`() {
        val fallback = addDeck(deckName = "A")
        val deleted = addDeck(deckName = "B match", setAsSelected = true)

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(deleted), filter = deckFilter("match"))

        assertEquals(fallback, col.decks.selected())
    }

    @Test
    fun `deleting the last deck falls back to Default`() {
        val deleted = addDeck(deckName = "A", setAsSelected = true)

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(deleted))

        assertEquals(DEFAULT_DECK_ID, col.decks.selected())
    }

    @Test
    fun `deletion and replacement selection undo together`() {
        val deleted = addDeck(deckName = "A", setAsSelected = true)
        val next = addDeck(deckName = "B")
        val previousUndo = col.undoStatus().undo

        deleteDeck(deckId = deleted, visibleDeckIds = listOf(deleted, next))

        assertEquals(next, col.decks.selected())
        assertEquals("Delete Deck", col.undoStatus().undo)

        col.undo()
        assertTrue(col.decks.have(deleted))
        assertEquals(deleted, col.decks.selected())
        assertEquals(previousUndo, col.undoStatus().undo)

        col.redo()
        assertFalse(col.decks.have(deleted))
        assertEquals(next, col.decks.selected())
    }
}
