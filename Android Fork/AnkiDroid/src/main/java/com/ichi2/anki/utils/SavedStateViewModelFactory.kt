// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.os.Bundle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Avoids duplicating large launch arguments in saved instance state, which can cause
 * [android.os.TransactionTooLargeException] when the activity stops.
 *
 * AndroidX normally copies all intent extras or fragment arguments into each ViewModel's saved
 * state. This factory creates a new [ViewModelSavedStateHandle] (or returns one which restores
 * the previously saved state).
 *
 * Pass launch inputs directly to the ViewModel constructor & use ViewModel state as normal.
 *
 * ```kt
 * class EditorViewModel(
 *     state: ViewModelSavedStateHandle,
 *     val noteId: NoteId,
 * ) : ViewModel() {
 *     val selectedField = state.getMutableStateFlow("selectedField", 0)
 * }
 *
 * // In a Fragment:
 * private val viewModel by viewModels<EditorViewModel> {
 *     savedStateViewModelFactory { state ->
 *         EditorViewModel(state, requireArguments().getLong("noteId"))
 *     }
 * }
 * ```
 *
 * Here, `noteId` comes from the fragment's arguments without being copied into the handle.
 * Edits to `selectedField.value` are saved and restored after process death.
 */
inline fun <reified VM : ViewModel> savedStateViewModelFactory(
    crossinline create: (ViewModelSavedStateHandle) -> VM,
): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val extras = MutableCreationExtras(this)
            extras[DEFAULT_ARGS_KEY] = Bundle.EMPTY
            create(extras.createSavedStateHandle())
        }
    }
