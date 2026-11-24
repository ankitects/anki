// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.multimedia.MultimediaResult
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import kotlinx.coroutines.asCoroutineDispatcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.fakes.RoboMenuItem
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NoteEditorPendingImportTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `save waits for pending media insertion`() {
        ensureCollectionLoadIsSynchronous()
        val note = addBasicNote("Front", "Back")
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.EditNoteFromPreviewer(note.firstCard().id).toIntent(targetContext),
            )
        val editor = controller.get().getNoteEditorFragment()
        val audio = File.createTempFile("pending-audio", ".3gp", targetContext.cacheDir).apply { writeBytes(ByteArray(256)) }
        val field =
            AudioRecordingField().apply {
                mediaFile = audio
                hasTemporaryMedia = true
            }
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val gate = CountDownLatch(1)
        executor.submit { check(gate.await(10, TimeUnit.SECONDS)) }
        val previous = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
        try {
            editor.multimediaController.handleResult(MultimediaResult.Success(1, field))
            gate.countDown()
            editor.onMenuItemSelected(RoboMenuItem(R.id.action_save))
            advanceRobolectricLooperUntil { controller.get().isFinishing }

            assertThat("the media import succeeded", col.media.have(field.mediaFile!!.name), equalTo(true))
            assertThat("the saved note includes the incoming audio", col.getNote(note.id).fields[1], containsString("[sound:"))
        } finally {
            gate.countDown()
            CollectionManager.setTestDispatcher(previous)
            dispatcher.close()
        }
    }
}
