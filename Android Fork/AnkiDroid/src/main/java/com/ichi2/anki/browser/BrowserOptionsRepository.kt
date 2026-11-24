// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.utils.ext.ignoreAccentsInSearch
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.utils.ext.defaultBrowserSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Source of truth for the values controlled by [com.ichi2.anki.dialogs.BrowserOptionsDialog]
 */
class BrowserOptionsRepository(
    private val sharedPrefs: SharedPreferences,
) {
    /** Keeps no-op checks and flow updates serialized with suspended collection access. */
    private val collectionOptionsMutex = Mutex()

    val cardsOrNotes: StateFlow<CardsOrNotes>
        field = MutableStateFlow(CardsOrNotes.CARDS)

    val isTruncated: StateFlow<Boolean>
        field = MutableStateFlow(sharedPrefs.getBoolean(PREF_IS_TRUNCATED, false))

    val ignoreAccentsInSearch: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /** @see com.ichi2.anki.libanki.Config.defaultBrowserSearch */
    val defaultBrowserSearch: StateFlow<String>
        field = MutableStateFlow("")

    /** Reads persisted values into the flows. Call once during ViewModel init. */
    suspend fun load() =
        collectionOptionsMutex.withLock {
            val (mode, ignoreAccents, defaultSearch) =
                withCol { Triple(CardsOrNotes.fromCollection(this), config.ignoreAccentsInSearch, config.defaultBrowserSearch) }
            cardsOrNotes.value = mode
            ignoreAccentsInSearch.value = ignoreAccents
            defaultBrowserSearch.value = defaultSearch
        }

    suspend fun setCardsOrNotes(value: CardsOrNotes) =
        collectionOptionsMutex.withLock {
            if (cardsOrNotes.value == value) return@withLock
            Timber.i("setting cards/notes mode to %s", value)
            withCol { value.saveToCollection(this) }
            cardsOrNotes.value = value
        }

    fun setIsTruncated(value: Boolean) {
        if (isTruncated.value == value) return
        Timber.d("setting truncated to %s", value)
        sharedPrefs.edit { putBoolean(PREF_IS_TRUNCATED, value) }
        isTruncated.value = value
    }

    suspend fun setIgnoreAccentsInSearch(value: Boolean) =
        collectionOptionsMutex.withLock {
            if (ignoreAccentsInSearch.value == value) return@withLock
            Timber.d("setting ignore accents in search to %s", value)
            withCol { config.ignoreAccentsInSearch = value }
            ignoreAccentsInSearch.value = value
        }

    suspend fun setDefaultBrowserSearch(value: String) =
        collectionOptionsMutex.withLock {
            if (defaultBrowserSearch.value == value) return@withLock
            Timber.d("setting default browser search to %s", value)
            withCol { config.defaultBrowserSearch = value }
            defaultBrowserSearch.value = value
        }

    companion object {
        private const val PREF_IS_TRUNCATED = "isTruncated"
    }
}
