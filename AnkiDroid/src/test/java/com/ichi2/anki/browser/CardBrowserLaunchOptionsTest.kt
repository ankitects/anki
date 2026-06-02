// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.browser.CardBrowserLaunchOptions.DeepLink
import com.ichi2.anki.browser.CardBrowserLaunchOptions.ScrollToCard
import com.ichi2.anki.browser.CardBrowserLaunchOptions.SearchQueryJs
import com.ichi2.anki.browser.CardBrowserLaunchOptions.SystemContextMenu
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [toCardBrowserLaunchOptions]: how an incoming [Intent] is mapped to the way
 * [com.ichi2.anki.CardBrowser] should open.
 *
 * @see CardBrowserViewModelTest - option handling.
 */
@RunWith(AndroidJUnit4::class)
class CardBrowserLaunchOptionsTest {
    @Test
    fun `deep link uses the search query parameter`() {
        val intent = Intent(Intent.ACTION_VIEW, "anki://x-callback-url/browser?search=dog".toUri())

        assertEquals(DeepLink("dog"), intent.toCardBrowserLaunchOptions())
    }

    @Test
    fun `search_query extra maps to SearchQueryJs`() {
        val intent =
            Intent().apply {
                putExtra(CardBrowserViewModel.EXTRA_SEARCH_QUERY, "dog")
                putExtra(CardBrowserViewModel.EXTRA_ALL_DECKS, true)
            }

        assertEquals(SearchQueryJs("dog", allDecks = true), intent.toCardBrowserLaunchOptions())
    }

    @Test
    fun `card id extra maps to ScrollToCard`() {
        val intent = Intent().putExtra(CardBrowserViewModel.EXTRA_CARD_ID_KEY, 123L)

        assertEquals(ScrollToCard(123L), intent.toCardBrowserLaunchOptions())
    }

    @Test
    fun `process text maps to SystemContextMenu`() {
        val intent =
            Intent(Intent.ACTION_PROCESS_TEXT)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "dog")

        assertEquals(SystemContextMenu("dog"), intent.toCardBrowserLaunchOptions())
    }

    @Test
    fun `empty intent has no launch options`() {
        assertNull(Intent().toCardBrowserLaunchOptions())
    }

    @Test
    fun `empty process text has no launch options`() {
        assertNull(Intent(Intent.ACTION_PROCESS_TEXT).toCardBrowserLaunchOptions())
    }
}
