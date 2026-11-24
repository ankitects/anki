// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import android.os.Parcelable
import anki.search.BrowserColumns.Column
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.common.utils.ext.browserSortCardsBackwards
import com.ichi2.anki.common.utils.ext.browserSortCardsColumn
import com.ichi2.anki.common.utils.ext.browserSortNotesBackwards
import com.ichi2.anki.common.utils.ext.browserSortNotesColumn
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.settings.Prefs
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * How to sort the rows in the [CardBrowser]
 *
 * A parcelable subset of the [SortOrders][SortOrder] which AnkiDroid supports
 *
 * [NoOrdering] is not supported by the upstream browser.
 * See: [Prefs.cardBrowserNoSorting]
 *
 * Other properties are stored in the collection config and synced:
 * [getBrowserColumnKey], [getSortBackwards]; [BrowserConfig]
 */
@Parcelize
sealed class SortType : Parcelable {
    /**
     * @see SortOrder.NoOrdering
     */
    data object NoOrdering : SortType()

    /**
     * @see SortOrder.UseCollectionOrdering
     * @see SortOrder.BuiltinColumnSortKind
     */
    data class CollectionOrdering(
        val key: BrowserColumnKey,
        val reverse: Boolean,
    ) : SortType()

    suspend fun save(cardsOrNotes: CardsOrNotes) {
        Timber.i("saving %s", this)

        when (this) {
            is NoOrdering -> Prefs.cardBrowserNoSorting = true
            is CollectionOrdering -> {
                withCol {
                    when (cardsOrNotes) {
                        CardsOrNotes.CARDS -> {
                            config.browserSortCardsColumn = this@SortType.key.value
                            config.browserSortCardsBackwards = this@SortType.reverse
                        }
                        NOTES -> {
                            config.browserSortNotesColumn = this@SortType.key.value
                            config.browserSortNotesBackwards = this@SortType.reverse
                        }
                    }
                }

                Prefs.cardBrowserNoSorting = false
            }
        }
    }

    companion object {
        suspend fun build(cardsOrNotes: CardsOrNotes) =
            when (Prefs.cardBrowserNoSorting) {
                true -> NoOrdering
                false -> {
                    val browserColumnKey = getBrowserColumnKey(cardsOrNotes)
                    val browserColumn: Column? = withCol { getBrowserColumn(browserColumnKey) }

                    if (browserColumn == null) {
                        NoOrdering
                    } else {
                        val reverse = getSortBackwards(cardsOrNotes)
                        val key = BrowserColumnKey.from(browserColumn)
                        CollectionOrdering(key = key, reverse = reverse)
                    }
                }
            }

        fun buildSortOrder(): SortOrder =
            when (Prefs.cardBrowserNoSorting) {
                true -> SortOrder.NoOrdering
                false -> SortOrder.UseCollectionOrdering
            }
    }
}

private suspend fun getBrowserColumnKey(cardsOrNotes: CardsOrNotes): String =
    withCol {
        if (cardsOrNotes == NOTES) config.browserSortNotesColumn else config.browserSortCardsColumn
    }

private suspend fun getSortBackwards(cardsOrNotes: CardsOrNotes): Boolean =
    withCol {
        if (cardsOrNotes == NOTES) config.browserSortNotesBackwards else config.browserSortCardsBackwards
    }
