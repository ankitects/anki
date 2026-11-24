// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import com.ichi2.testutils.withViewModel
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/** Process death restore of [CardBrowserSearchViewModel] */
@RunWith(AndroidJUnit4::class)
class CardBrowserSearchViewModelRestoreTest : RobolectricTest() {
    @Test
    fun `Search tools mode and text survive process death after the search view was closed once`() =
        withPref(Prefs::devUsingCardBrowserSearchTools, true) {
            runTest {
                val handle = SavedStateHandle()
                withViewModel(CardBrowserSearchViewModel(handle)) {
                    // the user opened and closed the search view
                    resetSearchState(SearchRequest("")).join()

                    // the user reopens it, types a search and enables Search tools
                    isScreenOpenFlow.value = true
                    onSearchTextChanged("deck:current")
                    toggleAdvancedSearch()
                    assertThat("Search tools enabled before saving", advancedSearchFlow.value, equalTo(true))
                }

                // the state the SavedStateRegistry keeps across process death
                val saved = handle.savedStateProvider().saveState()
                withViewModel(CardBrowserSearchViewModel(SavedStateHandle.createHandle(saved, null))) {
                    assertThat("Search tools mode is restored", advancedSearchFlow.value, equalTo(true))
                    assertThat("search text is restored", searchTextFlow.value, equalTo("deck:current"))
                }
            }
        }
}
