// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.content.Intent
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.common.destinations.BrowserDestination

/** Builds the [Intent] that launches [CardBrowser] for this destination. */
fun BrowserDestination.toIntent(context: Context): Intent =
    when (this) {
        is BrowserDestination.ToDeck ->
            Intent(context, CardBrowser::class.java).apply {
                putExtra(CardBrowserViewModel.EXTRA_DECK_ID, deckId)
            }
        is BrowserDestination.ScrollToCard ->
            Intent(context, CardBrowser::class.java).apply {
                putExtra(CardBrowserViewModel.EXTRA_DECK_ID, deckId)
                putExtra(CardBrowserViewModel.EXTRA_CARD_ID_KEY, cardId)
            }
    }
