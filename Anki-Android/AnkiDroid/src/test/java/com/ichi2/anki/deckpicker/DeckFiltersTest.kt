/*
 *  Copyright (c) 2026 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.anki.deckpicker

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.deckpicker.DeckFilters.DeckFilter.Companion.containsAtPosition
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class) // This is necessary, android and JVM differ on JSONObject.NULL
@Config(application = EmptyApplication::class)
class DeckFiltersTest : AndroidTest {
    // The lowercase method implementation are sometime buggy for the dotless ı, so we must test them separately.
    val upperDotlessI = "I"
    val upperDottedI = "I"
    val lowerDotlessI = "I"
    val lowerDottedI = "i"

    @Test
    fun testIsActive() {
        assertTrue(filters.isActive())
        assertFalse(DeckFilters(listOf()).isActive())
    }

    @Test
    @Config(qualifiers = "tr")
    fun testContainsAtPosition() {
        assertTrue(containsAtPosition("o::ba", 1, "foo::bar", 3))
        assertTrue(containsAtPosition("o::ba", 1, "foo::ba", 3))
        assertTrue(containsAtPosition("o::ba", 1, "o::bar", 1))
        assertTrue(containsAtPosition("o::ba", 1, "o::ba", 1))

        // Wrong position
        assertFalse(containsAtPosition("o::ba", 1, "foo::bar", 1))
        assertFalse(containsAtPosition("o::ba", 1, "foo::bar", 5))
        assertFalse(containsAtPosition("o::ba", 1, "foo::bar", 5))

        // Not enough characters in containing
        assertFalse(containsAtPosition("o::ba", 1, "foo::", 3))
        assertFalse(containsAtPosition("o::ba", 1, "::bar", 0))

        // Test for dotless and dotted i.
        // It seems both letters are considered equal (ignoring case). Which can cause too many results to be displayed, but at least all relevant results are displayed.
        assertTrue(containsAtPosition(lowerDotlessI, 1, lowerDotlessI, 1))
        assertTrue(containsAtPosition(lowerDotlessI, 1, upperDotlessI, 1))
        assertTrue(containsAtPosition(lowerDotlessI, 1, upperDottedI, 1))
        assertTrue(containsAtPosition(lowerDotlessI, 1, lowerDottedI, 1))
        assertTrue(containsAtPosition(upperDotlessI, 1, upperDotlessI, 1))
        assertTrue(containsAtPosition(upperDotlessI, 1, upperDottedI, 1))
        assertTrue(containsAtPosition(upperDotlessI, 1, lowerDottedI, 1))
        assertTrue(containsAtPosition(lowerDottedI, 1, upperDottedI, 1))
        assertTrue(containsAtPosition(lowerDottedI, 1, lowerDottedI, 1))
        assertTrue(containsAtPosition(upperDottedI, 1, upperDottedI, 1))
    }

    @Test
    fun testDeckLastNameMatchesFilter() {
        val nestedDecksFilter = DeckFilters.DeckFilter("o::ba")
        assertTrue(nestedDecksFilter.deckLastNameMatchesFilter("foo::bar"))
        assertFalse(nestedDecksFilter.deckLastNameMatchesFilter("foo::bar::buz"))
        assertFalse(nestedDecksFilter.deckLastNameMatchesFilter("foo::buz::bar"))
        assertFalse(nestedDecksFilter.deckLastNameMatchesFilter("foo::b"))

        val singleDeckFilter = DeckFilters.DeckFilter("u")
        assertFalse(singleDeckFilter.deckLastNameMatchesFilter("foo::bar"))
        assertTrue(singleDeckFilter.deckLastNameMatchesFilter("foo::bar::buz"))
        assertFalse(singleDeckFilter.deckLastNameMatchesFilter("foo::bu::bar"))
        assertFalse(singleDeckFilter.deckLastNameMatchesFilter("foo::b"))

        val partialFilterOne = DeckFilters.DeckFilter("u:")
        assertFalse(partialFilterOne.deckLastNameMatchesFilter("foo::bar"))
        // Ideally, this case should be true only if "foo::bar::bu" has a subdeck.
        // Due to the extra complexity of the implementation, and how little it would improve the user experience,
        // we decide to assume that all decks potentially have subdecks for this search filtering.
        assertTrue(partialFilterOne.deckLastNameMatchesFilter("foo::bar::bu"))
        assertFalse(partialFilterOne.deckLastNameMatchesFilter("foo::bu::bar"))
        assertFalse(partialFilterOne.deckLastNameMatchesFilter("foo::b"))

        val partialFilterTwo = DeckFilters.DeckFilter("u::")
        assertFalse(partialFilterTwo.deckLastNameMatchesFilter("foo::bar"))
        assertFalse(partialFilterTwo.deckLastNameMatchesFilter("foo::bar::bu"))
        assertTrue(partialFilterTwo.deckLastNameMatchesFilter("foo::bar::bu::plop"))
    }

    @Test
    fun testDeckNameMatchesFilter() {
        val singleDeckFilter = DeckFilters.DeckFilter("u")
        assertTrue(singleDeckFilter.deckNameMatchesFilter("foo::buz::bar"))
        assertTrue(singleDeckFilter.deckNameMatchesFilter("auie"))
        assertFalse(singleDeckFilter.deckNameMatchesFilter("aie"))

        val partialFilterOne = DeckFilters.DeckFilter("u:")
        assertTrue(partialFilterOne.deckNameMatchesFilter("foo::bu::plop"))
        assertTrue(partialFilterOne.deckNameMatchesFilter("foo::bu"))
    }

    val nestedDecksFilter = DeckFilters.DeckFilter("o::ba")
    val partialFilterTwo = DeckFilters.DeckFilter("u::")
    val filters = DeckFilters(listOf(DeckFilters.DeckFilter("u"), nestedDecksFilter))

    @Test
    fun testDeckLastNameMatchesAFilter() {
        assertTrue(filters.deckLastNameMatchesAFilter("foo::bar"))
        assertFalse(filters.deckLastNameMatchesAFilter("foo::buz::bar"))
        assertTrue(filters.deckLastNameMatchesAFilter("foo::bar::buz"))
        assertTrue(filters.deckLastNameMatchesAFilter("buz::foo::bar"))
        assertFalse(filters.deckLastNameMatchesAFilter("buz::foo::b"))
    }

    @Test
    fun testDeckNamesMatchFilter() {
        assertFalse(filters.deckNamesMatchFilters("foo::buz::bar"))
        assertTrue(filters.deckNamesMatchFilters("foo::bar::buz"))
        assertTrue(filters.deckNamesMatchFilters("buz::foo::bar"))
        assertFalse(filters.deckNamesMatchFilters("buz::foo::b"))
        assertFalse(filters.deckNamesMatchFilters("foo::bar::bz"))
    }

    @Test
    fun testAccept() {
        // Accepts exactly decks that either:
        // * ends with a deck containing "u", and another position contains "o::ba"
        // * ends with a deck starting with "ba", and second to last decks ends with "o", and some position contains a u
        assertTrue(DeckFilters(listOf()).accept("foo"))
        assertTrue(filters.accept("foo::bar::buz"))
        assertFalse(filters.accept("foo::bar::buz::plop"))
        assertTrue(filters.accept("buz::foo::bar"))
        assertTrue(filters.accept("buz::plop::foo::bar"))
        assertFalse(filters.accept("buz::plop::foo::plop::bar"))
        assertFalse(filters.accept("buz"))

        // Accepts exactly decks that either:
        // * ends with a deck ending in "u", and another position contains "o::ba"
        // * ends with a deck starting with "ba", and second to last decks ends with "o", and some parent deck name ends with a "u"
        val partialFiltersOne = DeckFilters(listOf(DeckFilters.DeckFilter("u:"), nestedDecksFilter))
        assertTrue(partialFiltersOne.accept("bu::foo::bar"))
        assertFalse(partialFiltersOne.accept("buz::foo::bar"))
        assertTrue(partialFiltersOne.accept("foo::bar::bu"))
        assertFalse(partialFiltersOne.accept("foo::bar::buz"))

        // Accepts exactly decks that either:
        // * second to last deck ends with a "u", and another position contains "o::ba"
        // * ends with a deck starting with "ba", and second to last decks ends with "o", and some parent deck name ends with a "u"
        val partialFiltersTwo = DeckFilters(listOf(partialFilterTwo, nestedDecksFilter))
        assertFalse(partialFiltersTwo.accept("foo::bar::bu"))
        assertFalse(partialFiltersOne.accept("foo::bar::buz"))
        assertTrue(partialFiltersTwo.accept("foo::bar::bu::plop"))
        assertTrue(partialFiltersTwo.accept("bu::foo::bar"))
    }
}
