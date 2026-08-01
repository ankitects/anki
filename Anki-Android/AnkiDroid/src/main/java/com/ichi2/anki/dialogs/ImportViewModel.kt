// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Georgios Michelakis <michelakisgio@gmail.com>

package com.ichi2.anki.dialogs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class ImportViewModel : ViewModel() {
    val importAddFlow: SharedFlow<String>
        field = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val importReplaceFlow: SharedFlow<String>
        field = MutableSharedFlow<String>(extraBufferCapacity = 1)

    fun triggerImportAdd(path: String) =
        viewModelScope.launch {
            importAddFlow.emit(path)
        }

    fun triggerImportReplace(path: String) =
        viewModelScope.launch {
            importReplaceFlow.emit(path)
        }
}
