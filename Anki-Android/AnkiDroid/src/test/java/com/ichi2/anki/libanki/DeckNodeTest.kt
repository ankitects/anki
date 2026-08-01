/*
 *
 * Copyright (c) 2025 Shridhar Goel <shridhar.goel@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.libanki

import anki.decks.deckTreeNode
import com.ichi2.anki.deckpicker.DeckFilters
import com.ichi2.anki.deckpicker.filterAndFlattenDisplay
import com.ichi2.anki.libanki.sched.DeckNode
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckNodeTest {
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

    @Test
    fun `search finds subdeck even if parent collapsed`() {
        // Science::Math::Algebra::Group
        val group = makeNode("Group", 4, 4)
        val algebra = makeNode("Algebra", 3, 3, children = listOf(group))
        val math = makeNode("Math", 2, 2, collapsed = true, children = listOf(algebra))
        val science = makeNode("Science", 1, 1, children = listOf(math))

        val results1 = science.filterAndFlatten("group")
        assertEquals(listOf("Science", "Math", "Algebra", "Group"), results1.map { it.lastDeckNameComponent })

        val results2 = science.filterAndFlatten("math")
        assertEquals(listOf("Science", "Math"), results2.map { it.lastDeckNameComponent })
    }

    @Test
    fun `collapsed parent hides children when not searching`() {
        val group = makeNode("Group", 4, 4)
        val algebra = makeNode("Algebra", 3, 3, children = listOf(group))
        val math = makeNode("Math", 2, 2, collapsed = true, children = listOf(algebra))
        val science = makeNode("Science", 1, 1, children = listOf(math))

        val results = math.filterAndFlatten("")
        assertEquals(1, results.size)
        assertEquals("Math", results[0].lastDeckNameComponent)
    }

    @Test
    fun `search for non-matching term returns no results`() {
        // Science::Math::Algebra::Group
        val group = makeNode("Group", 4, 4)
        val algebra = makeNode("Algebra", 3, 3, children = listOf(group))
        val math = makeNode("Math", 2, 2, collapsed = true, children = listOf(algebra))
        val science = makeNode("Science", 1, 1, children = listOf(math))

        val results = science.filterAndFlatten("complex")
        assertEquals(emptyList<String>(), results.map { it.lastDeckNameComponent })
    }

    @Test
    fun `search for algebra shows all decks in the path`() {
        // Science::Math::Algebra::Group
        val group = makeNode("Group", 4, 4)
        val algebra = makeNode("Algebra", 3, 3, children = listOf(group))
        val math = makeNode("Math", 2, 2, collapsed = false, children = listOf(algebra))
        val science = makeNode("Science", 1, 1, children = listOf(math))

        val results = science.filterAndFlatten("algebra")
        assertEquals(listOf("Science", "Math", "Algebra", "Group"), results.map { it.lastDeckNameComponent })
    }

    @Test
    fun `search for deck with correct parent shows appropriate decks`() {
        // Science::Math::Algebra::Group
        val analysis = makeNode("Analysis", 4, 4)
        val group = makeNode("Group", 4, 4)
        val algebra = makeNode("Algebra", 3, 3, children = listOf(group, analysis))
        val math = makeNode("Math", 2, 2, collapsed = false, children = listOf(algebra))
        val science = makeNode("Science", 1, 1, children = listOf(math))

        val results = science.filterAndFlatten("th::al")
        assertEquals(listOf("Science", "Math", "Algebra", "Group", "Analysis"), results.map { it.lastDeckNameComponent })
    }

    @Test
    fun `search for deck with incorrect parent shows no deck`() {
        // Science::Math::Algebra::Group
        val group = makeNode("Group", 4, 4)
        val algebra = makeNode("Algebra", 3, 3, children = listOf(group))
        val math = makeNode("Math", 2, 2, collapsed = false, children = listOf(algebra))
        val science = makeNode("Science", 1, 1, children = listOf(math))

        val results = science.filterAndFlatten("science::al")
        assertEquals(listOf<String>(), results.map { it.lastDeckNameComponent })
    }

    @Test
    fun `search for non-existent path pattern returns no results`() {
        // Science::Math::Algebra::Group
        val group = makeNode("Group", 4, 4)
        val algebra = makeNode("Algebra", 3, 3, children = listOf(group))
        val math = makeNode("Math", 2, 2, collapsed = false, children = listOf(algebra))
        val science = makeNode("Science", 1, 1, children = listOf(math))

        val results = science.filterAndFlatten("foo")
        assertEquals(emptyList<String>(), results.map { it.lastDeckNameComponent })
    }

    @Test
    fun `search for multiple-term`() {
        // Science::Math::Algebra::Group
        // Science::Chemistry::Group
        val mathGroup = makeNode("Group", 4, 4)
        val algebra = makeNode("Algebra", 3, 3, children = listOf(mathGroup))
        val math = makeNode("Math", 2, 2, collapsed = false, children = listOf(algebra))
        val chemistryGroup = makeNode("Group", deckId = 6, level = 3)
        val chemistry = makeNode("Chemistry", deckId = 7, level = 2, children = listOf(chemistryGroup))
        val science = makeNode("Science", 1, 1, children = listOf(math, chemistry))

        val results = science.filterAndFlatten("ma gr")
        // Chemistry::group should not be found
        assertEquals(listOf<String>("Science", "Math", "Algebra", "Group"), results.map { it.lastDeckNameComponent })
        val results2 = science.filterAndFlatten("ist gr")
        // Math::group should not be found
        assertEquals(listOf<String>("Science", "Chemistry", "Group"), results2.map { it.lastDeckNameComponent })
    }
}

fun DeckNode.filterAndFlatten(filter: CharSequence) =
    this.filterAndFlattenDisplay(DeckFilters.create(filter), selectedDeckId = 1337).map {
        it.deckNode
    }
