// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.testutils.withSplitPaneUi
import com.ichi2.utils.getInputField
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class CardTemplateRepositionTest : RobolectricTest() {
    @Test
    fun `reposition keeps edits and editor selection with the moved template`() {
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            val expectedFront = tempNoteType!!.getTemplate(0).qfmt + " unsaved edit"
            currentFragment!!.binding.editText.setText(expectedFront)
            currentFragment!!.binding.bottomNavigation.selectedItemId = R.id.back_edit
            val expectedBack =
                currentFragment!!
                    .binding.editText.text
                    .toString()
            currentFragment!!.binding.editText.setSelection(4)

            moveSelectedTemplateTo(2)

            assertEquals(listOf("Card 2", "Card 1"), tempNoteType!!.notetype.templatesNames)
            assertEquals(1, selectedTabPosition)
            assertEquals(
                "Card 1",
                topBinding.slidingTabs
                    .getTabAt(1)!!
                    .text
                    .toString(),
            )
            assertEquals(R.id.back_edit, currentFragment!!.binding.bottomNavigation.selectedItemId)
            assertEquals(
                expectedBack,
                currentFragment!!
                    .binding.editText.text
                    .toString(),
            )
            assertEquals(4, currentFragment!!.binding.editText.selectionStart)
            assertEquals(expectedFront, tempNoteType!!.getTemplate(1).qfmt)
            assertEquals(0, tempNoteType!!.getOriginalOrdinal(1))
            assertTrue(noteTypeHasChanged())
            assertTrue(displayDiscardChangesCallback.isEnabled)

            // Edits after the move must write to the new position, without changing the other template.
            currentFragment!!.binding.editText.append(" moved back")
            assertEquals(expectedBack + " moved back", tempNoteType!!.getTemplate(1).afmt)
            assertEquals(
                col.notetypes.basicAndReversed.templates[1]
                    .afmt,
                tempNoteType!!.getTemplate(0).afmt,
            )

            moveSelectedTemplateTo(1)
            assertEquals(0, selectedTabPosition)
            assertEquals(
                expectedBack + " moved back",
                currentFragment!!
                    .binding.editText.text
                    .toString(),
            )
        }
    }

    @Test
    fun `saving a reposition preserves card identities and scheduling and supports undo`() =
        runTest {
            val note = addBasicAndReversedNote()
            val originalCards = note.cards().sortedBy { it.ord }
            val scheduledCard =
                originalCards.first().apply {
                    due = 123
                    ivl = 7
                }
            col.updateCard(scheduledCard)

            withCardTemplateEditor(col.notetypes.basicAndReversed) {
                moveSelectedTemplateTo(2)
                assertEquals(listOf("Card 1", "Card 2"), col.notetypes.basicAndReversed.templatesNames)
                assertEquals(listOf(0, 1), originalCards.map { col.getCard(it.id).ord })
                saveChanges()
            }

            assertEquals(listOf("Card 2", "Card 1"), col.notetypes.basicAndReversed.templatesNames)
            assertEquals(listOf(1, 0), originalCards.map { col.getCard(it.id).ord })
            assertEquals(123, col.getCard(scheduledCard.id).due)
            assertEquals(7, col.getCard(scheduledCard.id).ivl)
            assertEquals(originalCards.map { it.id }.toSet(), note.cids().toSet())

            col.undo()
            assertEquals(listOf("Card 1", "Card 2"), col.notetypes.basicAndReversed.templatesNames)
            assertEquals(listOf(0, 1), originalCards.map { col.getCard(it.id).ord })
            col.redo()
            assertEquals(listOf(1, 0), originalCards.map { col.getCard(it.id).ord })
        }

    @Test
    fun `deleting a moved template after recreation deletes only its cards`() {
        val note = addBasicAndReversedNote()
        val reverseCardId = note.cards().single { it.ord == 1 }.id
        val intent = CardTemplateEditor.getIntent(targetContext, col.notetypes.basicAndReversed.id, note.id, ord = 0)
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)

        controller.get().moveSelectedTemplateTo(2)
        controller.recreate()
        advanceRobolectricLooper()
        with(controller.get()) {
            assertEquals(1, selectedTabPosition)
            deleteSelectedTemplate()
            saveChanges()
        }

        assertEquals(listOf(reverseCardId), note.cids())
        assertEquals(listOf("Card 2"), col.notetypes.basicAndReversed.templatesNames)
    }

    @Test
    fun `moving and deleting an unsaved addition preserves the original cards`() {
        val note = addBasicAndReversedNote()
        val originalIds = note.cids().toSet()
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            assertTrue(shadowOf(this).clickMenuItem(R.id.action_add))
            advanceRobolectricLooper()
            clickAlertDialogButton { positiveButton }
            advanceRobolectricLooper()
            assertEquals(2, selectedTabPosition)

            moveSelectedTemplateTo(1)
            assertTrue(CardTemplateNotetype.isOrdinalPendingAdd(tempNoteType!!, 0))
            deleteSelectedTemplate()
            assertEquals(listOf("Card 1", "Card 2"), tempNoteType!!.notetype.templatesNames)
            assertFalse(noteTypeHasChanged())
            saveChanges()
        }
        assertEquals(originalIds, note.cids().toSet())
    }

    @Test
    fun `deletion checks after a move use the original ordinal`() {
        val noteType = col.notetypes.byName("Basic (optional reversed card)")!!
        col.addNote(
            col.newNote(noteType).apply {
                setField(0, "Front")
                setField(1, "Back")
            },
        )
        withCardTemplateEditor(noteType) {
            moveSelectedTemplateTo(2)
            assertTrue(shadowOf(this).clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(getResourceString(CommonString.orphan_note_message), getAlertDialogText(true))
            clickAlertDialogButton { positiveButton }
            assertEquals(2, tempNoteType!!.templateCount)
        }
    }

    @Test
    fun `deletion confirmation counts the moved card type`() {
        val noteType = col.notetypes.basicAndReversed
        val spare =
            Notetypes.newTemplate("Spare").apply {
                qfmt = "{{Front}} spare"
                afmt = "{{Back}}"
            }
        col.notetypes.addTemplate(noteType, spare)
        addBasicAndReversedNote("one", "back")
        addBasicAndReversedNote("two", "")

        withCardTemplateEditor(noteType) {
            selectTab(1)
            advanceRobolectricLooper()
            moveSelectedTemplateTo(1)
            assertTrue(shadowOf(this).clickMenuItem(R.id.action_delete))
            advanceRobolectricLooper()
            assertEquals(
                getQuantityString(CommonPlurals.card_template_editor_confirm_delete, 1, 1, "Card 2"),
                getAlertDialogText(true),
            )
        }
    }

    @Test
    fun `field insertion after a move previews the original card`() {
        val note = addBasicAndReversedNote()
        val forwardCardId = note.cards().single { it.ord == 0 }.id
        val intent = CardTemplateEditor.getIntent(targetContext, col.notetypes.basicAndReversed.id, note.id)
        val editor = startActivityNormallyOpenCollectionWithIntent(CardTemplateEditor::class.java, intent)
        with(editor) {
            moveSelectedTemplateTo(2)
            currentFragment!!.showInsertFieldDialog()
            advanceRobolectricLooper()
            val dialog = supportFragmentManager.fragments.filterIsInstance<InsertFieldDialog>().single()
            val metadata = ViewModelProvider(dialog)[InsertFieldDialogViewModel::class.java].metadata
            assertEquals(forwardCardId, metadata.cardId)
        }
    }

    @Test
    fun `split preview follows the moved template`() =
        withSplitPaneUi {
            withCardTemplateEditor(col.notetypes.basicAndReversed) {
                moveSelectedTemplateTo(2)
                assertEquals(1, previewer.ord)
                assertEquals(1, selectedTabPosition)
            }
        }

    @Test
    fun `cancel and the current position leave the note type unchanged`() {
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            assertTrue(shadowOf(this).clickMenuItem(R.id.action_reposition))
            advanceRobolectricLooper()
            clickAlertDialogButton { negativeButton }
            assertFalse(noteTypeHasChanged())
            moveSelectedTemplateTo(1)
            assertFalse(noteTypeHasChanged())
            assertFalse(displayDiscardChangesCallback.isEnabled)
            assertEquals(0, selectedTabPosition)
        }
    }

    @Test
    fun `a synced collection requires confirmation before repositioning`() {
        val note = addBasicAndReversedNote()
        val originalIds = note.cids().toSet()
        // Align the schema timestamp with the mock clock before simulating a completed sync.
        col.setSchemaModified()
        col.db.execute("update col set ls = scm")
        assertFalse(col.schemaChanged())
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            moveSelectedTemplateTo(2)
            assertEquals(getResourceString(CommonString.full_sync_confirmation), getAlertDialogText(true))
            assertFalse(noteTypeHasChanged())
            clickAlertDialogButton { negativeButton }
            advanceRobolectricLooper()
            assertFalse(noteTypeHasChanged())
            assertFalse(col.schemaChanged())
            assertEquals(0, selectedTabPosition)

            moveSelectedTemplateTo(2)
            clickAlertDialogButton { positiveButton }
            advanceRobolectricLooper()
            assertEquals(listOf("Card 2", "Card 1"), tempNoteType!!.notetype.templatesNames)
            assertTrue(col.schemaChanged())
            saveChanges()
        }
        assertEquals(originalIds, note.cids().toSet())
    }

    @Test
    fun `the current position does not require a full sync`() {
        col.setSchemaModified()
        col.db.execute("update col set ls = scm")
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            moveSelectedTemplateTo(1)
            assertFalse(noteTypeHasChanged())
            assertFalse(col.schemaChanged())
            assertFalse(ShadowDialog.getLatestDialog().isShowing)
        }
    }

    @Test
    fun `reposition is available only for standard note types with multiple templates`() {
        for (noteType in listOf(col.notetypes.basic, col.notetypes.cloze, col.notetypes.byName("Image Occlusion")!!)) {
            withCardTemplateEditor(noteType) {
                assertFalse(shadowOf(this).optionsMenu!!.findItem(R.id.action_reposition).isVisible)
            }
        }
        withCardTemplateEditor(col.notetypes.basicAndReversed) {
            assertTrue(shadowOf(this).optionsMenu!!.findItem(R.id.action_reposition).isVisible)
        }
    }

    private fun CardTemplateEditor.moveSelectedTemplateTo(position: Int) {
        assertTrue(shadowOf(this).clickMenuItem(R.id.action_reposition))
        advanceRobolectricLooper()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getInputField().setText(position.toString())
        assertTrue(dialog.positiveButton.isEnabled)
        dialog.positiveButton.performClick()
        advanceRobolectricLooper()
    }

    private fun CardTemplateEditor.deleteSelectedTemplate() {
        assertTrue(shadowOf(this).clickMenuItem(R.id.action_delete))
        advanceRobolectricLooper()
        clickAlertDialogButton { positiveButton }
        advanceRobolectricLooper()
    }

    private fun CardTemplateEditor.saveChanges() {
        assertTrue(shadowOf(this).clickMenuItem(R.id.action_confirm))
        advanceRobolectricLooper()
    }
}
