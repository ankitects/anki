// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class CardBrowserFragmentViewModel : ViewModel() {
    val flowOfSearchForDecks = MutableSharedFlow<Unit>()

    fun openDeckSelectionDialog() =
        viewModelScope.launch {
            flowOfSearchForDecks.emit(Unit)
        }
}
