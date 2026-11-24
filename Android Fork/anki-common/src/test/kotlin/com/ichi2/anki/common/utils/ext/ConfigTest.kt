// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import com.ichi2.anki.browser.search.SavedSearch
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.model.CardsOrNotes
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for extensions to [com.ichi2.anki.libanki.Config]
 */
class ConfigTest : InMemoryAnkiTest() {
    @Test
    fun `legacy settings retain their missing and invalid value defaults`() {
        val keys =
            listOf(
                "curDeck",
                "schedVer",
                "rollover",
                "estTimes",
                "dueCounts",
                "sortType",
                "noteSortType",
                "sortBackwards",
                "browserNoteSortBackwards",
            )

        fun assertDefaults() {
            assertEquals(1L, col.config.currentDeckId)
            assertEquals(1L, col.config.schedulerVersion)
            assertEquals(4, col.config.rolloverHour)
            assertTrue(col.config.showIntervalsOnButtons)
            assertTrue(col.config.showRemainingDueCounts)
            assertEquals("noteFld", col.config.browserSortCardsColumn)
            assertEquals("noteFld", col.config.browserSortNotesColumn)
            assertFalse(col.config.browserSortCardsBackwards)
            assertFalse(col.config.browserSortNotesBackwards)
        }

        keys.forEach { col.config.remove(it) }
        assertDefaults()
        keys.forEach { col.config.set(it, listOf("invalid")) }
        assertDefaults()
    }

    @Test
    fun `legacy settings observe values changed outside the accessors`() {
        col.config.set("curDeck", 123L)
        col.config.set("schedVer", 2L)
        col.config.set("rollover", 8)
        col.config.set("estTimes", false)
        col.config.set("dueCounts", false)

        assertEquals(123L, col.config.currentDeckId)
        assertEquals(2L, col.config.schedulerVersion)
        assertEquals(8, col.config.rolloverHour)
        assertFalse(col.config.showIntervalsOnButtons)
        assertFalse(col.config.showRemainingDueCounts)
    }

    @Test
    fun `FSRS diagnostics distinguish missing and invalid values`() {
        col.config.remove("fsrs")
        assertEquals(false, col.config.fsrsEnabled)

        col.config.set("fsrs", "invalid")
        assertNull(col.config.fsrsEnabled)
        col.config.set<Boolean?>("fsrs", null)
        assertNull(col.config.fsrsEnabled)

        col.config.set("fsrs", true)
        assertEquals(true, col.config.fsrsEnabled)
    }

    @Test
    fun `flag label objects must be saved explicitly and defaults are independent`() {
        col.config.remove("flagLabels")
        val first = col.config.flagLabels
        first.put("1", "Red")
        assertEquals(0, col.config.flagLabels.length())
        assertNotSame(first, col.config.flagLabels)

        col.config.flagLabels = first
        assertEquals("Red", col.config.getObject("flagLabels", JSONObject()).getString("1"))
        assertEquals("Red", col.config.flagLabels.getString("1"))

        col.config.set("flagLabels", listOf("invalid"))
        assertEquals(0, col.config.flagLabels.length())
        col.config.flagLabels.put("2", "Orange")
        assertEquals(0, col.config.flagLabels.length())
    }

    @Test
    fun `saved searches preserve the backend map format and recover from invalid values`() {
        col.config.savedFilters = listOf(SavedSearch("A", "deck:Default"), SavedSearch("a", "is:new"))
        assertEquals(mapOf("A" to "deck:Default", "a" to "is:new"), col.config.get<Map<String, String>>("savedFilters"))

        col.config.set("savedFilters", mapOf("external" to "is:due"))
        assertEquals(listOf(SavedSearch("external", "is:due")), col.config.savedFilters)
        col.config.set("savedFilters", listOf("invalid"))
        assertEquals(emptyList(), col.config.savedFilters)
        col.config.remove("savedFilters")
        assertEquals(emptyList(), col.config.savedFilters)
    }

    @Test
    fun `browser mode helpers use the mapped config property`() {
        CardsOrNotes.NOTES.saveToCollection(col)
        assertEquals(CardsOrNotes.NOTES, col.config.cardsOrNotes)
        assertEquals(CardsOrNotes.NOTES, CardsOrNotes.fromCollection(col))

        col.config.cardsOrNotes = CardsOrNotes.CARDS
        assertEquals(CardsOrNotes.CARDS, CardsOrNotes.fromCollection(col))
    }

    @Test
    fun `adding defaults mode uses the backend default`() {
        col.config.remove("addToCur")

        assertEquals(AddingDefaultsMode.USE_CURRENT_DECK, col.config.addingDefaultsMode)
    }

    @Test
    fun `adding defaults mode changes the backend destination`() {
        val studyDeck = addDeck("Study", setAsSelected = true)
        val destination = addDeck("Destination")
        col.addNote(col.newNote(col.notetypes.basic).apply { fields[0] = "saved" }, destination)

        col.config.addingDefaultsMode = AddingDefaultsMode.DECIDE_BY_NOTE_TYPE
        assertEquals(AddingDefaultsMode.DECIDE_BY_NOTE_TYPE, col.config.addingDefaultsMode)
        assertEquals(destination, col.defaultsForAdding().deckId)

        col.config.addingDefaultsMode = AddingDefaultsMode.USE_CURRENT_DECK
        assertEquals(AddingDefaultsMode.USE_CURRENT_DECK, col.config.addingDefaultsMode)
        assertEquals(studyDeck, col.defaultsForAdding().deckId)
        assertEquals(studyDeck, col.decks.selected())
    }

    @Test
    fun `test non-diacritic input`() {
        addBasicNote("uber")
        addBasicNote("über")
        addBasicNote("Über")

        assertEquals(1, col.findCards("uber").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("uber").size)
    }

    @Test
    fun `test diacritic input`() {
        addBasicNote("uber")
        addBasicNote("über")
        addBasicNote("Über")

        assertEquals(1, col.findCards("über").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("über").size)
    }

    @Test
    fun `test Japanese input`() {
        addBasicNote("は")
        addBasicNote("ば")
        addBasicNote("ぱ")

        assertEquals(1, col.findCards("は").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("は").size)
    }
}
