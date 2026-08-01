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

package com.ichi2.anki.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.card_rendering.EmptyCardsReport
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchFailure
import com.ichi2.anki.dialogs.EmptyCardsUiState.EmptyCardsSearchResult
import com.ichi2.anki.dialogs.EmptyCardsUiState.SearchingForEmptyCards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** @see EmptyCardsDialogFragment */
class EmptyCardsViewModel : ViewModel() {
    val uiState: StateFlow<EmptyCardsUiState>
        field = MutableStateFlow<EmptyCardsUiState>(SearchingForEmptyCards)

    fun searchForEmptyCards() {
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
