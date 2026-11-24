// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.multimedia.MultimediaResult
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.fakes.RoboMenuItem
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NoteEditorRapidImportSaveTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `save blocks touches while it waits for a media import`() {
        ensureCollectionLoadIsSynchronous()
        val note = addBasicNote("Front", "Back")
        val activity =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).get()
        val editor = activity.getNoteEditorFragment()
        editor.setField(0, "Edited front")
        advanceRobolectricLooper()
        val audio =
            AudioRecordingField().apply {
                mediaFile = tempFolder.newFile("rapid.3gp").apply { writeBytes(ByteArray(256)) }
            }

        withCollectionOnBackgroundThread {
            editor.multimediaController.handleResult(MultimediaResult.Success(1, audio))
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            assertTrue(activity.touchesBlocked(), "a second Save tap must not reach the editor while the first waits")

            advanceRobolectricLooperUntil { activity.isFinishing }
        }

        assertEquals("Edited front", col.getNote(note.id).fields[0])
    }

    private fun withCollectionOnBackgroundThread(block: () -> Unit) {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val previous = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previous)
            dispatcher.close()
        }
    }

    private fun NoteEditorActivity.touchesBlocked() = window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
}
