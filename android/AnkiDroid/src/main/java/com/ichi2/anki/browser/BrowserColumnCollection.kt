/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

import android.content.SharedPreferences
import androidx.annotation.CheckResult
import androidx.core.content.edit
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.libanki.BrowserConfig.ACTIVE_CARD_COLUMNS_KEY
import com.ichi2.anki.libanki.BrowserConfig.ACTIVE_NOTE_COLUMNS_KEY
import com.ichi2.anki.libanki.BrowserDefaults
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import timber.log.Timber

/**
 * A collection of columns available in the [browser][CardBrowser]
 *
 * These are stored in [SharedPreferences] under either:
 * * [ACTIVE_CARD_COLUMNS_KEY]
 * * [ACTIVE_NOTE_COLUMNS_KEY]
 *
 * @see Backend.setActiveBrowserColumns
 * @see BrowserConfig.activeColumnsKey
 */
class BrowserColumnCollection(
    val columns: List<CardBrowserColumn>,
) {
    val backendKeys: Iterable<String> get() = columns.map { it.ankiColumnKey }
    val count = columns.size

    operator fun get(index: Int) = columns[index]

    companion object {
        private const val SEPARATOR_CHAR = '|'

        @CheckResult
        fun load(
            prefs: SharedPreferences,
            mode: CardsOrNotes,
        ): BrowserColumnCollection {
            val key = mode.toPreferenceKey()
            val columns =
                try {
                    val value = prefs.getString(key, mode.defaultColumns())!!
                    value.split(SEPARATOR_CHAR).map { CardBrowserColumn.fromColumnKey(it) }
                } catch (e: Exception) {
                    Timber.w(e, "error loading columns, returning default")
                    val value = mode.defaultColumns()
                    value.split(SEPARATOR_CHAR).map { CardBrowserColumn.fromColumnKey(it) }
                }
            // shared preferences had duplicate columns for unknown reasons
            // this removes duplicates on load, any saves will be de-duplicated
            return BrowserColumnCollection(columns.distinct())
        }

        class ColumnReplacement(
            val newColumns: BrowserColumnCollection,
            val originalColumns: List<CardBrowserColumn>,
        )

        fun replace(
            prefs: SharedPreferences,
            mode: CardsOrNotes,
            newColumns: List<CardBrowserColumn>,
        ): ColumnReplacement {
            val oldColumns = mutableListOf<CardBrowserColumn>()

            val newColumnCollection =
                update(prefs, mode) { cols ->
                    oldColumns.addAll(cols.filterNotNull())
                    cols.clear()
                    cols.addAll(newColumns)
                    return@update true
                    // guaranteed to be non-null, since we return true
                }!!

            return ColumnReplacement(
                newColumns = newColumnCollection,
                originalColumns = oldColumns,
            )
        }

        /**
         * @param block Update the column list here. `null` meaning 'none'.
         * Return `false` if no changes should be made
         *
         * @return the updated [BrowserColumnCollection], or `null` if [block] returned `false` so
         * no changes were made
         */
        fun update(
            prefs: SharedPreferences,
            mode: CardsOrNotes,
            block: (MutableList<CardBrowserColumn?>) -> Boolean,
        ): BrowserColumnCollection? {
            val valuesToUpdate: MutableList<CardBrowserColumn?> = load(prefs, mode).columns.toMutableList()
            if (!block(valuesToUpdate)) {
                Timber.d("no changes requested")
                return null
            }
            // as in AnkiMobile, this converts: [QUESTION, NONE, TAGS] into [QUESTION, TAGS]
            val updatedValues = valuesToUpdate.filterNotNull()
            return BrowserColumnCollection(updatedValues).also {
                save(prefs, mode, it)
            }
        }

        fun save(
            prefs: SharedPreferences,
            mode: CardsOrNotes,
            value: BrowserColumnCollection,
        ) {
            val key = mode.toPreferenceKey()
            val preferenceValue =
                value.columns
                    .joinToString(separator = SEPARATOR_CHAR.toString()) { it.ankiColumnKey }
            Timber.d("updating '%s' [%s] to '%s'", key, mode, preferenceValue)
            prefs.edit { putString(key, preferenceValue) }
        }

        private fun CardsOrNotes.toPreferenceKey() = BrowserConfig.activeColumnsKey(isNotesMode = this == NOTES)

        private fun CardsOrNotes.defaultColumns() =
            (if (this == CARDS) BrowserDefaults.CARD_COLUMNS else BrowserDefaults.NOTE_COLUMNS)
                .joinToString(separator = SEPARATOR_CHAR.toString())
    }
}
