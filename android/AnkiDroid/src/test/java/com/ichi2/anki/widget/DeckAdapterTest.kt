/*
 *  Copyright (c) 2026 Vedant Kakade <vedantkakade05@gmail.com>
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
package com.ichi2.anki.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.deckpicker.DeckFilters
import com.ichi2.anki.deckpicker.filterAndFlattenDisplay
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckAdapterTest : RobolectricTest() {
    @Test
    fun ensureDeckSelectionUpdatesCorrectly() {
        val deck1Id = addDeck("Deck 1")
        val deck2Id = addDeck("Deck 2")
        val deck3Id = addDeck("Deck 3")

        val node =
            col.sched.deckDueTree().filterAndFlattenDisplay(
                DeckFilters.create(""),
                deck1Id,
            )

        assertTrue(node.first { it.did == deck1Id }.isSelected)

        val afterDeck2 = node.map { it.withUpdatedDeckId(deck2Id) }
        assertFalse(actual = afterDeck2.first { it.did == deck1Id }.isSelected)
        assertTrue(actual = afterDeck2.first { it.did == deck2Id }.isSelected)

        val afterDeck3 = afterDeck2.map { it.withUpdatedDeckId(deck3Id) }
        assertFalse(actual = afterDeck3.first { it.did == deck2Id }.isSelected)
        assertTrue(actual = afterDeck3.first { it.did == deck3Id }.isSelected)
    }
}
