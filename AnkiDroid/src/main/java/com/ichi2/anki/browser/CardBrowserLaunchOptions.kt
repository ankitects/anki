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

import android.content.Intent
import androidx.annotation.CheckResult
import com.ichi2.anki.common.utils.ext.getLongExtra
import com.ichi2.anki.libanki.CardId

/** How the [com.ichi2.anki.CardBrowser] can be launched */
sealed interface CardBrowserLaunchOptions {
    data class DeepLink(
        val search: String,
    ) : CardBrowserLaunchOptions

    data class SearchQueryJs(
        val search: String,
        val allDecks: Boolean,
    ) : CardBrowserLaunchOptions

    /** Opened from the 'Card Browser' system context menu ([Intent.ACTION_PROCESS_TEXT]) */
    data class SystemContextMenu(
        val search: CharSequence,
    ) : CardBrowserLaunchOptions

    data class ScrollToCard(
        val cardId: CardId,
    ) : CardBrowserLaunchOptions
}

@CheckResult
fun Intent.toCardBrowserLaunchOptions(): CardBrowserLaunchOptions? {
    // search card using deep links
    data?.getQueryParameter("search")?.let {
        return CardBrowserLaunchOptions.DeepLink(it)
    }

    // for intent coming from search query js api
    getStringExtra(CardBrowserViewModel.EXTRA_SEARCH_QUERY)?.let {
        return CardBrowserLaunchOptions.SearchQueryJs(it, getBooleanExtra(CardBrowserViewModel.EXTRA_ALL_DECKS, false))
    }

    getLongExtra(CardBrowserViewModel.EXTRA_CARD_ID_KEY)?.let { cardId ->
        return CardBrowserLaunchOptions.ScrollToCard(cardId)
    }

    // Maybe we were called from ACTION_PROCESS_TEXT
    if (Intent.ACTION_PROCESS_TEXT == action) {
        val search = getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        if (search.isNullOrEmpty()) {
            return null
        }
        return CardBrowserLaunchOptions.SystemContextMenu(search)
    }

    return null
}
