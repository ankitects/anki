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

import android.content.SharedPreferences
import android.os.Parcelable
import androidx.annotation.VisibleForTesting
import anki.search.BrowserColumns.Column
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.ReverseDirection
import com.ichi2.anki.libanki.BrowserConfig
import com.ichi2.anki.libanki.Config
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * How to sort the rows in the [CardBrowser]
 *
 * This is an adapter from our [SharedPreferences] based handling of sorting
 * to Anki's [Config]
 *
 * We can likely remove the SharedPreferences and rely entirely on Anki
 *
 * @param ankiSortType The value to be passed into Anki's "sortType" config
 * @param cardBrowserLabelIndex The index into [R.array.card_browser_order_labels]
 */
@Suppress("unused") // 'unused' entries are iterated over by .entries
enum class LegacySortType(
    val ankiSortType: String?,
    val cardBrowserLabelIndex: Int,
) {
    NO_SORTING(null, 0),
    SORT_FIELD("noteFld", 1),
    CREATED_TIME("noteCrt", 2),
    NOTE_MODIFICATION_TIME("noteMod", 3),
    CARD_MODIFICATION_TIME("cardMod", 4),
    DUE_TIME("cardDue", 5),
    INTERVAL("cardIvl", 6),
    EASE("cardEase", 7),
    REVIEWS("cardReps", 8),
    LAPSES("cardLapses", 9),
    DECK("deck", 10),
    ;

    fun save(
        config: Config,
        prefs: PrefsRepository = Prefs,
    ) {
        Timber.v("update config to %s", this)
        // in the case of 'no sorting', we still need a sort type.
        // The inverse is handled in `fromCol`
        config.set("sortType", this.ankiSortType ?: SORT_FIELD.ankiSortType)
        config.set("noteSortType", this.ankiSortType ?: SORT_FIELD.ankiSortType)

        prefs.cardBrowserNoSorting = this == NO_SORTING
    }

    /** Converts the [LegacySortType] to a [SortOrder] */
    fun toSortOrder(): SortOrder = if (this == NO_SORTING) SortOrder.NoOrdering else SortOrder.UseCollectionOrdering

    companion object {
        fun fromCol(
            config: Config,
            cardsOrNotes: CardsOrNotes,
            prefs: PrefsRepository = Prefs,
        ): LegacySortType {
            val configKey = if (cardsOrNotes == CardsOrNotes.CARDS) "sortType" else "noteSortType"
            val colOrder = config.get<String>(configKey)
            val type = entries.firstOrNull { it.ankiSortType == colOrder } ?: NO_SORTING
            if (type == SORT_FIELD && prefs.cardBrowserNoSorting) {
                return NO_SORTING
            }
            return type
        }

        fun fromCardBrowserLabelIndex(index: Int): LegacySortType = entries.firstOrNull { it.cardBrowserLabelIndex == index } ?: NO_SORTING
    }
}

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
                val isNotesMode = cardsOrNotes == NOTES

                val sortKey = BrowserConfig.sortColumnKey(isNotesMode)
                val reverseKey = BrowserConfig.sortBackwardsKey(isNotesMode)

                withCol { config.set(sortKey, this@SortType.key.value) }
                withCol { config.set(reverseKey, this@SortType.reverse) }

                Prefs.cardBrowserNoSorting = false
            }
        }
    }

    fun toLegacy(): LegacySortType =
        when (this) {
            is NoOrdering -> LegacySortType.NO_SORTING
            is CollectionOrdering -> LegacySortType.entries.firstOrNull { it.ankiSortType == this.key.value } ?: LegacySortType.NO_SORTING
        }

    fun toLegacyReverse(): ReverseDirection? =
        when (this) {
            is NoOrdering -> null
            is CollectionOrdering -> ReverseDirection(orderAsc = this.reverse)
        }

    companion object {
        suspend fun build(cardsOrNotes: CardsOrNotes) =
            when (Prefs.cardBrowserNoSorting) {
                true -> NoOrdering
                false -> resolveColumnOrdering(cardsOrNotes)
            }

        private suspend fun resolveColumnOrdering(cardsOrNotes: CardsOrNotes): SortType {
            val browserColumnKey = getBrowserColumnKey(cardsOrNotes)
            val browserColumn: Column? = withCol { getBrowserColumn(browserColumnKey) }

            return if (browserColumn == null) {
                NoOrdering
            } else {
                val reverse = getSortBackwards(cardsOrNotes)
                val key = BrowserColumnKey.from(browserColumn)
                CollectionOrdering(key = key, reverse = reverse)
            }
        }

        fun buildSortOrder(): SortOrder =
            when (Prefs.cardBrowserNoSorting) {
                true -> SortOrder.NoOrdering
                false -> SortOrder.UseCollectionOrdering
            }
    }
}

/**
 * Whether the Card Browser should use an efficient 'no sorting' mode when displaying results
 *
 * **AnkiDroid Only**
 *
 * TODO: This should differentiate cards & notes mode
 */
@VisibleForTesting
var PrefsRepository.cardBrowserNoSorting: Boolean
    get() = getBoolean(R.string.pref_browser_no_sorting, false)
    set(value) {
        putBoolean(R.string.pref_browser_no_sorting, value)
    }

private suspend fun getBrowserColumnKey(cardsOrNotes: CardsOrNotes): String {
    val isNotesMode = cardsOrNotes == NOTES
    val sortKey = BrowserConfig.sortColumnKey(isNotesMode)

    return withCol { config.get<String>(sortKey) } ?: "noteFld"
}

private suspend fun getSortBackwards(cardsOrNotes: CardsOrNotes): Boolean {
    val isNotesMode = cardsOrNotes == NOTES
    val sortBackwardsKey = BrowserConfig.sortBackwardsKey(isNotesMode)

    return withCol { config.get<Boolean>(sortBackwardsKey) } ?: false
}
