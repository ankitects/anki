/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.libanki.exception.InvalidSearchException
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tests for [SearchString] */
@RunWith(AndroidJUnit4::class)
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
