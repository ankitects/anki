// SPDX-License-Identifier: LGPL-3.0-or-later

package com.ichi2.anki.api

import android.net.Uri
import com.ichi2.anki.FlashCardsContract
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class FlashCardsContractTest {
    @Test
    fun cardUriUsesSuppliedAuthority() {
        val authority = Uri.parse("content://com.ichi2.anki.a.flashcards")

        assertEquals(
            "content://com.ichi2.anki.a.flashcards/cards",
            FlashCardsContract.Card.getContentUri(authority).toString(),
        )
    }

    @Test
    fun defaultCardUriRemainsUnchanged() {
        val packageName = if (BuildConfig.DEBUG) "com.ichi2.anki.debug" else "com.ichi2.anki"

        assertEquals("content://$packageName.flashcards/cards", FlashCardsContract.Card.CONTENT_URI.toString())
    }
}
