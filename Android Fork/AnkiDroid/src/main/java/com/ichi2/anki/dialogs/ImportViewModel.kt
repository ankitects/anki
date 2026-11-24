// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Georgios Michelakis <michelakisgio@gmail.com>

package com.ichi2.anki.dialogs

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

class ImportViewModel : ViewModel() {
    val importAddFlow: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val importReplaceFlow: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    fun triggerImportAdd(path: String) {
        importAddFlow.value = path
    }

    fun triggerImportReplace(path: String) {
        importReplaceFlow.value = path
    }

    fun takeImportAdd(): String? = importAddFlow.getAndUpdate { null }

    fun takeImportReplace(): String? = importReplaceFlow.getAndUpdate { null }
}
