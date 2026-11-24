// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.allViews
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.REQUEST_HINT_LOCALE_SELECTION
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.RESULT_SELECTED_LOCALE
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction.AddLanguageHint
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction.Delete
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction.Rename
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction.Reposition
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction.Sort
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.servicelayer.LanguageHintService.languageHint
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NoteTypeFieldEditorRecreationTest : RobolectricTest() {
    private lateinit var controller: ActivityController<NoteTypeFieldEditor>
    private lateinit var launchIntent: Intent

    @Test
    fun `context menu acts on the chosen field after a night mode switch`() {
        launch()
        openContextMenu(BACK)
        switchNightMode()
        chooseAction(Delete)
        confirm()
        assertEquals(listOf("Front"), fieldNames())
    }

    @Test
    fun `delete confirmation works after a night mode switch`() {
        launch()
        openContextMenu(BACK)
        chooseAction(Delete)
        switchNightMode()
        confirm()
        assertEquals(listOf("Front"), fieldNames())
    }

    @Test
    fun `full sync confirmation for deleting works after a night mode switch`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Delete)
        switchNightMode()
        confirm()
        assertEquals(listOf("Front"), fieldNames())
    }

    @Test
    fun `full sync confirmation for adding works after a night mode switch`() {
        markCollectionSynced()
        launch()
        controller.get().findViewById<View>(R.id.btn_add).performClick()
        latestAlertDialog().apply {
            window!!
                .decorView.allViews
                .filterIsInstance<EditText>()
                .single()
                .setText("Extra")
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        switchNightMode()
        confirm()
        assertEquals(listOf("Front", "Back", "Extra"), fieldNames())
    }

    @Test
    fun `full sync confirmation for repositioning works after a night mode switch`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Reposition)
        latestAlertDialog().apply {
            input = "1"
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        switchNightMode()
        confirm()
        assertEquals(listOf("Back", "Front"), fieldNames())
    }

    @Test
    fun `full sync confirmation for sorting works after a night mode switch`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Sort)
        switchNightMode()
        confirm()
        assertEquals(BACK, getCurrentDatabaseNoteTypeCopy("Basic").sortf)
    }

    @Test
    fun `restored confirmation waits for the field editor data to load`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Sort)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            // Tap the real restored dialog while onCollectionLoaded is still pending.
            confirm()
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { getCurrentDatabaseNoteTypeCopy("Basic").sortf == BACK }
        }
    }

    @Test
    fun `add confirmed before loading survives another recreation exactly once`() {
        markCollectionSynced()
        launch()
        controller.get().findViewById<View>(R.id.btn_add).performClick()
        latestAlertDialog().apply {
            window!!
                .decorView.allViews
                .filterIsInstance<EditText>()
                .single()
                .setText("Extra")
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            confirm()
            val savedAgain = saveAndDestroy()
            unmockkObject(CollectionManager)
            controller = Robolectric.buildActivity(NoteTypeFieldEditor::class.java, launchIntent)
            saveControllerForCleanup(controller)
            controller.setup(savedAgain)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil(lazyMessage = { "Fields after second restore: " + fieldNames() }) { fieldNames().size == 3 }
            switchNightMode()
            assertEquals(listOf("Front", "Back", "Extra"), fieldNames())
        }
    }

    @Test
    fun `reposition confirmed before loading survives another recreation`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Reposition)
        latestAlertDialog().apply {
            input = "1"
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            confirm()
            val savedAgain = saveAndDestroy()
            unmockkObject(CollectionManager)
            controller = Robolectric.buildActivity(NoteTypeFieldEditor::class.java, launchIntent)
            saveControllerForCleanup(controller)
            controller.setup(savedAgain)
            releaseLoad.countDown()
            advanceRobolectricLooper()
            assertEquals(listOf("Back", "Front"), fieldNames())
        }
    }

    @Test
    fun `cancelling restored delete before loading does not queue a mutation`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Delete)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            val dialog = assertIs<ConfirmationDialog>(shownDialog()).dialog as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            advanceRobolectricLooper()
            releaseLoad.countDown()
            awaitFields()
            assertEquals(listOf("Front", "Back"), fieldNames())
            assertFalse(col.schemaChanged())
        }
    }

    @Test
    fun `restored context menu deletes selected field after cold loading`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            releaseLoad.countDown()
            awaitFields()
            chooseAction(Delete)
            confirm()
            assertEquals(listOf("Front"), fieldNames())
        }
    }

    @Test
    fun `a buffered sort is consumed once across pause and resume`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Sort)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            confirm()
            controller.pause().stop()
            releaseLoad.countDown()
            awaitFields()
            assertEquals(0, getCurrentDatabaseNoteTypeCopy("Basic").sortf)
            controller.start().resume().visible()
            advanceRobolectricLooperUntil { getCurrentDatabaseNoteTypeCopy("Basic").sortf == BACK }
            markCollectionSynced()
            controller
                .pause()
                .stop()
                .start()
                .resume()
                .visible()
            advanceRobolectricLooper()
            assertFalse(col.schemaChanged())
        }
    }

    @Test
    fun `restored delete still deletes Back after schema reorder`() {
        launch()
        openContextMenu(BACK)
        chooseAction(Delete)
        val saved = saveAndDestroy()
        val nt = getCurrentDatabaseNoteTypeCopy("Basic")
        col.notetypes.moveFieldLegacy(nt, nt.fields[BACK], 0)
        assertEquals(listOf("Back", "Front"), fieldNames())
        restore(roundTrip(saved))
        val dialog = assertIs<ConfirmationDialog>(shownDialog()).dialog as AlertDialog
        assertEquals("Back", dialog.findViewById<android.widget.TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString())
        confirm()
        assertEquals(listOf("Front"), fieldNames())
    }

    @Test
    fun `restored sort still sorts by Back after schema reorder`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Sort)
        val saved = saveAndDestroy()
        val nt = getCurrentDatabaseNoteTypeCopy("Basic")
        col.notetypes.moveFieldLegacy(nt, nt.fields[BACK], 0)
        restore(roundTrip(saved))
        confirm()
        val updated = getCurrentDatabaseNoteTypeCopy("Basic")
        assertEquals("Back", updated.fields[updated.sortf].name)
    }

    @Test
    fun `restored reposition still moves Back after schema reorder`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Reposition)
        latestAlertDialog().apply {
            input = "1"
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        val saved = saveAndDestroy()
        moveBackToTheFront()
        restore(roundTrip(saved))
        confirm()
        assertEquals(listOf("Back", "Front"), fieldNames())
    }

    @Test
    fun `restored context menu acts on Back after schema reorder`() {
        launch()
        openContextMenu(BACK)
        val saved = saveAndDestroy()
        moveBackToTheFront()
        restore(roundTrip(saved))
        chooseAction(Delete)
        confirm()
        assertEquals(listOf("Front"), fieldNames())
    }

    @Test
    fun `restored delete does nothing once Back was removed elsewhere`() {
        launch()
        openContextMenu(BACK)
        chooseAction(Delete)
        val saved = saveAndDestroy()
        removeBack()
        restore(roundTrip(saved))
        confirm()
        assertEquals(listOf("Front"), fieldNames())
        assertNull(shownDialog())
    }

    @Test
    fun `restored sort does nothing once Back was removed elsewhere`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Sort)
        val saved = saveAndDestroy()
        removeBack()
        restore(roundTrip(saved))
        confirm()
        assertEquals(0, getCurrentDatabaseNoteTypeCopy("Basic").sortf)
        assertNull(shownDialog())
    }

    @Test
    fun `restored reposition does nothing once Back was removed elsewhere`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Reposition)
        latestAlertDialog().apply {
            input = "1"
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        val saved = saveAndDestroy()
        removeBack()
        restore(roundTrip(saved))
        confirm()
        assertEquals(listOf("Front"), fieldNames())
        assertNull(shownDialog())
    }

    @Test
    fun `restored context menu does nothing once Back was removed elsewhere`() {
        launch()
        openContextMenu(BACK)
        val saved = saveAndDestroy()
        removeBack()
        restore(roundTrip(saved))
        chooseAction(Sort)
        assertEquals(0, getCurrentDatabaseNoteTypeCopy("Basic").sortf)
        assertNull(shownDialog())
    }

    @Test
    fun `restored add skips a name that was added elsewhere`() {
        markCollectionSynced()
        launch()
        controller.get().findViewById<View>(R.id.btn_add).performClick()
        latestAlertDialog().apply {
            window!!
                .decorView.allViews
                .filterIsInstance<EditText>()
                .single()
                .setText("Extra")
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        val saved = saveAndDestroy()
        val notetype = getCurrentDatabaseNoteTypeCopy("Basic")
        col.notetypes.addFieldLegacy(notetype, col.notetypes.newField("Extra"))
        restore(roundTrip(saved))
        confirm()
        assertEquals(listOf("Front", "Back", "Extra"), fieldNames())
    }

    @Test
    fun `language hint picked before loading goes to the chosen field`() {
        launch()
        openContextMenu(BACK)
        chooseAction(AddLanguageHint)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            pickLanguageHint(Locale.JAPANESE)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { field("Back").languageHint == Locale.JAPANESE }
            assertNull(field("Front").languageHint)
        }
    }

    @Test
    fun `language hint after schema reorder goes to the chosen field`() {
        launch()
        openContextMenu(BACK)
        chooseAction(AddLanguageHint)
        val saved = saveAndDestroy()
        moveBackToTheFront()
        restore(roundTrip(saved))
        pickLanguageHint(Locale.JAPANESE)
        assertEquals(Locale.JAPANESE, field("Back").languageHint)
        assertNull(field("Front").languageHint)
    }

    @Test
    fun `pending confirmation is dropped when the note type was deleted elsewhere`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Sort)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            confirm()
            col.notetypes.remove(col.notetypes.idForName("Basic")!!)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { controller.get().isFinishing }
        }
    }

    @Test
    fun `restored context menu used before loading acts once loaded`() {
        launch()
        openContextMenu(BACK)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            chooseAction(Delete)
            releaseLoad.countDown()
            awaitFields()
            confirm()
            assertEquals(listOf("Front"), fieldNames())
        }
    }

    @Test
    fun `using restored menu while loading does not replace a confirmed deletion`() {
        launch()
        openContextMenu(BACK)
        chooseAction(Delete)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            confirm()
            chooseAction(Sort)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil {
                fieldNames().size == 1 || getCurrentDatabaseNoteTypeCopy("Basic").sortf == BACK
            }
            assertEquals(listOf("Front"), fieldNames())
        }
    }

    @Test
    fun `restored delete of the last remaining field leaves the editor usable`() {
        throwOnShowError = false
        launch()
        openContextMenu(0)
        chooseAction(Delete)
        val saved = saveAndDestroy()
        removeBack()
        restore(roundTrip(saved))
        confirm()
        latestAlertDialog().takeIf { it.isShowing }?.dismiss()
        openContextMenu(0)
        chooseAction(Rename)
        latestAlertDialog().apply {
            window!!
                .decorView.allViews
                .filterIsInstance<EditText>()
                .single()
                .setText("Renamed")
            getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooper()
        assertEquals(listOf("Renamed"), fieldNames())
    }

    @Test
    fun `restored deletion follows the selected field when its old name is reused`() {
        launch()
        openContextMenu(0)
        chooseAction(Delete)
        val saved = saveAndDestroy()
        val notetype = getCurrentDatabaseNoteTypeCopy("Basic")
        col.notetypes.renameFieldLegacy(notetype, notetype.fields[0], "Word")
        col.notetypes.renameFieldLegacy(notetype, notetype.fields[BACK], "Front")
        restore(roundTrip(saved))
        confirm()
        assertTrue("Front" in fieldNames(), "The field that reused the selected name must be preserved")
    }

    @Test
    fun `pending sort confirmation is kept when the restored menu is used again`() {
        markCollectionSynced()
        launch()
        openContextMenu(BACK)
        chooseAction(Sort)
        val saved = saveAndDestroy()
        withColdRestore(saved) { releaseLoad ->
            confirm()
            chooseAction(Delete)
            releaseLoad.countDown()
            advanceRobolectricLooperUntil { getCurrentDatabaseNoteTypeCopy("Basic").sortf == BACK }
            assertEquals(listOf("Front", "Back"), fieldNames())
        }
    }

    private fun launch() {
        val intent =
            Intent()
                .putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, "Basic")
                .putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, col.notetypes.idForName("Basic")!!)
        controller = startActivityControllerNormallyOpenCollectionWithIntent(NoteTypeFieldEditor::class.java, intent)
        advanceRobolectricLooper()
    }

    private fun markCollectionSynced() {
        col.setSchemaModified()
        col.db.execute("update col set ls = scm")
        assertFalse(col.schemaChanged())
    }

    private fun saveAndDestroy(): Bundle {
        launchIntent = Intent(controller.get().intent)
        return Bundle().also {
            controller
                .pause()
                .stop()
                .saveInstanceState(it)
                .destroy()
        }
    }

    private fun restore(saved: Bundle) {
        controller = Robolectric.buildActivity(NoteTypeFieldEditor::class.java, launchIntent)
        saveControllerForCleanup(controller)
        controller.setup(saved)
        advanceRobolectricLooper()
    }

    private fun roundTrip(saved: Bundle): Bundle {
        val out = android.os.Parcel.obtain()
        val input = android.os.Parcel.obtain()
        try {
            out.writeBundle(saved)
            val bytes = out.marshall()
            input.unmarshall(bytes, 0, bytes.size)
            input.setDataPosition(0)
            return input.readBundle(NoteTypeFieldEditor::class.java.classLoader)!!
        } finally {
            out.recycle()
            input.recycle()
        }
    }

    private fun moveBackToTheFront() {
        val notetype = getCurrentDatabaseNoteTypeCopy("Basic")
        col.notetypes.moveFieldLegacy(notetype, notetype.fields[BACK], 0)
        assertEquals(listOf("Back", "Front"), fieldNames())
    }

    private fun removeBack() {
        val notetype = getCurrentDatabaseNoteTypeCopy("Basic")
        col.notetypes.remFieldLegacy(notetype, notetype.fields[BACK])
        assertEquals(listOf("Front"), fieldNames())
    }

    private fun pickLanguageHint(locale: Locale) {
        assertIs<LocaleSelectionDialog>(shownDialog()).parentFragmentManager.setFragmentResult(
            REQUEST_HINT_LOCALE_SELECTION,
            Bundle().apply { putSerializable(RESULT_SELECTED_LOCALE, locale) },
        )
        advanceRobolectricLooper()
    }

    private fun field(name: String) = getCurrentDatabaseNoteTypeCopy("Basic").fields.first { it.name == name }

    private fun withColdRestore(
        saved: Bundle,
        block: (CountDownLatch) -> Unit,
    ) {
        val realCollection = col
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        mockkObject(CollectionManager)
        every { CollectionManager.isOpenUnsafe() } returns false
        every { CollectionManager.getColUnsafe() } answers {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                loadStarted.countDown()
                check(releaseLoad.await(15, TimeUnit.SECONDS)) { "Background load was not released" }
            }
            realCollection
        }
        try {
            controller = Robolectric.buildActivity(NoteTypeFieldEditor::class.java, launchIntent)
            saveControllerForCleanup(controller)
            controller.setup(saved)
            check(loadStarted.await(5, TimeUnit.SECONDS)) { "Collection load did not begin" }
            block(releaseLoad)
        } finally {
            releaseLoad.countDown()
            unmockkObject(CollectionManager)
            advanceRobolectricLooper()
        }
    }

    private fun awaitFields() {
        advanceRobolectricLooperUntil {
            controller
                .get()
                .findViewById<ListView>(R.id.fields)
                .adapter
                ?.count == 2
        }
    }

    private fun openContextMenu(position: Int) {
        controller.get().findViewById<ListView>(R.id.fields).performItemClick(null, position, 0)
        advanceRobolectricLooper()
        assertIs<NoteTypeFieldEditorContextMenu>(shownDialog())
    }

    private fun chooseAction(action: NoteTypeFieldEditorContextMenuAction) {
        val menu = assertIs<NoteTypeFieldEditorContextMenu>(shownDialog()).dialog as AlertDialog
        val index = NoteTypeFieldEditorContextMenuAction.entries.sortedBy { it.order }.indexOf(action)
        menu.listView.performItemClick(null, index, 0)
        advanceRobolectricLooper()
    }

    private fun switchNightMode() {
        val activity = controller.get()
        RuntimeEnvironment.setQualifiers("+night")
        controller.configurationChange()
        advanceRobolectricLooper()
        assertNotSame(activity, controller.get())
    }

    private fun confirm() {
        val dialog = assertIs<ConfirmationDialog>(shownDialog()).dialog as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
    }

    private fun shownDialog() = controller.get().supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as DialogFragment?

    private fun latestAlertDialog() = ShadowDialog.getLatestDialog() as AlertDialog

    private fun fieldNames() = getCurrentDatabaseNoteTypeCopy("Basic").fieldsNames

    companion object {
        private const val BACK = 1
    }
}
