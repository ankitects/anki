// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.fakes.RoboMenuItem
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class NoteEditorRepeatedSaveTest : RobolectricTest() {
    @Test
    fun `second Ctrl+Enter does not close the editor while the first save is running`() = saveTwice { pressCtrlEnter() }

    @Test
    fun `Ctrl+Enter does not close the editor while a Save tap is running`() =
        saveTwice { getNoteEditorFragment().onMenuItemSelected(RoboMenuItem(R.id.action_save)) }

    private fun saveTwice(firstSave: NoteEditorActivity.() -> Unit) {
        ensureCollectionLoadIsSynchronous()
        val note = addBasicNote("Front", "Back")
        val activity =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            ).get()
        activity.getNoteEditorFragment().setField(0, "Edited front")
        advanceRobolectricLooper()

        withCollectionOnBackgroundThread {
            activity.firstSave()
            activity.pressCtrlEnter()
            assertFalse(activity.isFinishing, "the editor closed while the first save was still running")

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

    private fun NoteEditorActivity.pressCtrlEnter() {
        dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, KeyEvent.META_CTRL_ON))
    }
}
