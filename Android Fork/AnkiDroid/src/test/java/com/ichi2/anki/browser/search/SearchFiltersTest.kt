// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.libanki.DeckNameId
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.not
import org.junit.Test

/** Tests for [SearchFilters] */
class SearchFiltersTest {
    @Test
    fun `activeFilters is empty by default`() {
        val filters = SearchFilters.EMPTY
        assertThat(filters.activeFilters, empty())
    }

    @Test
    fun `activeFilters is non-empty if a filter is set`() {
        val filters = SearchFilters.partial(decks = listOf(DeckNameId("Default", 1)))
        assertThat(filters.activeFilters, not(empty()))
    }
}
