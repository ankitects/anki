// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.libanki.exception.InvalidSearchException
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tests for [SearchString] */
class SearchStringTest : JvmTest() {
    @Test
    fun `valid searchString from string`() {
        val result = fromUserInput("hello")
        val searchString = result.getOrThrow()
        assertEquals("hello", searchString.value)
    }

    @Test
    fun `invalid searchString from string`() {
        val result = fromUserInput("and")
        val ex = assertFailsWith<InvalidSearchException> { result.getOrThrow() }
        // Invalid search: an `and` was found but it is not connecting two search terms.
        assertContains(ex.message!!, "not connecting two search terms")
    }

    @Test
    fun `a non-breaking space before OR is read as a space`() {
        val noteIds = listOf("t1", "t2", "t3").map { tag -> addBasicNote().update { tags = mutableListOf(tag) }.id }

        val search = fromUserInput("(tag:t3 OR tag:t2\u00a0OR tag:t1)").getOrThrow()

        assertEquals("tag:t3 OR tag:t2 OR tag:t1", search.value)
        assertEquals(noteIds.toSet(), col.findNotes(search).toSet())
    }

    @Test
    fun `a searchNode list is transformed to a valid string`() {
        val deckSearchNode = searchNode { deck = "Default" }
        val querySearchNode = searchNode { parsableText = "hi" }
        val result = fromNodes(listOf(deckSearchNode, querySearchNode)).getOrThrow()
        assertEquals("deck:Default hi", result.value)
    }

    @Test
    fun `a failure is produced from an invalid searchNode list`() {
        val invalidSearchNode = searchNode { parsableText = "and" }
        val result = fromNodes(listOf(invalidSearchNode))
        val ex = assertFailsWith<InvalidSearchException> { result.getOrThrow() }
        assertContains(ex.message!!, "not connecting two search terms.")
    }

    @Test
    fun `a failure is produced from an empty searchNode list`() {
        val result = fromNodes(emptyList())
        val ex = assertFailsWith<IllegalArgumentException> { result.getOrThrow() }
        assertContains(ex.message!!, "At least one entry must be provided")
    }
}

context(test: AnkiTest)
private fun fromUserInput(input: String): Result<SearchString> = with(test.col) { SearchString.fromUserInput(input) }

context(test: AnkiTest)
private fun fromNodes(input: List<SearchNode>): Result<SearchString> = with(test.col) { SearchString.fromNodeList(input) }
