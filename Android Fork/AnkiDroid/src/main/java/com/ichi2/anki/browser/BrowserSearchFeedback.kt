// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

/** The feedback requested for a completed browser search. */
enum class BrowserSearchFeedbackMode {
    /** Suppress search feedback, including for operations which provide their own message. */
    NONE,

    /** Report search results, offering "Search all decks" when applicable. */
    SEARCH_RESULTS,

    /** Show the result count when it is hidden in the header. */
    COUNT_ONLY,
}

/** The feedback to display after a browser search completes. */
internal enum class BrowserSearchFeedback(
    val offersSearchAllDecks: Boolean = false,
) {
    NONE,
    COUNT,
    COUNT_WITH_SEARCH_ALL_DECKS(offersSearchAllDecks = true),
    NO_CARDS_IN_SELECTED_DECK(offersSearchAllDecks = true),
}

internal fun browserSearchFeedback(
    feedbackMode: BrowserSearchFeedbackMode,
    rowCount: Int,
    allDecksSelected: Boolean,
    isHeaderCountVisible: Boolean,
): BrowserSearchFeedback =
    // The search facts come from the completed search; header visibility is checked when displaying it.
    when (feedbackMode) {
        // Ordinary opening, deck changes and sorting stay quiet (Issue 21242).
        // Operations with their own feedback use this mode.
        BrowserSearchFeedbackMode.NONE -> BrowserSearchFeedback.NONE
        // User actions without their own message report the hidden count, without an action (Issue 22384).
        BrowserSearchFeedbackMode.COUNT_ONLY ->
            if (isHeaderCountVisible) BrowserSearchFeedback.NONE else BrowserSearchFeedback.COUNT
        BrowserSearchFeedbackMode.SEARCH_RESULTS ->
            when {
                // A search within a deck also offers "Search all decks", even with a visible header (Issue 5159).
                !allDecksSelected && rowCount == 0 -> BrowserSearchFeedback.NO_CARDS_IN_SELECTED_DECK
                !allDecksSelected -> BrowserSearchFeedback.COUNT_WITH_SEARCH_ALL_DECKS
                // Submitted searches show the count when the header hides it (Issue 3592).
                isHeaderCountVisible -> BrowserSearchFeedback.NONE
                else -> BrowserSearchFeedback.COUNT
            }
    }
