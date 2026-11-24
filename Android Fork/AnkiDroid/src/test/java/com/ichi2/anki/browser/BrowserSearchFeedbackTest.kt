// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.browser.BrowserSearchFeedback.COUNT
import com.ichi2.anki.browser.BrowserSearchFeedback.COUNT_WITH_SEARCH_ALL_DECKS
import com.ichi2.anki.browser.BrowserSearchFeedback.NONE
import com.ichi2.anki.browser.BrowserSearchFeedback.NO_CARDS_IN_SELECTED_DECK
import org.junit.Test
import kotlin.test.assertEquals

/** Feedback rules tested without Android views or a collection. */
class BrowserSearchFeedbackTest {
    @Test
    fun `count-only feedback shows a hidden count regardless of scope or number of results`() {
        for (allDecks in listOf(false, true)) {
            for (count in listOf(0, 2)) {
                assertEquals(
                    COUNT,
                    browserSearchFeedback(
                        feedbackMode = BrowserSearchFeedbackMode.COUNT_ONLY,
                        rowCount = count,
                        allDecksSelected = allDecks,
                        isHeaderCountVisible = false,
                    ),
                )
                assertEquals(
                    NONE,
                    browserSearchFeedback(
                        feedbackMode = BrowserSearchFeedbackMode.COUNT_ONLY,
                        rowCount = count,
                        allDecksSelected = allDecks,
                        isHeaderCountVisible = true,
                    ),
                )
            }
        }
    }

    @Test
    fun `disabled feedback stays quiet even when an empty deck could offer an action`() {
        for (headerVisible in listOf(false, true)) {
            assertEquals(
                NONE,
                browserSearchFeedback(
                    feedbackMode = BrowserSearchFeedbackMode.NONE,
                    rowCount = 0,
                    allDecksSelected = false,
                    isHeaderCountVisible = headerVisible,
                ),
            )
            assertEquals(
                NONE,
                browserSearchFeedback(
                    feedbackMode = BrowserSearchFeedbackMode.NONE,
                    rowCount = 2,
                    allDecksSelected = false,
                    isHeaderCountVisible = headerVisible,
                ),
            )
            assertEquals(
                NONE,
                browserSearchFeedback(
                    feedbackMode = BrowserSearchFeedbackMode.NONE,
                    rowCount = 0,
                    allDecksSelected = true,
                    isHeaderCountVisible = headerVisible,
                ),
            )
            assertEquals(
                NONE,
                browserSearchFeedback(
                    feedbackMode = BrowserSearchFeedbackMode.NONE,
                    rowCount = 2,
                    allDecksSelected = true,
                    isHeaderCountVisible = headerVisible,
                ),
            )
        }
    }

    @Test
    fun `submitted search in all decks shows the count including zero when the header is hidden`() {
        for (count in listOf(0, 2)) {
            assertEquals(
                COUNT,
                browserSearchFeedback(
                    feedbackMode = BrowserSearchFeedbackMode.SEARCH_RESULTS,
                    rowCount = count,
                    allDecksSelected = true,
                    isHeaderCountVisible = false,
                ),
            )
        }
    }

    @Test
    fun `submitted search does not duplicate a visible count when there is no action`() {
        for (count in listOf(0, 2)) {
            assertEquals(
                NONE,
                browserSearchFeedback(
                    feedbackMode = BrowserSearchFeedbackMode.SEARCH_RESULTS,
                    rowCount = count,
                    allDecksSelected = true,
                    isHeaderCountVisible = true,
                ),
            )
        }
    }

    @Test
    fun `submitted search with matches in a deck offers search all decks even with a visible header`() {
        for (headerVisible in listOf(false, true)) {
            val feedback =
                browserSearchFeedback(
                    feedbackMode = BrowserSearchFeedbackMode.SEARCH_RESULTS,
                    rowCount = 2,
                    allDecksSelected = false,
                    isHeaderCountVisible = headerVisible,
                )
            assertEquals(COUNT_WITH_SEARCH_ALL_DECKS, feedback)
            assertEquals(true, feedback.offersSearchAllDecks)
        }
    }

    @Test
    fun `submitted search with no matches in a deck explains the scope and offers search all decks`() {
        for (headerVisible in listOf(false, true)) {
            val feedback =
                browserSearchFeedback(
                    feedbackMode = BrowserSearchFeedbackMode.SEARCH_RESULTS,
                    rowCount = 0,
                    allDecksSelected = false,
                    isHeaderCountVisible = headerVisible,
                )
            assertEquals(NO_CARDS_IN_SELECTED_DECK, feedback)
            assertEquals(true, feedback.offersSearchAllDecks)
        }
    }
}
