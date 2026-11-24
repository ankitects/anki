// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.databinding.DialogNoteEditorToolbarEditCustomItemBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteEditorToolbarTest : RobolectricTest() {
    @Test
    fun `clearing the text before the cursor saves a button that inserts after the cursor`() {
        checkEditedButton(prefix = "", suffix = "<br>", expectedCursor = 0)
    }

    @Test
    fun `clearing the text after the cursor saves a button that inserts before the cursor`() {
        checkEditedButton(prefix = "<br>", suffix = "", expectedCursor = 4)
    }

    @Test
    fun `editing requires text on at least one side of the cursor`() {
        val toolbar = createToolbar()
        toolbar.findViewWithTag<View>("1").performLongClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val binding = dialog.binding
        val before = binding.noteEditorToolbarBefore
        val after = binding.noteEditorToolbarAfter
        val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        assertTrue(save.isEnabled)
        before.setText("")
        assertTrue(save.isEnabled)
        after.setText("")
        assertFalse(save.isEnabled, "Both fields empty must disable Save")

        after.setText("<br>")
        assertTrue(save.isEnabled)
        after.setText(" ")
        assertFalse(save.isEnabled, "Whitespace alone must not enable Save")
        before.setText("<br>")
        assertTrue(save.isEnabled)
        dialog.dismiss()
    }

    private fun checkEditedButton(
        prefix: String,
        suffix: String,
        expectedCursor: Int,
    ) {
        val toolbar = createToolbar()
        toolbar.findViewWithTag<View>("1").performLongClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val binding = dialog.binding
        binding.noteEditorToolbarBefore.setText(prefix)
        binding.noteEditorToolbarAfter.setText(suffix)
        clickAlertDialogButton { getButton(AlertDialog.BUTTON_POSITIVE) }

        var formatted: Toolbar.StringFormat? = null
        toolbar.formatListener = Toolbar.TextFormatListener { formatted = it.format("") }
        toolbar.findViewWithTag<View>("1").performClick()
        val result = assertNotNull(formatted)
        assertEquals("<br>", result.result)
        assertEquals(expectedCursor, result.selectionStart)
        assertEquals(expectedCursor, result.selectionEnd)

        // Reopening reads the saved button from preferences.
        toolbar.findViewWithTag<View>("1").performLongClick()
        val reopened = ShadowDialog.getLatestDialog() as AlertDialog
        val reopenedBinding = reopened.binding
        assertEquals(prefix, reopenedBinding.noteEditorToolbarBefore.text.toString())
        assertEquals(suffix, reopenedBinding.noteEditorToolbarAfter.text.toString())
        reopened.dismiss()
    }

    private fun createToolbar(): Toolbar {
        // Start with both fields populated so saving must clear an existing value.
        targetContext.sharedPrefs().edit {
            putStringSet(
                "note_editor_custom_buttons",
                CustomToolbarButton.toStringSet(arrayListOf(CustomToolbarButton(0, "BR", "<b>", "</b>"))),
            )
        }
        val editor =
            startRegularActivity<NoteEditorActivity>(NoteEditorDestination.AddNote().toIntent()).getNoteEditorFragment()
        return editor.binding.editorToolbar
    }

    private val AlertDialog.binding: DialogNoteEditorToolbarEditCustomItemBinding
        get() {
            val customPanel = assertNotNull(findViewById<ViewGroup>(androidx.appcompat.R.id.custom))
            return DialogNoteEditorToolbarEditCustomItemBinding.bind(customPanel.getChildAt(0))
        }
}
