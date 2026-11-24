// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.multimedia.MultimediaActivity
import com.ichi2.anki.multimedia.MultimediaArgsStorage
import com.ichi2.anki.multimedia.MultimediaBottomSheet
import com.ichi2.anki.multimedia.MultimediaResult
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.noteeditor.NoteEditorMultimediaController.Companion.STATE_KEY_IMAGE_CACHE
import com.ichi2.anki.throwOnShowError
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.verify
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NoteEditorMultimediaControllerTest : RobolectricTest() {
    @Test
    fun `cancelling gallery returns directly to the unchanged note editor`() {
        val noteEditor = openNoteEditor()
        val field = noteEditor.getFieldForTest(1)
        field.setText("back")
        field.requestFocus()
        field.setSelection(1, 3)
        selectGallery(noteEditor, 1)
        val activity = shadowOf(noteEditor.requireActivity())
        val picker = activity.nextStartedActivityForResult.intent

        assertThat(picker.action, equalTo(Intent.ACTION_PICK))
        assertThat(picker.data, equalTo(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        assertThat("no intermediate editor", picker.component, nullValue())
        activity.receiveResult(picker, Activity.RESULT_CANCELED, null)
        advanceRobolectricLooper()

        assertThat(activity.nextStartedActivityForResult, nullValue())
        assertThat(noteEditor.requireActivity().isFinishing, equalTo(false))
        assertThat(field.text.toString(), equalTo("back"))
        assertThat(field.selectionStart, equalTo(1))
        assertThat(field.selectionEnd, equalTo(3))

        selectGallery(noteEditor, 1)
        assertThat(activity.nextStartedActivityForResult.intent.action, equalTo(Intent.ACTION_PICK))
    }

    @Test
    fun `gallery selection opens the image editor for the selected field with read access`() {
        val noteEditor = openNoteEditor()
        noteEditor.setField(0, "front")
        noteEditor.setField(1, "back")
        selectGallery(noteEditor, 1)
        val activity = shadowOf(noteEditor.requireActivity())
        val picker = activity.nextStartedActivityForResult.intent
        val imageUri = "content://media/external/images/media/1".toUri()

        activity.receiveResult(picker, Activity.RESULT_OK, Intent().setData(imageUri))
        advanceRobolectricLooper()

        assertGalleryEditor(activity.nextStartedActivityForResult.intent, imageUri, 1)
        assertThat(noteEditor.editFieldAt(0)!!.text.toString(), equalTo("front"))
        assertThat(noteEditor.editFieldAt(1)!!.text.toString(), equalTo("back"))
    }

    @Test
    fun `gallery result retains its target field after note editor recreation`() {
        ensureCollectionLoadIsSynchronous()
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.PassArguments(NoteEditorFragment.addNoteArgs()).toIntent(targetContext),
            )
        selectGallery(controller.get().getNoteEditorFragment(), 1)
        val picker = shadowOf(controller.get()).nextStartedActivityForResult

        controller.recreate()
        advanceRobolectricLooper()
        val imageUri = "content://media/external/images/media/2".toUri()
        controller.get().activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_OK, Intent().setData(imageUri))
        advanceRobolectricLooper()

        assertGalleryEditor(shadowOf(controller.get()).nextStartedActivityForResult.intent, imageUri, 1)
    }

    @Test
    fun `gallery file uri result cannot open the image editor`() {
        val noteEditor = openNoteEditor()
        selectGallery(noteEditor, 0)
        val activity = shadowOf(noteEditor.requireActivity())
        val picker = activity.nextStartedActivityForResult.intent

        activity.receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(File("/private/image.png"))))
        advanceRobolectricLooper()

        assertThat(activity.nextStartedActivityForResult, nullValue())
    }

    @Test
    fun `controller is reachable from the hosted fragment`() {
        val noteEditor = openNoteEditor()
        assertThat(noteEditor.multimediaController, notNullValue())
    }

    @Test
    fun `argument write failure shows an error and preserves the note editor`() {
        val noteEditor = openNoteEditor()
        noteEditor.setField(0, "front")
        noteEditor.multimediaController.handleActions(0)
        advanceRobolectricLooper()
        throwOnShowError = false
        // Directory permissions cannot reliably force a write failure on every platform.
        mockkConstructor(MultimediaArgsStorage::class) {
            every { anyConstructed<MultimediaArgsStorage>().save(any()) } throws IOException("Unable to write multimedia arguments")

            noteEditor.multimediaViewModel.setMultimediaAction(MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_RECORDING)
            advanceRobolectricLooper()
            verify(exactly = 1) { anyConstructed<MultimediaArgsStorage>().save(any()) }
        }

        val dialog = ShadowDialog.getLatestDialog()
        assertThat(dialog, notNullValue())
        assertThat(dialog.isShowing, equalTo(true))
        assertThat(noteEditor.requireActivity().isFinishing, equalTo(false))
        assertThat(noteEditor.editFieldAt(0)!!.text.toString(), equalTo("front"))
        assertThat(shadowOf(noteEditor.requireActivity()).nextStartedActivity, equalTo(null))
    }

    @Test
    fun `onSaveInstanceState writes the imageCache key`() {
        val noteEditor = openNoteEditor()
        val out = Bundle()

        noteEditor.multimediaController.onSaveInstanceState(out)

        assertThat(out.containsKey(STATE_KEY_IMAGE_CACHE), equalTo(true))
    }

    @Test
    fun `restore then save round-trips the imageCache contents`() {
        val noteEditor = openNoteEditor()
        val seeded = hashMapOf("foo" to "<img src='foo.png'>")
        val saved = Bundle().apply { putSerializable(STATE_KEY_IMAGE_CACHE, seeded) }

        noteEditor.multimediaController.onRestoreInstanceState(saved)
        val out = Bundle().also { noteEditor.multimediaController.onSaveInstanceState(it) }

        val restored = out.getSerializableCompat<HashMap<String, String>>(STATE_KEY_IMAGE_CACHE)
        assertThat(restored, equalTo(seeded))
    }

    @Test
    fun `restore from a bundle without imageCache key keeps the cache empty`() {
        val noteEditor = openNoteEditor()

        noteEditor.multimediaController.onRestoreInstanceState(Bundle())
        val out = Bundle().also { noteEditor.multimediaController.onSaveInstanceState(it) }

        val restored = out.getSerializableCompat<HashMap<String, String>>(STATE_KEY_IMAGE_CACHE)
        assertThat(restored?.isEmpty(), equalTo(true))
    }

    @Test
    fun `an image result without a file preserves selected text`() =
        runTest {
            val noteEditor = openNoteEditor()
            val editText = noteEditor.getFieldForTest(0)
            editText.setText("front")
            editText.requestFocus()
            editText.setSelection(0, 5)

            noteEditor.multimediaController.handleResult(MultimediaResult.Success(0, ImageField()))
            advanceUntilIdle()

            assertThat(editText.text.toString(), equalTo("front"))
            assertThat(editText.selectionStart, equalTo(0))
            assertThat(editText.selectionEnd, equalTo(5))
        }

    @Test
    fun `an image result with a missing file preserves selected text`() =
        runTest {
            val noteEditor = openNoteEditor()
            val editText = noteEditor.getFieldForTest(0)
            editText.setText("front")
            editText.requestFocus()
            editText.setSelection(0, 5)
            val field = ImageField().apply { mediaFile = File(tempFolder.root, "missing.png") }

            noteEditor.multimediaController.handleResult(MultimediaResult.Success(0, field))
            advanceUntilIdle()

            assertThat(editText.text.toString(), equalTo("front"))
            assertThat(editText.selectionStart, equalTo(0))
            assertThat(editText.selectionEnd, equalTo(5))
        }

    private fun selectGallery(
        noteEditor: NoteEditorFragment,
        fieldIndex: Int,
    ) {
        noteEditor.multimediaController.handleActions(fieldIndex)
        advanceRobolectricLooper()
        noteEditor.multimediaViewModel.setMultimediaAction(MultimediaBottomSheet.MultimediaAction.SELECT_IMAGE_FILE)
        advanceRobolectricLooper()
    }

    private fun assertGalleryEditor(
        intent: Intent,
        imageUri: Uri,
        fieldIndex: Int,
    ) {
        assertThat(intent.component!!.className, equalTo(MultimediaActivity::class.java.name))
        assertThat(intent.data, equalTo(imageUri))
        assertThat(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION, equalTo(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        val argsFile = intent.extras!!.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)!!
        val args = MultimediaArgsStorage.create(targetContext).read(argsFile)
        assertThat(args.index, equalTo(fieldIndex))
        assertThat(args.imageUri, equalTo(imageUri.toString()))
    }

    @Test
    fun `attachment choice waits for the busy collection`() {
        val noteEditor = openNoteEditor()
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val gate = CountDownLatch(1)
        // Hold the collection queue so loading the note waits until after the choice is made.
        val blocker = executor.submit { check(gate.await(20, TimeUnit.SECONDS)) }
        val previous = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
        try {
            assertThat("the collection queue is blocked", blocker.isDone, equalTo(false))
            noteEditor.multimediaController.handleActions(0)
            noteEditor.multimediaViewModel.setMultimediaAction(MultimediaBottomSheet.MultimediaAction.SELECT_AUDIO_RECORDING)
            gate.countDown()
            executor.submit { }.get(10, TimeUnit.SECONDS)
            advanceRobolectricLooper()

            val started = shadowOf(noteEditor.requireActivity()).nextStartedActivity
            assertThat("the selected recorder opens", started?.component?.className, equalTo(MultimediaActivity::class.java.name))
        } finally {
            gate.countDown()
            CollectionManager.setTestDispatcher(previous)
            dispatcher.close()
        }
    }

    private fun openNoteEditor(): NoteEditorFragment {
        ensureCollectionLoadIsSynchronous()
        return openNoteEditorWithArgs(NoteEditorFragment.addNoteArgs())
    }
}
