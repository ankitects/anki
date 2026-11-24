// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.os.Parcelable
import anki.search.BrowserRow
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.model.CardsOrNotes
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/** @see CardBrowserColumn */
@Parcelize
class ColumnWithSample(
    val label: String,
    val columnType: CardBrowserColumn,
    val sampleValue: String?,
) : Parcelable {
    companion object {
        suspend fun loadSample(
            id: CardOrNoteId?,
            cardsOrNotes: CardsOrNotes,
        ): List<ColumnWithSample> {
            val allColumns = CollectionManager.withCol { allBrowserColumns() }.associateBy { it.key }

            val sampleRow: BrowserRow? = loadSampleRow(id, cardsOrNotes)

            fun toColumn(column: CardBrowserColumn): ColumnWithSample {
                val matched = allColumns.getValue(column.ankiColumnKey)
                val sampleValue = sampleRow?.getCells(column.ordinal)?.text
                return ColumnWithSample(
                    label = matched.getLabel(cardsOrNotes),
                    columnType = column,
                    sampleValue = sampleValue,
                )
            }

            return CardBrowserColumn.entries.map(::toColumn)
        }

        /**
         * Attempts to load all data for the provided row, to provide a sample for each column
         */
        private suspend fun loadSampleRow(
            id: CardOrNoteId?,
            cardsOrNotes: CardsOrNotes,
        ): BrowserRow? {
            if (id == null) return null

            val originalKeys =
                BrowserColumnCollection
                    .load(AnkiDroidApp.sharedPrefs(), cardsOrNotes)
                    .backendKeys
                    .toList()

            return try {
                val allKeys = CardBrowserColumn.entries.map { it.ankiColumnKey }
                CollectionManager
                    .withCol {
                        backend.setActiveBrowserColumns(allKeys)
                        browserRowForId(id.cardOrNoteId)
                    }.also { Timber.d("got sample row") }
            } finally {
                CollectionManager.withCol { backend.setActiveBrowserColumns(originalKeys) }
            }
        }
    }
}
