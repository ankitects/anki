// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.widget.PopupMenu
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.noteeditor.getNoteEditorFragment
import com.ichi2.anki.noteeditor.label
import com.ichi2.anki.noteeditor.presentation.ToolbarLayoutMode
import com.ichi2.anki.noteeditor.toIntent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteEditorToolbarLayoutTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `chooser reads existing stack preference and applies scroll and hidden`() {
        getPreferences().edit {
            putBoolean("noteEditorShowToolbar", true)
            putBoolean("noteEditorScrollToolbar", false)
        }
        val activity = startEditor()
        openChooser(activity)
        option(ToolbarLayoutMode.STACK).assertIsSelected()
        option(ToolbarLayoutMode.SCROLL).performClick()
        option(ToolbarLayoutMode.SCROLL).assertDoesNotExist()
        assertTrue(getPreferences().getBoolean("noteEditorScrollToolbar", false))
        assertTrue(
            activity
                .getNoteEditorFragment()
                .binding.editorToolbar.isVisible,
        )
        openChooser(activity)
        option(ToolbarLayoutMode.SCROLL).assertIsSelected()
        option(ToolbarLayoutMode.HIDDEN).performClick()
        option(ToolbarLayoutMode.HIDDEN).assertDoesNotExist()
        assertFalse(getPreferences().getBoolean("noteEditorShowToolbar", true))
        assertFalse(
            activity
                .getNoteEditorFragment()
                .binding.editorToolbar.isVisible,
        )
        openChooser(activity)
        option(ToolbarLayoutMode.HIDDEN).assertIsSelected()
        option(ToolbarLayoutMode.STACK).performClick()
        option(ToolbarLayoutMode.STACK).assertDoesNotExist()
        assertTrue(getPreferences().getBoolean("noteEditorShowToolbar", false))
        assertFalse(getPreferences().getBoolean("noteEditorScrollToolbar", true))
        assertTrue(
            activity
                .getNoteEditorFragment()
                .binding.editorToolbar.isVisible,
        )
    }

    @Test
    fun `chooser survives recreation and updates the recreated toolbar`() {
        getPreferences().edit {
            putBoolean("noteEditorShowToolbar", true)
            putBoolean("noteEditorScrollToolbar", false)
        }
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                NoteEditorActivity::class.java,
                NoteEditorDestination.AddNote().toIntent(targetContext),
            )
        advanceRobolectricLooper()
        openChooser(controller.get())
        option(ToolbarLayoutMode.STACK).assertIsSelected()

        controller.recreate()
        advanceRobolectricLooper()

        option(ToolbarLayoutMode.STACK).assertIsSelected()
        option(ToolbarLayoutMode.HIDDEN).performClick()
        option(ToolbarLayoutMode.HIDDEN).assertDoesNotExist()
        assertFalse(
            controller
                .get()
                .getNoteEditorFragment()
                .binding.editorToolbar.isVisible,
        )
    }

    private fun option(layout: ToolbarLayoutMode) = composeRule.onNodeWithText(targetContext.getString(layout.label()))

    private fun startEditor(): NoteEditorActivity =
        startActivityNormallyOpenCollectionWithIntent(
            NoteEditorActivity::class.java,
            NoteEditorDestination.AddNote().toIntent(targetContext),
        ).also { advanceRobolectricLooper() }

    private fun openChooser(activity: NoteEditorActivity) {
        composeRule.runOnIdle {
            val fragment = activity.getNoteEditorFragment()
            val menu = PopupMenu(activity, fragment.requireView()).menu
            activity.menuInflater.inflate(R.menu.note_editor, menu)
            assertTrue(fragment.onMenuItemSelected(menu.findItem(R.id.action_toolbar_layout)))
        }
    }
}
