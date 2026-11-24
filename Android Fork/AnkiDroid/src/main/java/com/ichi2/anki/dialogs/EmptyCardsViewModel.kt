// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.card_rendering.EmptyCardsReport
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchFailure
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchResult
import com.ichi2.anki.dialogs.EmptyCardsUiState.SearchingForEmptyCards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** @see EmptyCardsDialogFragment */
class EmptyCardsViewModel : ViewModel() {
    val uiState: StateFlow<EmptyCardsUiState>
        field = MutableStateFlow<EmptyCardsUiState>(SearchingForEmptyCards)

    private var searchJob: Job? = null

    fun searchForEmptyCards() {
        if (searchJob != null) return
        searchJob =
            viewModelScope.launch {
                runCatching { withCol { getEmptyCards() } }
                    .onFailure { exception ->
                        if (exception is CancellationException) {
                            throw exception
                        }
                        uiState.emit(EmptyCardsSearchFailure(exception))
                    }.onSuccess { emptyCardsReport ->
                        uiState.emit(EmptyCardsSearchResult(emptyCardsReport))
                    }
            }
    }
}

sealed class EmptyCardsUiState {
    data object SearchingForEmptyCards : EmptyCardsUiState()

    data class EmptyCardsSearchResult(
        val emptyCardsReport: EmptyCardsReport,
    ) : EmptyCardsUiState()

    data class EmptyCardsSearchFailure(
        val throwable: Throwable,
    ) : EmptyCardsUiState()
}
