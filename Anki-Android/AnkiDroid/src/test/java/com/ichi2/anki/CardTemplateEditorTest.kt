/*
 * Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardTemplateEditor.CardTemplateFragment.CardTemplate
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.notetype.ManageNoteTypesState.CardEditor
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.scheduling.selectTab
import com.ichi2.testutils.assertFalse
import com.ichi2.testutils.withSplitPaneUi
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.json.JSONObject
import org.junit.Assume.assumeThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity
import timber.log.Timber
import kotlin.test.junit5.JUnit5Asserter.assertEquals
import kotlin.test.junit5.JUnit5Asserter.assertNotEquals
import kotlin.test.junit5.JUnit5Asserter.assertNotNull
import kotlin.test.junit5.JUnit5Asserter.assertNull
import kotlin.test.junit5.JUnit5Asserter.assertTrue

@RunWith(AndroidJUnit4::class)
class CardTemplateEditorTest : RobolectricTest() {
    @Test
    @Throws(Exception::class)
    fun testEditTemplateContents() {
        val noteTypeName = "Basic"

        // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        var templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        var testEditor = templateEditorController.get()
        assertFalse("Note type should not have changed yet", testEditor.noteTypeHasChanged())

        // Change the note type and make sure it registers as changed, but the database is unchanged
        var templateFront = testEditor.editText
        val testNoteTypeQfmtEdit = "!@#$%^&*TEST*&^%$#@!"
        templateFront.text.append(testNoteTypeQfmtEdit)
        advanceRobolectricLooper()
        assertTrue("Note type did not change after edit?", testEditor.noteTypeHasChanged())
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )

        // Kill and restart the Activity, make sure note type edit is preserved
        val outBundle = Bundle()
        templateEditorController.saveInstanceState(outBundle)
        templateEditorController.pause().stop().destroy()
        templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java)
                .create(outBundle)
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        var shadowTestEditor = shadowOf(testEditor)
        assertTrue("note type change not preserved across activity lifecycle?", testEditor.noteTypeHasChanged())
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )

        // Make sure we get a confirmation dialog if we hit the back button
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home))
        advanceRobolectricLooper()
        assertEquals("Wrong dialog shown?", getAlertDialogText(true), "Discard changes?")
        clickAlertDialogButton(DialogInterface.BUTTON_NEGATIVE, false)
        advanceRobolectricLooper()
        assertTrue("note type change not preserved despite canceling back button?", testEditor.noteTypeHasChanged())

        // Make sure we things are cleared out after a cancel
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(android.R.id.home))
        assertEquals("Wrong dialog shown?", getAlertDialogText(true), "Discard changes?")
        clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, false)
        advanceRobolectricLooper()
        assertFalse("note type change not cleared despite discarding changes?", testEditor.noteTypeHasChanged())

        // Get going for content edit assertions again...
        templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        testEditor = templateEditorController.get()
        shadowTestEditor = shadowOf(testEditor)
        templateFront = testEditor.editText
        templateFront.text.append(testNoteTypeQfmtEdit)
        advanceRobolectricLooper()
        assertTrue("Note type did not change after edit?", testEditor.noteTypeHasChanged())

        // Make sure we pass the edit to the Previewer
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview))
        advanceRobolectricLooper()
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        advanceRobolectricLooper()
        assertEquals("Previewer not started?", CardViewerActivity::class.java.name, shadowIntent.intentClass.name)
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )
        shadowTestEditor.receiveResult(startedIntent, Activity.RESULT_OK, Intent())

        // Save the template then fetch it from the collection to see if it was saved correctly
        var testEditorNoteTypeEdited = testEditor.tempNoteType?.notetype
        advanceRobolectricLooper()
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooper()
        val collectionBasicNoteTypeCopyEdited = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        testEditorNoteTypeEdited = col.notetypes.get(testEditorNoteTypeEdited!!.id)
        assertNotEquals("Note type is unchanged?", collectionBasicNoteTypeOriginal, collectionBasicNoteTypeCopyEdited)
        assertEquals(
            "note type did not save?",
            testEditorNoteTypeEdited.toString().trim(),
            collectionBasicNoteTypeCopyEdited.toString().trim(),
        )
        assertTrue("Note type does not have our change?", collectionBasicNoteTypeCopyEdited.toString().contains(testNoteTypeQfmtEdit))
    }

    @Test
    fun testDeleteTemplate() {
        val noteTypeName = "Basic (and reversed card)"

        // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()
        assertFalse("Note type should not have changed yet", testEditor.noteTypeHasChanged())
        assertEquals("Note type should have 2 templates now", 2, testEditor.tempNoteType?.templateCount)

        // Try to delete the template - click delete, click confirm for card delete, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()
        assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getAlertDialogText(true))
        clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
        advanceRobolectricLooper()
        assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
        assertEquals("Note type should have 1 template now", 1, testEditor.tempNoteType?.templateCount)

        // Try to delete the template again, but there's only one
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()
        assertEquals(
            "Did not show dialog about deleting only card?",
            getResourceString(R.string.card_template_editor_cant_delete),
            getAlertDialogText(true),
        )
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )

        // Save the change to the database and make sure there's only one template after
        var testEditorNoteTypeEdited = testEditor.tempNoteType?.notetype
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooper()
        val collectionBasicNoteTypeCopyEdited = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        testEditorNoteTypeEdited = col.notetypes.get(testEditorNoteTypeEdited!!.id)
        assertNotEquals("Note type is unchanged?", collectionBasicNoteTypeOriginal, collectionBasicNoteTypeCopyEdited)
        assertEquals(
            "Note type did not save?",
            testEditorNoteTypeEdited.toString().trim(),
            collectionBasicNoteTypeCopyEdited.toString().trim(),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testTemplateAdd() {
        // Make sure we test previewing a new card template - not working for real yet
        val noteTypeName = "Basic"
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()
        assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))

        // Try to add a template - click add, click confirm for card add, click confirm again for full sync
        val shadowTestEditor = shadowOf(testEditor)
        addCardType(testEditor, shadowTestEditor)
        // if AnkiDroid moves to match AnkiDesktop it will pop a dialog to confirm card create
        // Assert.assertEquals("Wrong dialog shown?", "This will create NN cards. Proceed?", getDialogText());
        // clickDialogButton(WhichButton.POSITIVE);
        assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
        assertEquals("Change not pending add?", 1, CardTemplateNotetype.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempNoteType!!, 0))
        assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
        assertTrue("Ordinal not pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))
        assertEquals("Note type should have 2 templates now", 2, testEditor.tempNoteType!!.templateCount)

        // Make sure we pass the new template to the Previewer
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_preview))
        val startedIntent = shadowTestEditor.nextStartedActivity
        val shadowIntent = shadowOf(startedIntent)
        assertEquals("Previewer not started?", CardViewerActivity::class.java.name, shadowIntent.intentClass.name)
        assertEquals(
            "Change already in database?",
            collectionBasicNoteTypeOriginal.toString().trim(),
            getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
        )

        // Save the change to the database and make sure there are two templates after
        var testEditorNoteTypeEdited = col.notetypes.get(testEditor.tempNoteType!!.notetype.id)
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooper()
        val collectionBasicNoteTypeCopyEdited = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        assertNotEquals("Note type is unchanged?", collectionBasicNoteTypeOriginal, collectionBasicNoteTypeCopyEdited)
        testEditorNoteTypeEdited = col.notetypes.get(testEditorNoteTypeEdited!!.id)

        assertEquals(
            "Note type did not save?",
            testEditorNoteTypeEdited.toString().trim(),
            collectionBasicNoteTypeCopyEdited.toString().trim(),
        )
    }

    /**
     * In a note type with two card templates using different fields, some notes may only use card 1,
     * and some may only use card 2. If you delete the 2nd template,
     * it will cause the notes that only use card 2 to disappear.
     *
     * So the unit test would then be to make a note type like the "basic (optional reverse card)"
     * with two fields Enable1 and Enable2, and two templates "card 1" and "card 2".
     * Both cards use selective generation, so they're empty unless the corresponding field is set.
     *
     * So then in the unit test you make the note type, add the two templates, then you add two notes,
     * with Enable1 and Enable2 respectively set to "y".
     * Then you try to delete one of the templates and it should fail
     *
     * (question: but I thought deleting one should work - still one card left to maintain the note,
     * and second template delete should fail since we finally get to a place where no cards are left?
     * I am having trouble creating selectively generated cards though - I can do one optional field but not 2 ugh)
     *
     */
    @Test
    fun testDeleteTemplateWithSelectivelyGeneratedCards() =
        runTest {
            val noteTypeName = "Basic (optional reversed card)"
            val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)

            // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
            val intent = Intent(Intent.ACTION_VIEW)
            intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
            val templateEditorController =
                Robolectric
                    .buildActivity(
                        CardTemplateEditor::class.java,
                        intent,
                    ).create()
                    .start()
                    .resume()
                    .visible()
            saveControllerForCleanup(templateEditorController)
            val testEditor = templateEditorController.get()
            assertFalse("Note type should not have changed yet", testEditor.noteTypeHasChanged())
            assertEquals("Note type should have 2 templates now", 2, testEditor.tempNoteType?.templateCount)
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))

            // Try to delete Card 1 template - click delete, check confirm for card delete popup indicating it was possible, then dismiss it
            val shadowTestEditor = shadowOf(testEditor)
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals("Wrong dialog shown?", "Delete the “Card 1” card type, and its 0 cards?", getAlertDialogText(true))
            clickAlertDialogButton(DialogInterface.BUTTON_NEGATIVE, true)
            advanceRobolectricLooper()
            assertFalse("Note type should not have changed", testEditor.noteTypeHasChanged())

            // Create note with forward and back info, Add Reverse is empty, so should only be one card
            val selectiveGeneratedNote = col.newNote(collectionBasicNoteTypeOriginal)
            selectiveGeneratedNote.setField(0, "TestFront")
            selectiveGeneratedNote.setField(1, "TestBack")
            val fields = selectiveGeneratedNote.fields
            for (field in fields) {
                Timber.d("Got a field: %s", field)
            }
            col.addNote(selectiveGeneratedNote)
            assertEquals("selective generation should result in one card", 1, getNoteTypeCardCount(collectionBasicNoteTypeOriginal))

            // Try to delete the template again, but there's selective generation means it would orphan the note
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(
                "Did not show dialog about deleting only card?",
                getResourceString(R.string.orphan_note_message),
                getAlertDialogText(true),
            )
            clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
            advanceRobolectricLooper()
            assertNull(
                "Can delete used template?",
                collectionBasicNoteTypeOriginal.getCardIds(0),
            )
            assertEquals(
                "Change already in database?",
                collectionBasicNoteTypeOriginal.toString().trim(),
                getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
            )
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
            assertEquals("Change incorrectly added to list?", 0, testEditor.templateChangeCount)

            // Assert can delete 'Card 2'
            assertNotNull("Cannot delete unused template?", collectionBasicNoteTypeOriginal.getCardIds(1))

            // Edit note to have Add Reverse set to 'y' so we get a second card
            selectiveGeneratedNote.setField(2, "y")
            selectiveGeneratedNote.flush()

            // - assert two cards
            assertEquals("should be two cards now", 2, getNoteTypeCardCount(collectionBasicNoteTypeOriginal))

            // - assert can delete either Card template but not both
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(0))
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(1))
            assertNull("Can delete both templates?", collectionBasicNoteTypeOriginal.getCardIds(0, 1))

            // A couple more notes to make sure things are okay
            val secondNote = col.newNote(collectionBasicNoteTypeOriginal)
            secondNote.setField(0, "TestFront2")
            secondNote.setField(1, "TestBack2")
            secondNote.setField(2, "y")
            col.addNote(secondNote)

            // - assert can delete either Card template but not both
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(0))
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(1))
            assertNull("Can delete both templates?", collectionBasicNoteTypeOriginal.getCardIds(0, 1))
        }

    /**
     * Normal template deletion - with no selective generation should of course work
     */
    @Test
    fun testDeleteTemplateWithGeneratedCards() =
        runTest {
            val noteTypeName = "Basic (and reversed card)"
            var collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)

            // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
            var intent = Intent(Intent.ACTION_VIEW)
            intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
            var templateEditorController =
                Robolectric
                    .buildActivity(
                        CardTemplateEditor::class.java,
                        intent,
                    ).create()
                    .start()
                    .resume()
                    .visible()
            saveControllerForCleanup(templateEditorController)
            var testEditor = templateEditorController.get()
            assertFalse("Note type should not have changed yet", testEditor.noteTypeHasChanged())
            assertEquals("Note type should have 2 templates now", 2, testEditor.tempNoteType?.templateCount)
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))

            // Create note with forward and back info
            val selectiveGeneratedNote = col.newNote(collectionBasicNoteTypeOriginal)
            selectiveGeneratedNote.setField(0, "TestFront")
            selectiveGeneratedNote.setField(1, "TestBack")
            col.addNote(selectiveGeneratedNote)
            assertEquals("card generation should result in two cards", 2, getNoteTypeCardCount(collectionBasicNoteTypeOriginal))

            // Test if we can delete the template - should be possible - but cancel the delete
            var shadowTestEditor = shadowOf(testEditor)
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(
                "Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"),
                getAlertDialogText(true),
            )
            clickAlertDialogButton(DialogInterface.BUTTON_NEGATIVE, true)
            advanceRobolectricLooper()
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(0))
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(1))
            assertNull("Can delete both templates?", collectionBasicNoteTypeOriginal.getCardIds(0, 1))
            assertEquals(
                "Change in database despite no change?",
                collectionBasicNoteTypeOriginal.toString().trim(),
                getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
            )
            assertEquals("Note type should have 2 templates still", 2, testEditor.tempNoteType?.templateCount)

            // Add a template - click add, click confirm for card add, click confirm again for full sync
            addCardType(testEditor, shadowTestEditor)
            // the templates must be different
            testEditor.tempNoteType!!.getTemplate(2).qfmt += "different_template"
            assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
            assertEquals(
                "Change added but not adjusted correctly?",
                2,
                CardTemplateNotetype.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempNoteType!!, 0),
            )
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))
            assertTrue("Ordinal not pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(2))
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
            advanceRobolectricLooper()
            assertFalse("Note type should now be unchanged", testEditor.noteTypeHasChanged())
            assertEquals("card generation should result in three cards", 3, getNoteTypeCardCount(collectionBasicNoteTypeOriginal))
            // reload the note type for future comparison after saving the edit
            collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)

            // Start the CardTemplateEditor back up after saving (which closes the thing...)
            intent = Intent(Intent.ACTION_VIEW)
            intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
            templateEditorController =
                Robolectric
                    .buildActivity(CardTemplateEditor::class.java, intent)
                    .create()
                    .start()
                    .resume()
                    .visible()
            saveControllerForCleanup(templateEditorController)
            testEditor = templateEditorController.get()
            shadowTestEditor = shadowOf(testEditor)
            assertFalse("Note type should not have changed yet", testEditor.noteTypeHasChanged())
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(2))
            assertEquals("Note type should have 3 templates now", 3, testEditor.tempNoteType?.templateCount)

            // Add another template - but we work in memory for a while before saving
            addCardType(testEditor, shadowTestEditor)
            assertEquals(
                "Change added but not adjusted correctly?",
                3,
                CardTemplateNotetype.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempNoteType!!, 0),
            )
            assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
            assertEquals("Note type should have 4 templates now", 4, testEditor.tempNoteType?.templateCount)
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(2))
            assertTrue("Ordinal not pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(3))
            assertEquals(
                "Change added but not adjusted correctly?",
                3,
                CardTemplateNotetype.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempNoteType!!, 0),
            )

            // Delete two pre-existing templates for real now - but still without saving it out, should work fine
            advanceRobolectricLooper()
            testEditor.mainBinding.cardTemplateEditorPager.currentItem = 0
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(
                "Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 1"),
                getAlertDialogText(true),
            )
            clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            testEditor.mainBinding.cardTemplateEditorPager.currentItem = 0
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(
                "Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
                getAlertDialogText(true),
            )
            clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
            advanceRobolectricLooper()

            // - assert can delete any 1 or 2 Card templates but not all
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(0))
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(1))
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(2))
            assertNotNull("Cannot delete two templates?", collectionBasicNoteTypeOriginal.getCardIds(0, 1))
            assertNotNull("Cannot delete two templates?", collectionBasicNoteTypeOriginal.getCardIds(0, 2))
            assertNotNull("Cannot delete two templates?", collectionBasicNoteTypeOriginal.getCardIds(1, 2))
            assertNull("Can delete all templates?", collectionBasicNoteTypeOriginal.getCardIds(0, 1, 2))
            assertEquals(
                "Change already in database?",
                collectionBasicNoteTypeOriginal.toString().trim(),
                getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
            )
            assertEquals(
                "Change added but not adjusted correctly?",
                1,
                CardTemplateNotetype.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempNoteType!!, 0),
            )
            assertEquals(
                "Change incorrectly pending add?",
                -1,
                CardTemplateNotetype.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempNoteType!!, 1),
            )
            assertEquals(
                "Change incorrectly pending add?",
                -1,
                CardTemplateNotetype.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempNoteType!!, 2),
            )

            // Now confirm everything to persist it to the database
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            assertNotEquals(
                "Change not in database?",
                collectionBasicNoteTypeOriginal.toString().trim(),
                getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
            )
            assertEquals("Note type should have 2 templates now", 2, getCurrentDatabaseNoteTypeCopy(noteTypeName).templates.length())
            assertEquals("should be two cards", 2, getNoteTypeCardCount(collectionBasicNoteTypeOriginal))
        }

    /**
     * Deleting a template you just added - but in the same ordinal as a previous pending delete - should get it's card count correct
     */
    @Test
    fun testDeletePendingAddExistingCardCount() =
        runTest {
            val noteTypeName = "Basic (optional reversed card)"
            val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)

            // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
            val intent = Intent(Intent.ACTION_VIEW)
            intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
            val templateEditorController =
                Robolectric
                    .buildActivity(
                        CardTemplateEditor::class.java,
                        intent,
                    ).create()
                    .start()
                    .resume()
                    .visible()
            saveControllerForCleanup(templateEditorController)
            val testEditor = templateEditorController.get()
            assertFalse("Note type should not have changed yet", testEditor.noteTypeHasChanged())
            assertEquals("Note type should have 2 templates now", 2, testEditor.tempNoteType?.templateCount)
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))

            // Create note with forward and back info
            val selectiveGeneratedNote = col.newNote(collectionBasicNoteTypeOriginal)
            selectiveGeneratedNote.setField(0, "TestFront")
            selectiveGeneratedNote.setField(1, "TestBack")
            selectiveGeneratedNote.setField(2, "y")
            col.addNote(selectiveGeneratedNote)
            assertEquals("card generation should result in two cards", 2, getNoteTypeCardCount(collectionBasicNoteTypeOriginal))

            // Delete ord 1 / 'Card 2' and check the message
            val shadowTestEditor = shadowOf(testEditor)
            testEditor.mainBinding.cardTemplateEditorPager.currentItem = 1
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(
                "Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
                getAlertDialogText(true),
            )
            clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
            advanceRobolectricLooper()
            assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(0))
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(1))
            assertNull("Can delete both templates?", collectionBasicNoteTypeOriginal.getCardIds(0, 1))
            assertEquals(
                "Change in database despite no save?",
                collectionBasicNoteTypeOriginal.toString().trim(),
                getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
            )
            assertEquals("Note type should have 1 template", 1, testEditor.tempNoteType?.templateCount)

            // Add a template - click add, click confirm for card add, click confirm again for full sync
            addCardType(testEditor, shadowTestEditor)
            assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
            assertEquals(
                "Change added but not adjusted correctly?",
                1,
                CardTemplateNotetype.getAdjustedAddOrdinalAtChangeIndex(testEditor.tempNoteType!!, 1),
            )
            assertFalse("Ordinal pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(0))
            assertTrue("Ordinal not pending add?", testEditor.tempNoteType.isOrdinalPendingAdd(1))
            assertEquals("Note type should have 2 templates", 2, testEditor.tempNoteType?.templateCount)

            // Delete ord 1 / 'Card 2' again and check the message - it's in the same spot as the pre-existing template but there are no cards actually associated
            testEditor.mainBinding.cardTemplateEditorPager.currentItem = 1
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(
                "Did not show dialog about deleting template and it's card?",
                getQuantityString(R.plurals.card_template_editor_confirm_delete, 0, 0, CollectionManager.TR.cardTemplatesCard(2)),
                getAlertDialogText(true),
            )
            clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
            advanceRobolectricLooper()
            assertTrue("Note type should have changed", testEditor.noteTypeHasChanged())
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(0))
            assertNotNull("Cannot delete template?", collectionBasicNoteTypeOriginal.getCardIds(1))
            assertNull("Can delete both templates?", collectionBasicNoteTypeOriginal.getCardIds(0, 1))
            assertEquals(
                "Change in database despite no save?",
                collectionBasicNoteTypeOriginal.toString().trim(),
                getCurrentDatabaseNoteTypeCopy(noteTypeName).toString().trim(),
            )
            assertEquals("Note type should have 1 template", 1, testEditor.tempNoteType?.templateCount)

            // Save it out and make some assertions
            assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_confirm))
            advanceRobolectricLooper()
            assertFalse("Note type should now be unchanged", testEditor.noteTypeHasChanged())
            assertEquals("card generation should result in 1 card", 1, getNoteTypeCardCount(collectionBasicNoteTypeOriginal))
        }

    @Test
    fun testDeckOverride() {
        val noteTypeName = "Basic (optional reversed card)"
        val noteType = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", noteType.id)
        val editor = super.startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        val template = editor.tempNoteType?.getTemplate(0)
        MatcherAssert.assertThat("Deck ID element should exist", template?.jsonObject?.has("did"), Matchers.equalTo(true))
        MatcherAssert.assertThat("Deck ID element should be null", template?.jsonObject?.get("did"), Matchers.equalTo(JSONObject.NULL))
        editor.onDeckSelected(SelectableDeck.Deck(1, "hello"))
        MatcherAssert.assertThat("Deck ID element should be changed", template?.jsonObject?.get("did"), Matchers.equalTo(1L))
        editor.onDeckSelected(null)
        MatcherAssert.assertThat("Deck ID element should exist", template!!.jsonObject.has("did"), Matchers.equalTo(true))
        MatcherAssert.assertThat("Deck ID element should be null", template.jsonObject["did"], Matchers.equalTo(JSONObject.NULL))
    }

    @Test
    fun `ensure 'Discard changes' dialog is enabled after note type changes - Issue 18518`() {
        fun assertDiscardChangesDialogShown(shadowEditor: ShadowActivity) {
            advanceRobolectricLooper()
            assertTrue("Unable to click?", shadowEditor.clickMenuItem(android.R.id.home))
            advanceRobolectricLooper()
            assertEquals("Wrong dialog shown?", "Discard changes?", getAlertDialogText(true))
            clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, false)
        }

        // Case 1: Show dialog after deck override
        withCardTemplateEditor {
            val shadowEditor = shadowOf(this)
            onDeckSelected(SelectableDeck.Deck(1, "hello"))
            assertDiscardChangesDialogShown(shadowEditor)
        }

        // Case 2: Show dialog after changing card browser appearance
        withCardTemplateEditor {
            val shadowEditor = shadowOf(this)
            assertTrue(
                "Unable to click?",
                shadowEditor.clickMenuItem(R.id.action_card_browser_appearance),
            )
            advanceRobolectricLooper()
            shadowEditor.receiveResult(
                shadowEditor.nextStartedActivity,
                Activity.RESULT_OK,
                Intent()
                    .putExtra(CardTemplateBrowserAppearanceEditor.INTENT_QUESTION_FORMAT, "q")
                    .putExtra(CardTemplateBrowserAppearanceEditor.INTENT_ANSWER_FORMAT, "a"),
            )
            assertDiscardChangesDialogShown(shadowEditor)
        }

        // Case 3: Show dialog after adding a card type
        withCardTemplateEditor {
            val shadowEditor = shadowOf(this)
            addCardType(this, shadowEditor)
            assertDiscardChangesDialogShown(shadowEditor)
        }
    }

    @Test
    fun testContentPreservedAfterChangingEditorView() {
        val noteTypeName = "Basic"

        // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()

        // Change the note type and make sure it registers as changed, but the database is unchanged
        val templateEditText = testEditor.editText
        val testNoteTypeQfmtEdit = "!@#$%^&*TEST*&^%$#@!"
        val updatedFrontContent = templateEditText.text.append(testNoteTypeQfmtEdit).toString()
        advanceRobolectricLooper()
        val cardTemplateFragment = testEditor.currentFragment
        val tempNoteType = testEditor.tempNoteType
        val cardId = 0
        // set Bottom Navigation View to Style
        cardTemplateFragment!!.setCurrentEditorView(R.id.styling_edit, cardId, tempNoteType!!.css)

        // set Bottom Navigation View to Front
        cardTemplateFragment.setCurrentEditorView(R.id.front_edit, cardId, tempNoteType.getTemplate(0).qfmt)

        // check if current content is updated or not
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(updatedFrontContent))
    }

    @Test
    fun testSaveButtonEnabledAfterException() {
        withCardTemplateEditor(noteType = col.notetypes.cloze) {
            editText.setText("New Random Template Text")

            // throw an exception to simulate failure
            this.tempNoteType = null

            confirmButton.performClick()

            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("Button should be clickable after failure", confirmButton.isClickable)
            assertTrue("Button should be enabled after failure", confirmButton.isEnabled)
        }
    }

    @Test
    fun testBottomNavigationViewLayoutTransition() {
        val noteTypeName = "Basic"

        // Start the CardTemplateEditor with a specific note type, and make sure the note type starts unchanged
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()

        // Change the note type and make sure it registers as changed, but the database is unchanged
        val templateEditText = testEditor.editText
        advanceRobolectricLooper()
        val cardTemplateFragment = testEditor.currentFragment
        val tempNoteType = testEditor.tempNoteType

        // check if current view is front(default) view
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(tempNoteType!!.getTemplate(0).qfmt))
        assumeThat(cardTemplateFragment!!.currentEditorViewId, Matchers.equalTo(R.id.front_edit))

        val cardId = 0
        // set Bottom Navigation View to Style
        cardTemplateFragment.setCurrentEditorView(R.id.styling_edit, cardId, tempNoteType.css)

        // check if current view is changed or not
        assumeThat(templateEditText.text.toString(), Matchers.equalTo(tempNoteType.css))
        assumeThat(cardTemplateFragment.currentEditorViewId, Matchers.equalTo(R.id.styling_edit))
    }

    @Test
    fun testInsertFieldInCorrectFragmentAfterNavigation() {
        val noteTypeName = "Basic (and reversed card)"

        // Start the CardTemplateEditor with a note type that has multiple templates
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy(noteTypeName)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.putExtra("noteTypeId", collectionBasicNoteTypeOriginal.id)
        val templateEditorController =
            Robolectric
                .buildActivity(CardTemplateEditor::class.java, intent)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(templateEditorController)
        val testEditor = templateEditorController.get()

        assertEquals("Note type should have 2 templates", 2, testEditor.tempNoteType?.templateCount)

        // Start on first fragment (Card 1)
        assertEquals("Should start on first fragment", 0, testEditor.viewPager.currentItem)
        advanceRobolectricLooper()

        val firstFragment = testEditor.currentFragment
        assertNotNull("First fragment should exist", firstFragment)
        // Use fragment's view to get EditText (activity.findViewById may return wrong view with offscreenPageLimit)
        val firstTemplateEditText = firstFragment!!.requireView().findViewById<EditText>(R.id.edit_text)
        val originalFirstContent = firstTemplateEditText.text.toString()

        // Navigate to second fragment (Card 2)
        testEditor.viewPager.currentItem = 1
        advanceRobolectricLooper()

        val secondFragment = testEditor.currentFragment
        assertNotNull("Second fragment should exist", secondFragment)
        // Use fragment's view to get EditText (activity.findViewById may return wrong view with offscreenPageLimit)
        val secondTemplateEditText = secondFragment!!.requireView().findViewById<EditText>(R.id.edit_text)
        val originalSecondContent = secondTemplateEditText.text.toString()

        // Navigate back to first fragment
        testEditor.viewPager.currentItem = 0
        advanceRobolectricLooper()

        val firstFragmentAgain = testEditor.currentFragment
        assertNotNull("First fragment should exist after navigation back", firstFragmentAgain)
        assertEquals("Should be back on first fragment", 0, testEditor.viewPager.currentItem)

        // Insert a field into the first fragment
        val fieldToInsert = "Front"
        val expectedFieldText = "{{$fieldToInsert}}"

        // Simulate showing the insert field dialog and selecting a field
        firstFragmentAgain!!.showInsertFieldDialog()
        advanceRobolectricLooper()

        val resultBundle = Bundle()
        resultBundle.putString(InsertFieldDialog.KEY_INSERTED_FIELD, expectedFieldText)
        testEditor.supportFragmentManager.setFragmentResult(firstFragmentAgain.insertFieldRequestKey, resultBundle)
        advanceRobolectricLooper()

        // Verify the field was inserted into the first fragment
        val updatedFirstContent = firstTemplateEditText.text.toString()
        assertTrue(
            "Field should be inserted into first fragment",
            updatedFirstContent.contains(expectedFieldText),
        )
        assertTrue(
            "First fragment content should have changed",
            updatedFirstContent != originalFirstContent,
        )

        // Navigate to second fragment to verify it wasn't modified
        testEditor.viewPager.currentItem = 1
        advanceRobolectricLooper()

        // Use currentFragment's view to get EditText
        val secondFragmentAfter = testEditor.currentFragment
        assertNotNull("Second fragment should exist after navigation", secondFragmentAfter)
        val secondTemplateEditTextAfter = secondFragmentAfter!!.requireView().findViewById<EditText>(R.id.edit_text)
        val secondContentAfter = secondTemplateEditTextAfter.text.toString()

        assertEquals(
            "Second fragment should not have been modified",
            originalSecondContent,
            secondContentAfter,
        )
        assertFalse(
            "Field should NOT be in second fragment",
            secondContentAfter.contains(expectedFieldText),
        )
    }

    @Test
    fun `tab changes succeed with tablet UI - Issue 19589`() =
        withSplitPaneUi {
            withCardTemplateEditor(col.notetypes.basicAndReversed) {
                selectTab(1)
                selectTab(0)

                assertThat(selectedTabPosition, equalTo(0))
            }
        }

    @Test
    fun `correct ord is previewed after tab change - tablet ui - issue 20097`() =
        withSplitPaneUi {
            withCardTemplateEditor(col.notetypes.basicAndReversed) {
                selectTab(1)

                assertThat(previewer.ord, equalTo(1))
            }
        }

    private fun addCardType(
        testEditor: CardTemplateEditor,
        shadowTestEditor: ShadowActivity,
    ) {
        assertTrue("Unable to click?", shadowTestEditor.clickMenuItem(R.id.action_add))
        advanceRobolectricLooper()
        val ordinal = testEditor.mainBinding.cardTemplateEditorPager.currentItem
        val numAffectedCards =
            if (!testEditor.tempNoteType.isOrdinalPendingAdd(ordinal)) {
                col.notetypes.tmplUseCount(testEditor.tempNoteType!!.notetype, ordinal)
            } else {
                0
            }
        assertEquals(
            "Did not show dialog about adding template and it's card?",
            getQuantityString(R.plurals.card_template_editor_confirm_add, numAffectedCards, numAffectedCards),
            getAlertDialogText(true),
        )
        clickAlertDialogButton(DialogInterface.BUTTON_POSITIVE, true)
    }

    @Test
    fun `template to markdown`() {
        val template =
            CardTemplate(
                front = "Hello World{{Front}}\n{{Extra}}",
                back = "{{FrontSide}}\n",
                style = ".card { }",
            )

        assertEquals(
            "markdown formatted template",
"""
**Front template**

```html
Hello World{{Front}}
{{Extra}}
```

**Back template**

```html
{{FrontSide}}

```

**Styling**

```css
.card { }
```
""".trim(),
            template.toMarkdown(targetContext),
        )
    }

    private fun getNoteTypeCardCount(notetype: NotetypeJson): Int {
        var cardCount = 0
        for (noteId in col.notetypes.nids(notetype)) {
            cardCount += col.getNote(noteId).numberOfCards()
        }
        return cardCount
    }

    private fun CardTemplateNotetype?.isOrdinalPendingAdd(ordinal: Int): Boolean {
        requireNotNull(this)
        return CardTemplateNotetype.isOrdinalPendingAdd(this, ordinal)
    }

    private val CardTemplateEditor.templateChangeCount
        get() = tempNoteType?.templateChanges?.size

    private suspend fun NotetypeJson.getCardIds(vararg ords: Int): List<Long>? = withCol { notetypes.getCardIdsForNoteType(id, ords) }
}

private val CardTemplateEditor.editText: EditText
    get() = this.findViewById(R.id.edit_text)

private val CardTemplateEditor.viewPager
    get() = this.mainBinding.cardTemplateEditorPager

fun RobolectricTest.withCardTemplateEditor(
    noteType: NotetypeJson = col.notetypes.basic,
    block: CardTemplateEditor.() -> Unit,
) {
    val intent = CardEditor(ntid = noteType.id).toIntent(targetContext)
    val activity = startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
    block(activity)
}

fun CardTemplateEditor.selectTab(index: Int) = topBinding.slidingTabs.selectTab(index)

val CardTemplateEditor.selectedTabPosition: Int get() = topBinding.slidingTabs.selectedTabPosition

val CardTemplateEditor.previewer: TemplatePreviewerFragment
    get() = this.supportFragmentManager.findFragmentById(R.id.fragment_container) as TemplatePreviewerFragment

val TemplatePreviewerFragment.ord: CardOrdinal
    get() = this.viewModel.ordFlow.value

val CardTemplateEditor.confirmButton: View
    get() = findViewById(R.id.action_confirm)
