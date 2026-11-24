// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import com.ichi2.anki.IntentHandler.Companion.EXTRA_DECK_ID
import com.ichi2.anki.common.destinations.ReviewDeckDestination

/** Builds the [Intent] that opens the reviewer for this destination. */
fun ReviewDeckDestination.toIntent(context: Context): Intent =
    when (this) {
        is ReviewDeckDestination.CurrentDeck -> Reviewer.getIntent(context)
        is ReviewDeckDestination.ExternalLaunch ->
            Intent(context, IntentHandler::class.java).apply {
                // shortcuts require the intent to have an action
                setAction(Intent.ACTION_VIEW)
                putExtra(EXTRA_DECK_ID, deckId)
            }
    }
