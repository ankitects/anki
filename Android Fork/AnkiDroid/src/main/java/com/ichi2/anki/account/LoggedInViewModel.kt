// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.launch

class LoggedInViewModel : ViewModel() {
    /**
     * Handles the logic for logging out the user.
     */
    fun onLogout() {
        viewModelScope.launch {
            Prefs.hkey = null
            Prefs.username = null
            Prefs.currentSyncUri = null

            withCol {
                media.forceResync()
            }
        }
    }
}
