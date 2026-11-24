// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.instanteditor

import android.content.DialogInterface
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.instantnoteeditor.InstantEditorViewModel
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.coroutines.CoroutineContext

@RunWith(AndroidJUnit4::class)
class InstantNoteEditorActivityTest : RobolectricTest() {
    @Test
    fun `ignored Save does not overwrite the note being saved`() =
        runTest {
            val originalText = "{{c1::Hello}} world"
            val activity = startInstantNoteEditor(originalText)
            onView(withId(R.id.switch_edit_mode_button)).inRoot(isDialog()).perform(click())
            val editor = ShadowDialog.getLatestDialog() as AlertDialog
            val viewModel = ViewModelProvider(activity)[InstantEditorViewModel::class.java]
            val notesBefore = col.noteCount()
            val pendingOperations = ArrayDeque<Runnable>()
            val dispatcher =
                object : CoroutineDispatcher() {
                    override fun dispatch(
                        context: CoroutineContext,
                        block: Runnable,
                    ) {
                        pendingOperations.addLast(block)
                    }
                }

            withCollectionAccessOn(dispatcher) {
                onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
                assertTrue(viewModel.progressManager.progress.value is ViewModelProgress.Active)

                // Complete field validation, but leave insertion queued while the dialog is interactive.
                pendingOperations.removeFirst().run()
                assertEquals("note insertion is pending", 1, pendingOperations.size)
                editor.findViewById<EditText>(R.id.edit_field_text)!!.setText("")
                onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())

                while (pendingOperations.isNotEmpty()) {
                    pendingOperations.removeFirst().run()
                }
            }
            advanceUntilIdle()

            assertTrue("editor closes after saving", activity.isFinishing)
            assertEquals("notes added", notesBefore + 1, col.noteCount())
            assertEquals("saved fields must match the validated fields", originalText, col.getNote(viewModel.editorNote.id).fields[0])
        }

    @Test
    fun `tapping Save twice in the no-cloze confirmation adds one note`() =
        runTest {
            startInstantNoteEditor("Hello world")
            val notesBefore = col.noteCount()
            onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
            advanceUntilIdle()
            val warning = ShadowDialog.getLatestDialog() as AlertDialog
            val save = warning.getButton(DialogInterface.BUTTON_POSITIVE)
            assertTrue("warning is visible", warning.isShowing)

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                // Queue both button callbacks before AlertDialog's posted dismissal runs.
                save.performClick()
                save.performClick()
                shadowOf(Looper.getMainLooper()).idle()
            }
            advanceUntilIdle()
            assertEquals("notes added", notesBefore + 1, col.noteCount())
        }

    @Test
    fun `tapping Save twice while the note is saving adds one note`() =
        runTest {
            startInstantNoteEditor("{{c1::Hello}} world")
            val notesBefore = col.noteCount()

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                repeat(2) {
                    onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
                }
            }
            advanceUntilIdle()

            assertEquals("notes added", notesBefore + 1, col.noteCount())
        }

    @Test
    fun `tapping Save again after the note is saved adds one note`() =
        runTest {
            val activity = startInstantNoteEditor("{{c1::Hello}} world")
            val notesBefore = col.noteCount()

            onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
            advanceUntilIdle()
            assertTrue("editor is closing", activity.isFinishing)

            onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
            advanceUntilIdle()

            assertEquals("notes added", notesBefore + 1, col.noteCount())
        }

    @Test
    fun `back while the note is saving does not close the editor`() =
        runTest {
            val activity = startInstantNoteEditor("{{c1::Hello}} world")

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
                activity.onBackPressedDispatcher.onBackPressed()

                assertFalse("editor closed while saving", activity.isFinishing)
            }
            advanceUntilIdle()

            assertTrue("editor closes once the note is saved", activity.isFinishing)
        }

    private suspend fun startInstantNoteEditor(text: String): InstantNoteEditorActivity {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        return withCollectionAccessOn(Handler(Looper.getMainLooper()).asCoroutineDispatcher()) {
            startRegularActivity<InstantNoteEditorActivity>(intent)
        }
    }
}
