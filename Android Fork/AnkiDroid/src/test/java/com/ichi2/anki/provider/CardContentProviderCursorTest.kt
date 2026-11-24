// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.NoteId
import com.ichi2.testutils.grantPermissions
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CardContentProviderCursorTest : RobolectricTest() {
    /**
     * Regression test for Issue #8950 (Akebi API crash).
     *
     * Some endpoints (`notes_v2`) returned a custom cursor to the Anki Backend. The Android system
     * automatically registered a change listener to these cursors, which the Backend threw
     * `NotImplementedException` when accessing.
     *
     * This is Android-specific machinery which is skipped when run in the same process, so we
     * emulate the calls.
     */
    @Test
    fun `notes cursor supports observer registration and removal`() {
        val note = addBasicNote("Akebi", "dictionary entry")
        val cursor = queryNote(note.id)
        cursor.use {
            val contentObserver = object : ContentObserver(null) {}
            val dataSetObserver = object : DataSetObserver() {}
            it.registerContentObserver(contentObserver)
            it.registerDataSetObserver(dataSetObserver)

            assertEquals(1, it.count)
            assertTrue(it.moveToFirst())
            assertEquals(note.id, it.getLong(0))

            it.unregisterDataSetObserver(dataSetObserver)
            it.unregisterContentObserver(contentObserver)
        }
        assertTrue(cursor.isClosed)
    }

    private fun createProvider(): CardContentProvider {
        grantPermissions(FlashCardsContract.READ_WRITE_PERMISSION)
        return Robolectric
            .buildContentProvider(CardContentProvider::class.java)
            .create(FlashCardsContract.AUTHORITY)
            .get()
    }

    private fun queryNote(noteId: NoteId): Cursor =
        assertNotNull(
            createProvider().query(
                uri = FlashCardsContract.Note.CONTENT_URI_V2,
                projection = arrayOf(FlashCardsContract.Note._ID),
                selection = "id=?",
                selectionArgs = arrayOf(noteId.toString()),
                order = null,
            ),
        )
}
