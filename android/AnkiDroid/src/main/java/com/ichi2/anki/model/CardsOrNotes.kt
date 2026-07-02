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

package com.ichi2.anki.model

import android.os.Parcelable
import anki.config.ConfigKey
import com.ichi2.anki.libanki.Collection
import kotlinx.parcelize.Parcelize

/**
 * Config: Whether the `CardBrowser` is in "Cards" or "Notes" mode
 *
 * @see ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE
 */
@Parcelize
enum class CardsOrNotes : Parcelable {
    CARDS,
    NOTES,
    ;

    fun saveToCollection(col: Collection) {
        col.config.setBool(ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE, this == NOTES)
    }

    companion object {
        fun fromCollection(col: Collection): CardsOrNotes =
            when (col.config.getBool(ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE)) {
                true -> NOTES
                false -> CARDS
            }
    }
}
