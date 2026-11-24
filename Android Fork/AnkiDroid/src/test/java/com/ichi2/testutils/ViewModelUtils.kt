// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore

/** Clears [viewModel] when [block] exits, including when it throws. */
suspend fun <VM : ViewModel, T> withViewModel(
    viewModel: VM,
    block: suspend VM.() -> T,
): T {
    val store = ViewModelStore().apply { put("test", viewModel) }
    try {
        return viewModel.block()
    } finally {
        store.clear()
    }
}
