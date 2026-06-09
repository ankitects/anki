/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
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
