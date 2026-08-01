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

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.SearchHistory
import com.ichi2.anki.browser.SearchHistory.SearchHistoryEntry
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel.Companion.MAX_SEARCH_HISTORY_ENTRIES
import com.ichi2.anki.browser.search.CardBrowserSearchViewModel.UserMessage
import com.ichi2.testutils.assertFalse
import kotlinx.coroutines.flow.map
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests for [CardBrowserSearchViewModel] */
@RunWith(AndroidJUnit4::class) // PERF: only required for `Prefs`
class CardBrowserSearchViewModelTest : RobolectricTest() {
    @Before
    override fun setUp() {
        SearchHistory().clear()
        super.setUp()
    }

    @Test
    fun `initial state`() =
        withViewModel {
            // TODO: design the initial state
            assertThat("no initial search history", searchHistory, empty())
            searchHistoryAvailableFlow.test {
                assertFalse("no history => history unavailable", expectMostRecentItem())
            }
        }

    @Test
    fun `initial state with no cards`() =
        withViewModel(cardCount = 0) {
            // TODO: design the initial state
            assertThat("no initial search history", searchHistory, empty())
            searchHistoryAvailableFlow.test {
                assertFalse("no history => history unavailable", expectMostRecentItem())
            }
        }

    @Test
    fun `search updated after submit`() =
        withViewModel {
            searchHistoryListFlow.test {
                submitSearch("Hello")

                val mostRecentItem = expectMostRecentItem()
                assertThat(mostRecentItem, not(empty()))
                val item = mostRecentItem.single()
                assertThat(item, equalTo(SearchHistoryEntry("Hello")))
            }
        }

    @Test
    fun `search closed after submit`() =
        withViewModel {
            closeSearchViewFlow.test {
                expectNoEvents()
                submitSearch("Hello")
                expectMostRecentItem()
            }
        }

    @Test
    fun `selecting search history entry`() =
        withViewModel {
            submittedSearchFlow.test {
                submitSearch("Hello")
                assertThat(expectMostRecentItem()?.toSearchString(), equalTo("Hello"))
            }
        }

    @Test
    fun `appendAdvancedSearch appends spacing`() =
        withViewModel {
            this.searchTextFlow.test {
                assertEquals("", this.expectMostRecentItem())

                toggleAdvancedSearch()

                appendAdvancedSearch("hello")
                appendAdvancedSearch("deck:aa")

                // space at the end: if a user types, it does not interfere with previous append calls
                assertEquals("hello deck:aa ", this.expectMostRecentItem())
            }
        }

    @Test
    fun `onSearchTextChanged takes priority over appendAdvancedSearch`() =
        withViewModel {
            this.searchTextFlow.test {
                assertEquals("", this.expectMostRecentItem())

                toggleAdvancedSearch()

                appendAdvancedSearch("hello")
                onSearchTextChanged("replaced")

                // the append call had no effect
                assertEquals("replaced", this.expectMostRecentItem())
            }
        }

    @Test
    fun `text can be updated when a different screen is selected`() =
        withViewModel {
            searchTextFlow.test {
                expectMostRecentItem()

                appendAdvancedSearch("advanced, while in standard")

                toggleAdvancedSearch()

                assertEquals("advanced, while in standard ", expectMostRecentItem())
            }
        }

    @Test
    fun `text can be updated in Basic mode`() =
        withViewModel {
            searchTextFlow.test {
                expectMostRecentItem()

                onSearchTextChanged("hi")

                assertEquals("hi", expectMostRecentItem())
            }
        }

    @Test
    fun `submitSearch trims spaces`() =
        withViewModel {
            submittedSearchFlow.test {
                submitSearch(" aa ")
                assertThat(expectMostRecentItem()?.toSearchString(), equalTo("aa"))
            }
        }

    // there's no point in having this in the history as a user can trivially clear the search box.
    @Test
    fun `a submitted empty string does not appear in the history`() =
        withViewModel {
            submittedSearchFlow.test {
                submitSearch("")
                assertThat(searchHistory, empty())
                // matches Anki Desktop
                assertThat(expectMostRecentItem()?.toSearchString(), equalTo("deck:*"))
            }
        }

    @Test
    fun `search history - submitted searches are trimmed and deduplicated`() =
        withViewModel {
            searchHistoryListFlow.test {
                submitSearch("aa")
                assertThat(expectMostRecentItem(), hasSize(1))

                submitSearch(" aa ")
                expectNoEvents()

                // confirm a new event would have appeared
                submitSearch(" bb ")
                expectMostRecentItem()
            }
        }

    @Test
    fun `add saved search - success modifies saved searches`() =
        withViewModel {
            savedSearchesFlow.test {
                addSavedSearch(SavedSearch("a", "b"))

                val item = expectMostRecentItem().single()
                assertEquals("a", item.name)
                assertEquals("b", item.query)
            }
        }

    @Test
    fun `add saved search - success outputs message`() =
        withViewModel {
            userMessageFlow.test {
                addSavedSearch(SavedSearch("a", "b"))
                assertEquals(UserMessage.SEARCH_SAVED, expectMostRecentItem())
            }
        }

    @Test
    fun `add duplicate saved search  - searches are not updated`() =
        withViewModel {
            savedSearchesFlow.test {
                addSavedSearch(SavedSearch("a", "b"))
                addSavedSearch(SavedSearch("a", "aa"))

                val item = expectMostRecentItem().single()
                assertEquals("a", item.name)
                assertEquals("b", item.query)
            }
        }

    @Test
    fun `add duplicate saved search - user is warned`() =
        withViewModel {
            addSavedSearch(SavedSearch("a", "b"))
            userMessageFlow.test {
                addSavedSearch(SavedSearch("a", "aa"))
                assertEquals(UserMessage.SAVED_SEARCH_DUPLICATE_ADDED, expectMostRecentItem())
            }
        }

    @Test
    fun `delete a valid saved search modifies list`() =
        withViewModel {
            addSavedSearch(SavedSearch("a", "b"))

            savedSearchesFlow.test {
                deleteSavedSearch(savedSearches.single())
                assertEquals(0, expectMostRecentItem().size)
            }
        }

    @Test
    fun `delete a valid saved search outputs a message`() =
        withViewModel {
            addSavedSearch(SavedSearch("a", "b"))

            userMessageFlow.test {
                deleteSavedSearch(savedSearches.single())
                assertEquals(UserMessage.SAVED_SEARCH_DELETED, expectMostRecentItem())
            }
        }

    @Test
    fun `delete saved search not found`() =
        withViewModel {
            userMessageFlow.test {
                deleteSavedSearch(SavedSearch("does not exist", ""))
                assertEquals(UserMessage.SAVED_SEARCH_NAME_DOES_NOT_EXIST, expectMostRecentItem())
            }
        }

    @Test
    fun `submit saved search - current text is updated`() =
        withViewModel {
            addSavedSearch(SavedSearch("A", "sample query"))

            searchTextFlow.test {
                assertEquals("", expectMostRecentItem())
                submitSavedSearch(savedSearches.single())
                assertEquals("sample query", expectMostRecentItem())
            }
        }

    @Test
    fun `submit saved search - history is updated`() =
        withViewModel {
            addSavedSearch(SavedSearch("A", "sample query"))

            searchHistoryListFlow.test {
                // nothing in history initially
                assertEquals(0, expectMostRecentItem().size)

                submitSavedSearch(savedSearches.single())
                val history = expectMostRecentItem().single()
                assertEquals("sample query", history.query)
            }
        }

    @Test
    fun `submit saved search - view is closed`() =
        withViewModel {
            addSavedSearch(SavedSearch("A", "sample query"))

            closeSearchViewFlow.test {
                expectNoEvents()
                submitSavedSearch(savedSearches.single())
                expectMostRecentItem()
            }
        }

    @Test
    fun `submit saved search - search is submitted`() =
        withViewModel {
            addSavedSearch(SavedSearch("A", "sample query"))

            submittedSearchFlow.test {
                expectNoEvents()
                submitSavedSearch(savedSearches.single())
                assertEquals("sample query", expectMostRecentItem()?.toSearchString())
            }
        }

    @Test
    fun `apply saved search - blank search is replaced with trailing space`() =
        withViewModel {
            addSavedSearch(SavedSearch("A", "sample query"))

            searchTextFlow.test {
                applySavedSearch(savedSearches.single())

                assertEquals("sample query ", expectMostRecentItem())
            }
        }

    @Test
    fun `apply saved search - existing search is replaced`() =
        withViewModel {
            addSavedSearch(SavedSearch("A", "sample query"))

            onSearchTextChanged("testing")
            searchTextFlow.test {
                assertEquals("testing", expectMostRecentItem())
                applySavedSearch(savedSearches.single())

                assertEquals("sample query ", expectMostRecentItem())
            }
        }

    @Test
    fun `saved searches can only be managed if they exist`() =
        withViewModel {
            canManageSavedSearchesFlow.test {
                assertEquals(false, expectMostRecentItem())

                addSavedSearch(SavedSearch("A", "sample query"))

                assertEquals(true, expectMostRecentItem())

                deleteSavedSearch(SavedSearch("A", "sample query"))

                assertEquals(false, expectMostRecentItem())
            }
        }

    @Test
    fun `search history - initially collapsed`() =
        withViewModel {
            assertThat("initially not expanded", isHistoryExpandedFlow.value, equalTo(false))
        }

    @Test
    fun `search history - toggle expands and collapses`() =
        withViewModel {
            isHistoryExpandedFlow.test {
                assertThat(expectMostRecentItem(), equalTo(false))
                toggleHistoryExpanded()
                assertThat(expectMostRecentItem(), equalTo(true))
                toggleHistoryExpanded()
                assertThat(expectMostRecentItem(), equalTo(false))
            }
        }

    @Test
    fun `search history - displayed list is truncated when collapsed`() =
        withViewModel {
            repeat(MAX_SEARCH_HISTORY_ENTRIES + 3) { i ->
                submitSearch("search $i")
            }
            displayedSearchHistoryFlow.test {
                val items = expectMostRecentItem()
                assertThat(items.entryToSearchString, hasSize(MAX_SEARCH_HISTORY_ENTRIES))
            }
        }

    @Test
    fun `search history - displayed list shows all when expanded`() =
        withViewModel {
            val totalItems = MAX_SEARCH_HISTORY_ENTRIES + 3
            repeat(totalItems) { i ->
                submitSearch("search $i")
            }
            toggleHistoryExpanded()
            displayedSearchHistoryFlow.test {
                val items = expectMostRecentItem()
                assertThat(items.entryToSearchString, hasSize(totalItems))
            }
        }

    @Test
    fun `search history - toggle button visible only when items exceed max`() =
        withViewModel {
            showHistoryToggleFlow.test {
                assertThat(expectMostRecentItem(), equalTo(false))
            }

            repeat(MAX_SEARCH_HISTORY_ENTRIES) { i ->
                submitSearch("search $i")
            }
            showHistoryToggleFlow.test {
                assertThat(expectMostRecentItem(), equalTo(false))
            }

            submitSearch("one more search")
            showHistoryToggleFlow.test {
                assertThat(expectMostRecentItem(), equalTo(true))
            }
        }

    fun withViewModel(
        cardCount: Int = 1,
        block: suspend CardBrowserSearchViewModel.() -> Unit,
    ) = runTest {
        addNotes(count = cardCount)
        block(
            CardBrowserSearchViewModel(
                SavedStateHandle(),
            ),
        )
    }
}

/**
 * @see com.ichi2.anki.browser.SearchHistory
 */
val CardBrowserSearchViewModel.searchHistoryListFlow get() =
    this.searchHistoryFlow.map { it.entryToSearchString.map { it.first } }

/**
 * @see com.ichi2.anki.browser.SearchHistory
 */
val CardBrowserSearchViewModel.searchHistory get() =
    this.searchHistoryFlow.value.entryToSearchString
        .map { it.first }

val CardBrowserSearchViewModel.savedSearches
    get() = savedSearchesFlow.value

fun CardBrowserSearchViewModel.submitSearch(submittedText: String) {
    onSearchTextChanged(submittedText)
    submitCurrentSearch()
}
