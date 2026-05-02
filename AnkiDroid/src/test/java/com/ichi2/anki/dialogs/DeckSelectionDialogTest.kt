/*
 *  Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
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

package com.ichi2.anki.dialogs

import anki.decks.deckTreeNode
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.model.SelectableDeck
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeckSelectionDialogTest : RobolectricTest() {
    @Test
    fun verifyDeckDisplayName() {
        val input = "deck::sub-deck::sub-deck2::sub-deck3"
        val expected = "sub-deck3"

        val deck = SelectableDeck.Deck(1234, input)
        val actual: String = deck.getDisplayName(targetContext)

        assertThat(actual, Matchers.equalTo(expected))
    }

    @Test
    fun testDialogCreation() {
        val decks: List<SelectableDeck> = listOf(SelectableDeck.Deck(5L, "deck"))
        val dialogTitle = "Select Deck"
        val summaryMessage = "Choose a deck from the list"

        val dialog = DeckSelectionDialog.newInstance(title = dialogTitle, templateEditorMessage = summaryMessage, decks = decks)
        assertNotNull(dialog)
        assertEquals(dialogTitle, dialog.arguments?.getString("arg_title"))
        assertEquals(summaryMessage, dialog.arguments?.getString("arg_template_editor_message"))
    }

    @Test
    fun `selecting child deck does not collect parent or sibling ids`() {
        // Build tree: Parent (id=1) -> Child1 (id=2), Child2 (id=3)
        val child1 = makeNode("Child1", deckId = 2, level = 2)
        val child2 = makeNode("Child2", deckId = 3, level = 2)
        val parent = makeNode("Parent", deckId = 1, level = 1, children = listOf(child1, child2))

        val selectedChild = parent.find(2L)!!
        val idsToRemove = mutableSetOf(selectedChild.did)
        selectedChild.forEach { idsToRemove.add(it.did) }

        assertThat(idsToRemove, containsInAnyOrder(2L))
        assertThat(idsToRemove, not(hasItem(1L)))
        assertThat(idsToRemove, not(hasItem(3L)))
    }

    @Test
    fun `selecting parent deck with nested children collects all descendants`() {
        // Build tree: A (id=1) -> B (id=2) -> C (id=3)
        //                      -> D (id=4)
        val c = makeNode("C", deckId = 3, level = 3)
        val b = makeNode("B", deckId = 2, level = 2, children = listOf(c))
        val d = makeNode("D", deckId = 4, level = 2)
        val a = makeNode("A", deckId = 1, level = 1, children = listOf(b, d))

        val idsToRemove = mutableSetOf(a.did)
        a.forEach { idsToRemove.add(it.did) }

        assertThat(idsToRemove, containsInAnyOrder(1L, 2L, 3L, 4L))
    }

    private fun makeNode(
        name: String,
        deckId: Long,
        level: Int,
        collapsed: Boolean = false,
        children: List<DeckNode> = emptyList(),
    ): DeckNode {
        val treeNode =
            deckTreeNode {
                this.name = name
                this.deckId = deckId
                this.level = level
                this.collapsed = collapsed
                children.forEach { this.children.add(it.node) }
                this.reviewCount = 0
                this.newCount = 0
                this.learnCount = 0
                this.filtered = false
            }
        return DeckNode(treeNode, name)
    }
}
