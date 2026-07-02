/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.customstudy

import android.widget.AdapterView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyCardState
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId

/**
 * @see CustomStudyDialog
 */
class CustomStudyViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /**
     * The index of the currently selected [CustomStudyDialog.CustomStudyCardState] or
     * [AdapterView.INVALID_POSITION] if there's no selection.
     */
    var selectedCardStateIndex: Int = AdapterView.INVALID_POSITION
        get() = savedStateHandle.get<Int>(KEY_CARDS_SELECTION_INDEX) ?: AdapterView.INVALID_POSITION
        set(value) {
            field = value
            savedStateHandle[KEY_CARDS_SELECTION_INDEX] = value
        }

    /** Required [DeckId] of the [Deck] for which the custom study session is being built. */
    val deckId: DeckId
        get() = savedStateHandle.get<DeckId>(KEY_DID) ?: error("Deck id was not provided!")

    /*
     * Translates the user's selection into a specific study type.
     * This prevents the app from "forgetting" user's choice (e.g., Due Cards)
     * even if the tag selection screen is skipped.
     */
    @NeedsTest("Verify that selectedCardStateIndex correctly maps to the corresponding CramKind")
    val selectedKind: CramKind
        get() =
            if (selectedCardStateIndex != AdapterView.INVALID_POSITION) {
                CustomStudyCardState.entries[selectedCardStateIndex].kind
            } else {
                CramKind.CRAM_KIND_NEW
            }

    companion object {
        /**
         * Required key for a [DeckId] which [CustomStudyDialog] expects to receive as an argument.
         */
        const val KEY_DID = "key_did"
        private const val KEY_CARDS_SELECTION_INDEX = "key_cards_selection_index"
    }
}
