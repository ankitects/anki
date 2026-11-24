// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import anki.collection.OpChanges
import app.cash.turbine.test
import com.ichi2.anki.browser.BrowserEvent.CollectionChanged
import com.ichi2.anki.browser.BrowserEvent.CollectionChanged.Origin
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CardBrowserCollectionChangedTest : JvmCardBrowserViewModelTestBase() {
    private val relevantChanges =
        listOf(
            OpChanges.newBuilder().setBrowserSidebar(true).build(),
            OpChanges.newBuilder().setBrowserTable(true).build(),
            OpChanges.newBuilder().setNoteText(true).build(),
            OpChanges.newBuilder().setCard(true).build(),
        )

    @Test
    fun `browser operations preserve search and selection`() {
        for (changes in relevantChanges) {
            assertChangesIgnored(CollectionChanged(changes, Origin.BROWSER))
        }
    }

    @Test
    fun `irrelevant collection changes preserve search and selection`() {
        for (origin in listOf(Origin.NOTE_EDITOR, Origin.EXTERNAL)) {
            assertChangesIgnored(CollectionChanged(OpChanges.getDefaultInstance(), origin))
        }
    }

    @Test
    fun `note editor changes refresh results and request count feedback`() =
        assertChangesRefreshSearch(Origin.NOTE_EDITOR, BrowserSearchFeedbackMode.COUNT_ONLY)

    @Test
    fun `external changes refresh results without feedback`() = assertChangesRefreshSearch(Origin.EXTERNAL, BrowserSearchFeedbackMode.NONE)

    private fun assertChangesIgnored(event: CollectionChanged) =
        runViewModelTest(notes = 1) {
            searchJob?.join()
            selectRowAtPosition(0)
            val selectedRow = cards.first()
            val initialSearch = searchJob

            uiEffects.receiveAsFlow().test {
                onEvent(event)
                searchJob?.join()
                expectNoEvents()
            }

            assertSame(initialSearch, searchJob)
            assertEquals(setOf(selectedRow), selectedRows)
            assertTrue(isInMultiSelectMode)
        }

    private fun assertChangesRefreshSearch(
        origin: Origin,
        feedbackMode: BrowserSearchFeedbackMode,
    ) = runViewModelTest(notes = 1) {
        searchJob?.join()
        flowOfSearchState.filterIsInstance<SearchState.Completed>().test {
            for ((index, changes) in relevantChanges.withIndex()) {
                // Add a row without notifying the browser, so stale results are observable.
                addBasicNote("new $index", "back")
                selectRowAtPosition(0)
                assertTrue(isInMultiSelectMode)

                uiEffects.receiveAsFlow().test {
                    onEvent(CollectionChanged(changes, origin))
                    assertEquals(BrowserUiEffect.RefreshControls, awaitItem())
                    expectNoEvents()
                }

                val completed = awaitItem()
                assertEquals(index + 2, completed.rowCount)
                assertEquals(feedbackMode, completed.feedbackMode)
                assertTrue(selectedRows.isEmpty())
                assertFalse(isInMultiSelectMode)
                searchJob?.join()
                if (feedbackMode == BrowserSearchFeedbackMode.NONE) {
                    assertNull(flowOfSearchFeedback.value)
                } else {
                    assertEquals(feedbackMode, flowOfSearchFeedback.value?.feedbackMode)
                }
            }
        }
    }
}
