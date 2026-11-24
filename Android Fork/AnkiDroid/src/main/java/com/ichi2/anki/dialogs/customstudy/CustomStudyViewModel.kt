// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.customstudy

import android.widget.AdapterView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyCardState
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyDefaults
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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

    var userInputText: String?
        get() = savedStateHandle[KEY_USER_INPUT_TEXT]
        set(value) {
            savedStateHandle[KEY_USER_INPUT_TEXT] = value
        }

    /** Required [DeckId] of the [Deck] for which the custom study session is being built. */
    val deckId: DeckId
        get() = savedStateHandle.get<DeckId>(ARG_DECK_ID) ?: error("Deck id was not provided!")

    /**
     * Check the requested deck before loading defaults or changing selection.
     * [canSelectDeck] is checked on the collection queue immediately before selecting.
     */
    suspend fun initialize(canSelectDeck: () -> Boolean = { true }): CustomStudyInitialization {
        val context = currentCoroutineContext()
        return withCol {
            if (!hasCustomStudyCards()) {
                CustomStudyInitialization.Empty
            } else {
                val defaults = sched.customStudyDefaults(deckId).toDomainModel()
                // The dialog may have closed while the backend was loading defaults.
                context.ensureActive()
                if (!canSelectDeck()) throw CancellationException("Custom Study initialization is no longer needed")
                decks.select(deckId)
                CustomStudyInitialization.Ready(deckId, defaults)
            }
        }
    }

    suspend fun isDeckEmptyOrHidden(): Boolean = withCol { !hasCustomStudyCards() }

    private fun Collection.hasCustomStudyCards(): Boolean =
        // Check listing first: counting child-deck cards requires the deck to exist.
        // Default may also be hidden while cardCount still includes its borrowed cards.
        sched.deckTree(includeCounts = false).find(deckId) != null &&
            decks.cardCount(deckId, includeSubdecks = true) > 0

    /*
     * Translates the user's selection into a specific study type.
     * This prevents the app from "forgetting" user's choice (e.g., Due Cards)
     * even if the tag selection screen is skipped.
     */
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
        const val ARG_DECK_ID = "key_did"
        private const val KEY_CARDS_SELECTION_INDEX = "key_cards_selection_index"
        private const val KEY_USER_INPUT_TEXT = "key_user_input_text"
    }
}

sealed interface CustomStudyInitialization {
    data object Loading : CustomStudyInitialization

    data object Empty : CustomStudyInitialization

    data class Ready(
        val deckId: DeckId,
        val defaults: CustomStudyDefaults,
    ) : CustomStudyInitialization
}
