// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import androidx.annotation.CheckResult
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.filtered.FilteredDeckOptionsFragment
import com.ichi2.anki.libanki.DeckId

/** Builds the [Intent] that opens the deck options screen for this destination. */
fun DeckOptionsDestination.toIntent(context: Context): Intent =
    if (isFiltered) {
        FilteredDeckOptionsFragment.getIntent(context, did = deckId)
    } else {
        DeckOptions.getIntent(context, deckId)
    }

suspend fun DeckOptionsDestination.Companion.fromDeckId(deckId: DeckId): DeckOptionsDestination =
    DeckOptionsDestination(
        deckId = deckId,
        isFiltered = withCol { decks.isFiltered(deckId) },
    )

@CheckResult
suspend fun DeckOptionsDestination.Companion.fromCurrentDeck(): DeckOptionsDestination =
    withCol {
        val deckId = decks.getCurrentId()
        DeckOptionsDestination(
            deckId = deckId,
            isFiltered = decks.isFiltered(deckId),
        )
    }
