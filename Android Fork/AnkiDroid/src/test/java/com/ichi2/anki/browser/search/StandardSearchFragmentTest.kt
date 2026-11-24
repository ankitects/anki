// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.withCardBrowserFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Test of [StandardSearchFragment] */
@RunWith(AndroidJUnit4::class)
class StandardSearchFragmentTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        SearchHistory().clear()
    }

    @Test
    fun `history entries are loaded`() {
        SearchHistory().addRecent(SearchHistoryEntry("aa"))

        withFragment {
            assertEquals(1, binding.searchHistory.count)
        }
    }

    @Test
    fun `history entries are truncated`() {
        val expectedMaxEntries = 5
        assertEquals(expectedMaxEntries, CardBrowserSearchViewModel.MAX_SEARCH_HISTORY_ENTRIES)

        val history = SearchHistory()
        repeat(expectedMaxEntries + 1) {
            history.addRecent(SearchHistoryEntry(it.toString()))
        }

        withFragment {
            assertEquals(expectedMaxEntries, binding.searchHistory.count)
            assertEquals(expectedMaxEntries + 1, viewModel.searchHistory.size)
        }
    }

    @Test
    fun `see more button is hidden when entries do not exceed limit`() {
        val history = SearchHistory()
        repeat(3) { history.addRecent(SearchHistoryEntry(it.toString())) }

        withFragment {
            assertFalse(binding.toggleSearchHistory.isVisible)
        }
    }

    @Test
    fun `see more button is visible when entries exceed limit`() {
        val history = SearchHistory()
        repeat(CardBrowserSearchViewModel.MAX_SEARCH_HISTORY_ENTRIES + 1) {
            history.addRecent(SearchHistoryEntry(it.toString()))
        }

        withFragment {
            assertTrue(binding.toggleSearchHistory.isVisible)
        }
    }

    fun withFragment(block: StandardSearchFragment.() -> Unit) =
        withCardBrowserFragment(useSearchView = true) {
            val targetFragment = requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
            block(targetFragment)
        }
}

fun <T : Fragment> Fragment.requireChildFragment(tag: String): T {
    @Suppress("UNCHECKED_CAST")
    return requireNotNull(this.childFragmentManager.findFragmentByTag(tag) as? T?)
    { "can't find tag $tag" }
}
