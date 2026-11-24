// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.libanki.utils.remove
import net.ankiweb.rsdroid.BackendException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.IOException
import kotlin.test.junit5.JUnit5Asserter.assertNotNull
import kotlin.test.junit5.JUnit5Asserter.assertNull

@RunWith(AndroidJUnit4::class)
class CardTemplateNotetypeTest : RobolectricTest() {
    @Test
    fun `failed validation preserves a bundle saved before the save attempt`() =
        runTest {
            val original = col.notetypes.basicAndReversed
            val edited = CardTemplateNotetype(original.deepClone())
            edited.getTemplate(0).qfmt = "{{Missing}}"
            val state = edited.toBundle()

            try {
                edited.saveToDatabase()
                Assert.fail("An unknown field must fail validation")
            } catch (_: BackendException) {
                // The saved bundle must remain usable after validation fails.
            }

            val restored = CardTemplateNotetype.fromBundle(state)
            assertNotNull("Failed validation must preserve the saved editor state", restored)
            Assert.assertEquals(edited.notetype.toString(), restored!!.notetype.toString())
            restored.getTemplate(0).qfmt = original.templates[0].qfmt
            restored.saveToDatabase()
            assertNull("A successful save should clear the temporary file", CardTemplateNotetype.fromBundle(state))
        }

    @Test
    @Throws(Exception::class)
    fun testTempNoteTypeStorage() {
        // Start off with clean state in the cache dir
        CardTemplateNotetype.clearTempNoteTypeFiles()

        // Make sure save / retrieve works
        val tempModelPath =
            CardTemplateNotetype.saveTempNoteType(
                targetContext,
                NotetypeJson(
                    """{"foo": "bar"}""",
                ),
            )
        assertNotNull("Saving temp model unsuccessful", tempModelPath)
        val tempModel = CardTemplateNotetype.getTempNoteType(tempModelPath!!)
        assertNotNull("Temp model not read successfully", tempModel)
        Assert.assertEquals(JSONObject("{\"foo\": \"bar\"}").toString(), tempModel.toString())

        // Make sure clearing works
        Assert.assertEquals(1, CardTemplateNotetype.clearTempNoteTypeFiles().toLong())
        Timber.i("The following logged NoSuchFileException is an expected part of verifying a file delete.")
        try {
            CardTemplateNotetype.getTempNoteType(tempModelPath)
            Assert.fail("Should have caught an exception here because the file is missing")
        } catch (e: IOException) {
            // this is expected
        }
    }

    @Test
    fun `deletion checks preserve database ordinals across additions and deletions`() {
        val edited = CardTemplateNotetype(col.notetypes.basicAndReversed.deepClone())
        edited.appendTemplate("Third")
        edited.appendTemplate("Fourth")
        edited.deleteTemplate(1)
        edited.deleteTemplate(1)

        Assert.assertArrayEquals(intArrayOf(1, 0), edited.getDeleteDbOrds(0))
        Assert.assertEquals(0, edited.getOriginalOrdinal(0))
        assertNull("An unsaved addition must have no database ordinal", edited.getOriginalOrdinal(1))
        Assert.assertTrue(CardTemplateNotetype.isOrdinalPendingAdd(edited, 1))

        val restored = CardTemplateNotetype.fromBundle(edited.toBundle())!!
        Assert.assertArrayEquals(intArrayOf(1), restored.getDeleteDbOrds(1))
        restored.deleteTemplate(1)
        restored.appendTemplate("Replacement")
        Assert.assertArrayEquals(intArrayOf(1, 0), restored.getDeleteDbOrds(0))
        Assert.assertTrue(CardTemplateNotetype.isOrdinalPendingAdd(restored, 1))
    }

    @Test
    fun `deletion checks retain the order of multiple original deletions`() {
        val notetype = col.notetypes.basicAndReversed.deepClone()
        for (name in listOf("Third", "Fourth")) {
            notetype.templates.append(Notetypes.newTemplate(name))
        }
        Notetypes._updateTemplOrds(notetype)
        val edited = CardTemplateNotetype(notetype)
        edited.deleteTemplate(1)
        edited.deleteTemplate(0)

        Assert.assertArrayEquals(intArrayOf(1, 0, 2), edited.getDeleteDbOrds(0))
        Assert.assertEquals(2, edited.getOriginalOrdinal(0))
        Assert.assertEquals(3, edited.getOriginalOrdinal(1))
    }

    @Test
    fun `all reposition destinations preserve the cards belonging to each template`() =
        runTest {
            val original = col.notetypes.basicAndReversed
            col.notetypes.addTemplate(
                original,
                Notetypes.newTemplate("Third").apply {
                    qfmt = "{{Front}} third"
                    afmt = "{{Back}}"
                },
            )
            val note = addBasicAndReversedNote()
            val originalCards = note.cards().associate { it.id to it.template().name }
            for (from in 0..2) {
                for (to in 0..2) {
                    if (from == to) continue
                    val edited = CardTemplateNotetype(col.notetypes.get(original.id)!!.deepClone())
                    val expectedNames = original.templatesNames.toMutableList()
                    expectedNames.add(to, expectedNames.removeAt(from))
                    edited.repositionTemplate(from, to)
                    edited.saveToDatabase()

                    Assert.assertEquals(expectedNames, col.notetypes.get(original.id)!!.templatesNames)
                    Assert.assertEquals(originalCards, note.cards().associate { it.id to it.template().name })
                    col.undo()
                }
            }
        }

    @Test
    fun `mixed moves additions and deletions survive recreation and a failed save`() =
        runTest {
            val original = col.notetypes.basicAndReversed
            val note = addBasicAndReversedNote()
            val originalCards = note.cards().sortedBy { it.ord }
            val edited = CardTemplateNotetype(original.deepClone())
            edited.appendTemplate("New")
            edited.getTemplate(2).apply {
                qfmt = "{{Front}} new"
                afmt = "{{Back}}"
            }
            edited.repositionTemplate(2, 0)
            edited.deleteTemplate(2)
            edited.repositionTemplate(1, 0)
            Assert.assertArrayEquals(intArrayOf(1, 0), edited.getDeleteDbOrds(0))
            Assert.assertTrue(CardTemplateNotetype.isOrdinalPendingAdd(edited, 1))
            edited.getTemplate(0).qfmt = "{{Missing}}"

            val restored = CardTemplateNotetype.fromBundle(edited.toBundle())!!
            try {
                restored.saveToDatabase()
                Assert.fail("An unknown field must fail validation")
            } catch (_: BackendException) {
                Assert.assertEquals(originalCards.map { it.id }.toSet(), note.cids().toSet())
            }
            restored.getTemplate(0).qfmt = original.templates[0].qfmt
            restored.saveToDatabase()

            Assert.assertEquals(listOf("Card 1", "New"), col.notetypes.get(original.id)!!.templatesNames)
            Assert.assertEquals(originalCards[0].id, note.cards().single { it.ord == 0 }.id)
            Assert.assertFalse(note.cids().contains(originalCards[1].id))
            Assert.assertEquals(2, note.cids().size)
            col.undo()
            Assert.assertEquals(originalCards.map { it.id }.toSet(), note.cids().toSet())
        }

    private fun CardTemplateNotetype.appendTemplate(name: String) {
        notetype.templates.append(Notetypes.newTemplate(name))
        Notetypes._updateTemplOrds(notetype)
        addNewTemplate()
    }

    private fun CardTemplateNotetype.deleteTemplate(ordinal: Int) {
        removeTemplate(ordinal)
        notetype.templates.remove(getTemplate(ordinal))
        Notetypes._updateTemplOrds(notetype)
    }
}
