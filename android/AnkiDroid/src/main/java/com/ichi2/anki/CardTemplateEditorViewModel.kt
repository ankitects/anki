// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2025 Snowiee <xenonnn4w@gmail.com>

package com.ichi2.anki

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import com.ichi2.anki.exception.ReportableException
import com.ichi2.anki.libanki.CardOrdinal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

class CardTemplateEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow<CardTemplateEditorState>(CardTemplateEditorState.Initializing())
    val state: StateFlow<CardTemplateEditorState> = _state.asStateFlow()

    /**
     * Transitions to the Loaded state.
     * Called internally after data is successfully loaded.
     */
    @VisibleForTesting
    internal fun onLoadComplete() {
        _state.value = CardTemplateEditorState.Loaded()
    }

    /**
     * Transitions to the [CardTemplateEditorState.Initializing] state carrying the failure.
     * Called internally when initialization fails.
     */
    internal fun onError(exception: ReportableException) {
        _state.value = CardTemplateEditorState.Initializing(exception)
    }

    /**
     * Transitions to the Finished state.
     * Called internally when the activity should close.
     */
    internal fun onFinish() {
        _state.value = CardTemplateEditorState.Finished
    }

    /**
     * Updates the current template ordinal.
     * Only valid when in Loaded state.
     */
    fun setCurrentTemplateOrd(ord: CardOrdinal) {
        updateLoadedState { it.copy(currentTemplateOrd = ord) }
    }

    /**
     * Updates the current editor view.
     * Only valid when in Loaded state.
     */
    fun setCurrentEditorView(viewType: EditorViewType) {
        updateLoadedState { it.copy(currentEditorView = viewType) }
    }

    /**
     * Clears the current message.
     * Only valid when in Loaded state.
     */
    fun clearMessage() {
        updateLoadedState { it.copy(message = null) }
    }

    /**
     * Clears the current transient error.
     * Only valid when in Loaded state.
     */
    fun clearError() {
        updateLoadedState { it.copy(error = null) }
    }

    /**
     * Helper to update only when in Loaded state.
     * Ignores updates when in Initializing or Finished states.
     */
    private inline fun updateLoadedState(transform: (CardTemplateEditorState.Loaded) -> CardTemplateEditorState.Loaded) {
        _state.update { currentState ->
            when (currentState) {
                is CardTemplateEditorState.Loaded -> transform(currentState)
                else -> {
                    Timber.w("Ignoring Loaded-state update; current state is %s", currentState)
                    currentState
                }
            }
        }
    }
}
