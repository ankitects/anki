// SPDX-License-Identifier: GPL-3.0-or-later

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
