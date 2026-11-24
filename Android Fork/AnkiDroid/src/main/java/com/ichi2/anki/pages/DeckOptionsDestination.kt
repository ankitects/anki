// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment

/** Builds the [Intent] that opens the deck options screen for this destination. */
fun DeckOptionsDestination.toIntent(context: Context): Intent =
    if (isFiltered) {
        FilteredDeckOptionsFragment.getIntent(context, did = deckId)
    } else {
        DeckOptions.getIntent(context, deckId)
    }
