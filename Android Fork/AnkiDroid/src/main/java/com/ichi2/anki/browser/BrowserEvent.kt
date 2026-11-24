// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import anki.collection.OpChanges

/** Events coordinated by [CardBrowserViewModel.onEvent]. */
sealed interface BrowserEvent {
    data object ToggleMarkRequested : BrowserEvent

    /** Reports a mutation which has already happened. */
    data class CollectionChanged(
        val changes: OpChanges,
        val origin: Origin,
    ) : BrowserEvent {
        enum class Origin { BROWSER, NOTE_EDITOR, EXTERNAL }
    }
}
