/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

import android.os.Parcelable
import anki.search.BrowserColumns
import com.ichi2.anki.libanki.Collection
import kotlinx.parcelize.Parcelize

/**
 * The key defining a column in the Card Browser: [BrowserColumns.Column.key]
 *
 * Example: `noteFld`
 *
 * @see Collection.getBrowserColumn
 * @see Collection.allBrowserColumns
 * @see Collection.loadBrowserCardColumns
 * @see Collection.loadBrowserNoteColumns
 * @see Collection.setBrowserCardColumns
 * @see Collection.setBrowserNoteColumns
 */
@JvmInline
@Parcelize
value class BrowserColumnKey(
    val value: String,
) : Parcelable {
    companion object {
        fun from(column: BrowserColumns.Column) = BrowserColumnKey(column.key)
    }
}
