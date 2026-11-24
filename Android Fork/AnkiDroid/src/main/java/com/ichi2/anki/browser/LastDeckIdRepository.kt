// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.core.content.edit
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.preferences.SharedPreferencesProvider

interface LastDeckIdRepository {
    var lastDeckId: DeckId?
}

/**
 * Saves the last selected [DeckId] in the Card Browser
 *
 * This exists as the old code used [PERSISTENT_STATE_FILE], rather than [AnkiDroidApp.sharedPrefs]
 *
 * [Decks.select] is not used in the Card Browser: this can be launched from a review session and
 * should not affect the session
 */
class SharedPreferencesLastDeckIdRepository(
    private val preferences: SharedPreferencesProvider =
        SharedPreferencesProvider { appContext.getSharedPreferences(PERSISTENT_STATE_FILE, 0) },
) : LastDeckIdRepository {
    override var lastDeckId: DeckId?
        get() =
            preferences
                .sharedPrefs()
                .getLong(PREF_LAST_DECK_ID, Decks.NOT_FOUND_DECK_ID)
                .takeUnless { it == Decks.NOT_FOUND_DECK_ID }
        set(value) {
            preferences.sharedPrefs().edit {
                if (value == null) {
                    remove(PREF_LAST_DECK_ID)
                } else {
                    putLong(PREF_LAST_DECK_ID, value)
                }
            }
        }

    companion object {
        fun clearLastDeckId() {
            SharedPreferencesLastDeckIdRepository().lastDeckId = null
        }

        private const val PERSISTENT_STATE_FILE = "DeckPickerState"
        private const val PREF_LAST_DECK_ID = "lastDeckId"
    }
}
