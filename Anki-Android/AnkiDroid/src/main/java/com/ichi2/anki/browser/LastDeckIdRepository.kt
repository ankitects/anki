/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.browser

import android.content.Context
import androidx.core.content.edit
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks

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
class SharedPreferencesLastDeckIdRepository : LastDeckIdRepository {
    override var lastDeckId: DeckId?
        get() =
            appContext
                .getSharedPreferences(PERSISTENT_STATE_FILE, 0)
                .getLong(LAST_DECK_ID_KEY, Decks.NOT_FOUND_DECK_ID)
                .takeUnless { it == Decks.NOT_FOUND_DECK_ID }
        set(value) =
            if (value == null) {
                clearLastDeckId()
            } else {
                appContext.getSharedPreferences(PERSISTENT_STATE_FILE, 0).edit {
                    putLong(LAST_DECK_ID_KEY, value)
                }
            }

    companion object {
        fun clearLastDeckId() {
            val context: Context = appContext
            context.getSharedPreferences(PERSISTENT_STATE_FILE, 0).edit {
                remove(LAST_DECK_ID_KEY)
            }
        }

        private const val PERSISTENT_STATE_FILE = "DeckPickerState"
        private const val LAST_DECK_ID_KEY = "lastDeckId"
    }
}
