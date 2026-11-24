// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SavedSearchesTest : JvmTest() {
    @Test
    fun `matches Anki Desktop ordering`() =
        withSavedSearches {
            add("red")
            add("blue")
            val (_, result) = add("green")

            assertThat(result.map { it.name }, equalTo(listOf("blue", "green", "red")))
        }

    @Test
    fun `searches are case sensitive`() =
        withSavedSearches {
            add("a")
            add("A")
            val (_, result) = add("Z")

            assertThat(result.map { it.name }, equalTo(listOf("A", "Z", "a")))
        }

    @Test
    fun `add fails on name clash`() =
        withSavedSearches {
            add(SavedSearch("a", "b")).also { (success, values) ->
                assertTrue(success)
                assertThat(values, hasSize(1))
                assertThat(values.single(), equalTo(SavedSearch("a", "b")))
            }

            // success: false; no change in values
            add(SavedSearch("a", "c")).also { (success, values) ->
                assertFalse(success)
                assertThat(values, hasSize(1))
                assertThat(values.single(), equalTo(SavedSearch("a", "b")))
            }
        }

    @Test
    fun `add normalizes query`() =
        withSavedSearches {
            add(SavedSearch("a", " b ")).also { (success, values) ->
                assertTrue(success)
                val value = assertNotNull(values.singleOrNull())
                assertThat("query is trimmed", value, equalTo(SavedSearch("a", "b")))
            }
        }

    @Test
    fun `remove by name - found`() =
        withSavedSearches {
            add("a")
            add("b")
            val (success, values) = removeByName("a")
            assertTrue(success)
            assertThat(values, hasSize(1))
            assertThat(values.single(), equalTo(SavedSearch("b", "b")))
        }

    @Test
    fun `remove by name - missing`() =
        withSavedSearches {
            val (success, _) = removeByName("not found")
            assertFalse(success)
        }

    @Test
    fun `test clear`() =
        withSavedSearches {
            clear()
            add("a")
            add("b")
            clear()
            assertThat(loadFromConfig(), empty())
        }

    @Test
    fun `byName - search`() =
        withSavedSearches {
            add("a")
            assertThat(byName("a"), equalTo(SavedSearch("a", "a")))
        }

    @Test
    fun `byName - case sensitive search`() =
        withSavedSearches {
            add("A")
            add("a")
            assertThat(byName("a"), equalTo(SavedSearch("a", "a")))
        }

    @Test
    fun `byName - not found returns null`() =
        withSavedSearches {
            assertNull(byName("a"))
        }

    private fun withSavedSearches(block: suspend SavedSearches.() -> Unit) =
        runTest {
            block(SavedSearches)
        }
}

context(_: SavedSearches)
suspend fun add(data: String) = SavedSearches.add(SavedSearch(data, data))
