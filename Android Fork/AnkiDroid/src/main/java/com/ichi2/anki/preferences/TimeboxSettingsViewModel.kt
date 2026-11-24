// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.utils.CollectionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimeboxUiState(
    val isLoading: Boolean = true,
    val isEnabled: Boolean = false,
    val limit: TimeboxTimeLimit = TimeboxTimeLimit.DEFAULT,
)

/**
 * State holder for [TimeboxSettingsFragment] and [TimeboxSettingsScreen].
 */
class TimeboxSettingsViewModel(
    private val prefs: PrefsRepository = Prefs,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TimeboxUiState())
    val uiState: StateFlow<TimeboxUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val collectionLimit = CollectionPreferences.getTimeboxLimit()
            val isEnabled = collectionLimit.isEnabled
            val limit = collectionLimit.resolveDisplayLimit(prefs)
            _uiState.value =
                TimeboxUiState(
                    isLoading = false,
                    isEnabled = isEnabled,
                    limit = limit,
                )
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isEnabled = enabled) }
        viewModelScope.launch {
            val limitToSave =
                if (enabled) {
                    _uiState.value.limit
                } else {
                    TimeboxTimeLimit.DISABLED
                }
            CollectionPreferences.setTimeboxLimit(limitToSave)
        }
    }

    fun setMinutes(newMinutes: Int) {
        val limit = TimeboxTimeLimit.fromMinutes(newMinutes) ?: return
        _uiState.update { it.copy(limit = limit) }
        prefs.timeboxCustomLimit = limit
        if (_uiState.value.isEnabled) {
            viewModelScope.launch {
                CollectionPreferences.setTimeboxLimit(limit)
            }
        }
    }

    fun increment() = setMinutes(_uiState.value.limit.minutes + 1)

    fun decrement() = setMinutes(_uiState.value.limit.minutes - 1)
}
