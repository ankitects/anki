// noinspection MissingCopyrightHeader #17351

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.SharedDecksDownloadFragment.Companion.getDeckPageUri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SharedDecksDownloadFragmentTest : RobolectricTest() {
    @Test
    fun `test getDeckIdFromDownloadURL with valid URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=some-token"
        assertEquals("1104981491", SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url))
    }

    @Test
    fun `test getDeckIdFromDownloadURL without Query Parameter`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491"
        assertEquals("1104981491", SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url))
    }

    @Test
    fun `test getDeckIdFromDownloadURL with invalid URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/"
        assertNull(SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url), "Expected deckId to be null")
    }

    @Test
    fun `test getDeckPageUri with valid deck URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=some-token"
        assertEquals("https://ankiweb.net/shared/info/1104981491", targetContext.getDeckPageUri(url))
    }

    @Test
    fun `test getDeckPageUri with invalid deck URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/"
        assertEquals("https://ankiweb.net/shared/decks/", targetContext.getDeckPageUri(url))
    }
}
