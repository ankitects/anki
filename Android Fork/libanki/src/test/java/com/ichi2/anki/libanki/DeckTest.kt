// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2025 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeckTest : InMemoryAnkiTest() {
    val d = Deck("{}")

    @Test
    fun `optional properties retain legacy defaults`() {
        assertFalse(d.browserCollapsed)
        assertFalse(d.descriptionAsMarkdown)
        assertEquals("", d.description)
        assertEquals(1L, d.conf)
        for (value in listOf(JSONObject.NULL, 0L, -1L, "invalid")) {
            d.jsonObject.put("conf", value)
            assertEquals(1L, d.conf)
        }
    }

    @Test
    fun `wrapping JSON preserves shared mutations`() {
        val json = JSONObject("""{"name":"before"}""")
        val deck = Deck(json)
        assertSame(json, deck.jsonObject)
        deck.name = "after"
        assertEquals("after", json.getString("name"))
        json.put("name", "updated")
        assertEquals("updated", deck.name)
    }

    @Test
    fun `deck serialization preserves unwrapped legacy fields`() {
        // Legacy learning steps are numbers in minutes, not strings such as "1h".
        val deck = Deck("""{"delays":[60,1],"previewAgainSecs":60,"previewHardSecs":600,"previewGoodSecs":0}""")
        deck.name = "Filtered"
        deck.resched = false
        val serialized = JSONObject(BackendUtils.toJsonBytes(deck).toStringUtf8())
        assertEquals(deck.jsonObject.toString(), deck.toString())
        assertEquals("Filtered", serialized.get("name"))
        assertEquals(false, serialized.get("resched"))
        assertEquals(60, serialized.get("previewAgainSecs"))
        assertEquals(600, serialized.get("previewHardSecs"))
        assertEquals(0, serialized.get("previewGoodSecs"))
        assertEquals("[60,1]", serialized.getJSONArray("delays").toString())
    }

    @Test
    fun `legacy filtered deck defaults and term changes persist`() {
        val id = col.decks.newFiltered("Filtered")
        val deck = col.decks.get(id)!!
        assertEquals(1, deck.jsonObject.getJSONArray("terms").length())
        assertEquals("", deck.firstFilter.search)
        assertEquals(0, deck.firstFilter.order)
        assertFalse(deck.browserCollapsed)
        assertFalse(deck.collapsed)
        deck.firstFilter.search = "is:new"
        deck.firstFilter.limit = 7
        deck.firstFilter.order = 2
        col.decks.save(deck)
        val saved = col.decks.get(id)!!
        assertEquals("is:new", saved.firstFilter.search)
        assertEquals(7, saved.firstFilter.limit)
        assertEquals(2, saved.firstFilter.order)
    }

    @Test
    fun testFiltered() {
        // `dyn` can't be set by the front-end anymore.
        val d = Deck("""{"dyn" :1}""")
        assertTrue(d.isFiltered)
        assertFalse(d.isNormal, "This deck should not be normal")
    }

    @Test
    fun testNormal() {
        val d = Deck("""{"dyn" :0}""")
        assertTrue(d.isNormal)
        assertFalse(d.isFiltered, "this deck should not be filtered")
    }

    @Test
    fun testName() {
        val name = "foo"
        d.name = name
        assertEquals(name, d.name)
    }

    @Test
    fun testBrowserCollapsed() {
        d.browserCollapsed = true
        assertTrue(d.browserCollapsed)
        d.browserCollapsed = false
        assertFalse(d.browserCollapsed, "browser should be collapsed")
    }

    @Test
    fun testCollapsed() {
        d.collapsed = true
        assertTrue(d.collapsed)
        d.collapsed = false
        assertFalse(d.collapsed, "deck should be collapsed")
    }

    @Test
    fun testId() {
        val id = 42L
        d.id = id
        assertEquals(id, d.id)
    }

    @Test
    fun testConfId() {
        val confId = 42L
        d.conf = confId
        assertEquals(confId, d.conf)
    }

    @Test
    fun testDescription() {
        val description = "foo"
        d.description = description
        assertEquals(description, d.description)
    }

    @Test
    fun testNoteTypeId() {
        val noteTypeId = 42L
        d.noteTypeId = noteTypeId
        assertEquals(noteTypeId, d.noteTypeId)
    }

    @Test
    fun testResched() {
        d.resched = true
        assertTrue(d.resched)
        d.resched = false
        assertTrue(!d.resched)
    }

    val search = "search"
    val limit = 7
    val order = 42
    val t = Deck.Term(search, limit, order)

    @Test
    fun testSearch() {
        val expectedSearch = "expectedSearch"
        t.search = expectedSearch
        assertEquals(expectedSearch, t.search)
    }

    @Test
    fun testLimit() {
        val expectedLimit = 10
        t.limit = expectedLimit
        assertEquals(expectedLimit, t.limit)
    }

    @Test
    fun testOrder() {
        val expectedOrder = 7
        t.order = expectedOrder
        assertEquals(expectedOrder, t.order)
    }

    @Test
    fun testFirstFilter() {
        // All decks are expected to have at least one term.
        val d = Deck("""{"terms": [$t]}""")
        val firstFilter = d.firstFilter
        assertEquals(firstFilter.search, search)
        assertEquals(firstFilter.limit, limit)
        assertEquals(firstFilter.order, order)
    }

    @Test
    fun `second filter updates the legacy array without changing the first filter`() {
        val deck = Deck("""{"terms":[["first",5,0]]}""")
        val terms = deck.jsonObject.getJSONArray("terms")
        val first = terms.getJSONArray(0)
        assertNull(deck.secondFilter)
        deck.secondFilter = null
        assertEquals(1, terms.length())

        val second = Deck.Term("second", 10, 1)
        deck.secondFilter = second
        assertEquals(2, terms.length())
        assertSame(second.array, terms.getJSONArray(1))
        deck.secondFilter!!.search = "updated"
        assertEquals("updated", terms.getJSONArray(1).getString(0))

        val replacement = Deck.Term("replacement", 20, 2)
        deck.secondFilter = replacement
        assertEquals(2, terms.length())
        assertSame(replacement.array, terms.getJSONArray(1))

        deck.secondFilter = null
        assertNull(deck.secondFilter)
        assertEquals(1, terms.length())
        assertSame(first, terms.getJSONArray(0))
    }
}
