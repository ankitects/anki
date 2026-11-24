// SPDX-License-Identifier: GPL-3.0-or-later

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
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.instantnoteeditor.InstantEditorViewModel
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class InstantEditorNoClozeSnapshotReviewTest : RobolectricTest() {
    @Test
    fun `confirmation saves the fields that produced the no-cloze warning`() =
        runTest {
            val originalText = "Hello world"
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, originalText)
                }
            val activity =
                withCollectionAccessOn(Handler(Looper.getMainLooper()).asCoroutineDispatcher()) {
                    startRegularActivity<InstantNoteEditorActivity>(intent)
                }
            onView(withId(R.id.switch_edit_mode_button)).inRoot(isDialog()).perform(click())
            val editor = ShadowDialog.getLatestDialog() as AlertDialog
            val viewModel = ViewModelProvider(activity)[InstantEditorViewModel::class.java]
            val notesBefore = col.noteCount()

            withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                onView(withId(R.id.action_save_note)).inRoot(isDialog()).perform(click())
                // This window remains interactive while validation waits in the collection queue.
                editor.findViewById<EditText>(R.id.edit_field_text)!!.setText("")
            }
            advanceUntilIdle()
            val warning = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue("no-cloze warning must be shown", warning !== editor && warning.isShowing)
            assertEquals(
                CollectionManager.TR.addingYouHaveAClozeDeletionNote(),
                warning.findViewById<android.widget.TextView>(android.R.id.message)!!.text.toString(),
            )

            warning.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()

            assertTrue("confirmed note is saved", activity.isFinishing)
            assertEquals("one note added", notesBefore + 1, col.noteCount())
            assertEquals(
                "confirmation must not insert unvalidated replacement fields",
                originalText,
                col.getNote(viewModel.editorNote.id).fields[0],
            )
        }
}
