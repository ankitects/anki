// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import androidx.lifecycle.SavedStateHandle

/**
 * A [SavedStateHandle] created without automatically copying intent extras or fragment arguments.
 *
 * @see savedStateViewModelFactory
 */
typealias ViewModelSavedStateHandle = SavedStateHandle
