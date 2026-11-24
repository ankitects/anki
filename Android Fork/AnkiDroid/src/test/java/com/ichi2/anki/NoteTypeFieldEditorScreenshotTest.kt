// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import com.ichi2.testutils.simulateKeyboard
import org.junit.Test

/**
 * Screenshot tests for [NoteTypeFieldEditor]
 *
 * `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.NoteTypeFieldEditorScreenshotTest"`
 */
class NoteTypeFieldEditorScreenshotTest : ScreenshotTest() {
    @Test
    fun base() =
        withNoteTypeFieldEditor {
            captureScreen("base")
        }

    @Test
    fun keyboardOpen() =
        withNoteTypeFieldEditor { activity ->
            activity.simulateKeyboard()
            captureScreen("keyboard_open")
        }

    @Test
    fun deleteFieldDialog() =
        withNoteTypeFieldEditor { activity ->
            activity.handleAction(NoteTypeFieldEditorContextMenuAction.Delete)
            advanceRobolectricLooper()
            captureScreen("delete_field_dialog")
        }

    @Test
    fun repositionFieldDialog() =
        withNoteTypeFieldEditor { activity ->
            activity.handleAction(NoteTypeFieldEditorContextMenuAction.Reposition)
            advanceRobolectricLooper()
            captureScreen("reposition_field_dialog")
        }

    private fun withNoteTypeFieldEditor(block: (NoteTypeFieldEditor) -> Unit) {
        val notetype = getCurrentDatabaseNoteTypeCopy("Basic")
        val intent =
            Intent(targetContext, NoteTypeFieldEditor::class.java).apply {
                putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, notetype.name)
                putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, notetype.id)
            }
        ActivityScenario.launch<NoteTypeFieldEditor>(intent).use { scenario ->
            scenario.onActivity { activity ->
                advanceRobolectricLooper()
                block(activity)
            }
        }
    }
}
