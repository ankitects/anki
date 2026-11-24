// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.instanteditor

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.instantnoteeditor.InstantEditorProgress
import com.ichi2.anki.instantnoteeditor.InstantEditorViewModel
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity
import com.ichi2.anki.instantnoteeditor.SaveNoteResult
import com.ichi2.anki.instantnoteeditor.clozePattern
import com.ichi2.anki.libanki.clozeNumbersInNote
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertIs

class InstantEditorViewModelTest : JvmTest() {
    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    @Test
    fun testSetUpNoteType_with_Cloze_NoteType() =
        runViewModelTest {
            assertEquals(InstantNoteEditorActivity.DialogType.SHOW_EDITOR_DIALOG, dialogType.value)
        }

    @Test
    fun testSetUpNoteType_with_NoCloze_NoteType() =
        runTest {
            col.notetypes
                .all()
                .filter { it.isCloze }
                .forEach { col.backend.removeNotetype(it.id) }
            val viewModel = InstantEditorViewModel()
            advanceUntilIdle()
            assertEquals(InstantNoteEditorActivity.DialogType.NO_CLOZE_NOTE_TYPES_DIALOG, viewModel.dialogType.value)
        }

    @Test
    fun `test cloze number reset to 1`() =
        runViewModelTest {
            val sentenceArray = mutableListOf("Hello", "world")

            toggleAllClozeDeletions(sentenceArray)

            assertEquals("all clozes are detected", clozeDeletionCount, 2)

            toggleAllClozeDeletions(sentenceArray)

            assertEquals("all cloze deletions are removed", clozeDeletionCount, 0)
            assertEquals("cloze number is reset if there are no clozes", currentClozeNumber.value, 1)
        }

    @Test
    fun `test cloze number is reset to max value from cloze list`() =
        runViewModelTest {
            val sentenceArray = mutableListOf("Hello", "world", "this", "is", "test", "sentence")

            // cloze on the first 3 words
            toggleClozeDeletions(sentenceArray, 0, 1, 2)

            // disable cloze on the first 2, leaving "this" as {{c3::
            toggleClozeDeletions(sentenceArray, 0, 1)

            assertEquals("cloze number is 'Current Cloze Number + 1'", currentClozeNumber.value, 4)

            // remove the remaining cloze all clozes
            toggleClozeDeletion(sentenceArray, 2)
            assertEquals("cloze number is reset if all clozes are removed", currentClozeNumber.value, 1)
        }

    @Test
    fun testSavingNoteWithNoCloze() =
        runViewModelTest {
            editorNote.setField(0, "Hello")
            val result = checkAndSaveNote()

            assertEquals(CollectionManager.TR.addingYouHaveAClozeDeletionNote(), saveNoteResult(result))
        }

    @Test
    fun testSavingNoteWithEmptyFields() =
        runViewModelTest {
            editorNote.setField(0, "{{c1::Hello}}")

            val result = checkAndSaveNote()

            assertEquals("Success", saveNoteResult(result))
        }

    @Test
    fun testSavingNoteWithClozeFields() =
        runViewModelTest {
            val result = checkAndSaveNote()

            assertEquals(CollectionManager.TR.addingTheFirstFieldIsEmpty(), saveNoteResult(result))
        }

    @Test
    fun `saving a note reports progress`() =
        runViewModelTest {
            editorNote.setField(0, "{{c1::Hello}}")

            progressManager.progress.test {
                assertIs<ViewModelProgress.Idle>(awaitItem())
                checkAndSaveNote()
                val active = assertIs<ViewModelProgress.Active<InstantEditorProgress>>(awaitItem())
                assertEquals(InstantEditorProgress.SAVING_NOTE, active.message)
                assertIs<ViewModelProgress.Idle>(awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun testCheckAndSaveNote_NullEditorNote_ReturnsFailure() =
        runViewModelTest {
            val result = checkAndSaveNote()

            assertTrue(result is SaveNoteResult.Warning)
        }

    @Test
    fun buildClozeTextTest() =
        runViewModelTest {
            val text = "test"
            val result = buildClozeText(text)

            assertEquals("{{c1::test}}", result)
        }

    @Test
    fun `buildClozeText handles undo word`() =
        runViewModelTest {
            val text = "{{c1::Word}}"
            val result = buildClozeText(text)

            assertEquals("Word", result)
        }

    @Test
    fun `buildClozeText does not use a cloze number for text without a word`() =
        runViewModelTest {
            assertEquals("🙂", buildClozeText("🙂"))

            assertEquals("{{c1::test}}", buildClozeText("test"))
        }

    @Test
    fun testExtractWordsIncludingClozes() =
        runViewModelTest {
            val sentence = "This is a {{c1::test}} sentence with {{c2::multiple}} clozes."
            setClozeFieldText(sentence)
            val expectedWords = listOf("This", "is", "a", "{{c1::test}}", "sentence", "with", "{{c2::multiple}}", "clozes.")
            val extractedWords = getWordsFromFieldText()
            assertEquals(expectedWords, extractedWords)
        }

    @Test
    fun testExtractWordsIncludingPunctuations() =
        runViewModelTest {
            val sentence = "This is a {{c1::test}}!! sentence with {{c2::multiple}} clozes?"
            setClozeFieldText(sentence)
            val expectedWords = listOf("This", "is", "a", "{{c1::test}}!!", "sentence", "with", "{{c2::multiple}}", "clozes?")
            val extractedWords = getWordsFromFieldText()
            assertEquals(expectedWords, extractedWords)
        }

    @Test
    fun `words keep text touching a cloze`() =
        runViewModelTest {
            setClozeFieldText("at {{c1::20}}°C or €{{c2::100}}")
            val expectedWords = listOf("at", "{{c1::20}}°C", "or", "€{{c2::100}}")
            assertEquals(expectedWords, getWordsFromFieldText())
        }

    @Test
    fun `touching clozes are separate words`() =
        runViewModelTest {
            setClozeFieldText("{{c1::a}}-{{c2::b}}{{c3::c}}")
            val expectedWords = listOf("{{c1::a}}-", "{{c2::b}}", "{{c3::c}}")
            assertEquals(expectedWords, getWordsFromFieldText())
        }

    @Test
    fun `a later cloze containing a space stays one word`() =
        runViewModelTest {
            setClozeFieldText("{{c1::born in}}-{{c2::New York}}")
            val expectedWords = listOf("{{c1::born in}}-", "{{c2::New York}}")
            assertEquals(expectedWords, getWordsFromFieldText())
        }

    @Test
    fun `editing another chip preserves a nested chemical formula`() =
        runViewModelTest {
            setClozeFieldText("{{c1::H2{{c2::O}}}} water")
            val words = getWordsFromFieldText().toMutableList()
            val waterIndex = words.indexOf("water")
            words[waterIndex] = buildClozeText(words[waterIndex])
            setClozeFieldText(words.joinToString(" "))
            assertEquals("{{c1::H2{{c2::O}}}} " + words[waterIndex], actualClozeFieldText.value)
        }

    @Test
    fun `nested cloze does not split its outer markup into incomplete chips`() =
        runViewModelTest {
            setClozeFieldText("{{c1::H2{{c2::O}}}} water")
            assertEquals(listOf("{{c1::H2{{c2::O}}}}", "water"), getWordsFromFieldText())
        }

    @Test
    fun `a new chip does not reuse a touching cloze ordinal`() =
        runViewModelTest {
            setClozeFieldText("{{c1::born in}}-{{c2::New York}} extra")
            val words = getWordsFromFieldText().toMutableList()
            val extraIndex = words.indexOf("extra")

            words[extraIndex] = buildClozeText(words[extraIndex])
            setClozeFieldText(words.joinToString(" "))

            assertEquals("{{c3::extra}}", words[extraIndex])
        }

    @Test
    fun `a new chip does not reuse a nested cloze ordinal`() =
        runViewModelTest {
            setClozeFieldText("{{c1::H2{{c2::O}}}} water")
            val words = getWordsFromFieldText().toMutableList()
            val waterIndex = words.indexOf("water")

            words[waterIndex] = buildClozeText(words[waterIndex])
            setClozeFieldText(words.joinToString(" "))

            assertEquals("{{c1::H2{{c2::O}}}} {{c3::water}}", actualClozeFieldText.value)
        }

    @Test
    fun `renumbering an outer chip preserves every nested cloze ordinal`() =
        runViewModelTest {
            setClozeFieldText("{{c1::Water contains {{c2::hydrogen}} and {{c3::oxygen}}.}}")
            val words = getWordsFromFieldText().toMutableList()

            // This is the update performed after long-pressing the outer chip.
            words[0] = updateClozeNumber(words[0], 4)
            setClozeFieldText(words.joinToString(" "))

            assertEquals(
                "{{c4::Water contains {{c2::hydrogen}} and {{c3::oxygen}}.}}",
                actualClozeFieldText.value,
            )
        }

    @Test
    fun `renumbering a chip changes only the number shown by its badge`() =
        runViewModelTest {
            val originalText = "{{c1::{x} + {{c2::y}}}}"
            setClozeFieldText(originalText)
            val words = getWordsFromFieldText().toMutableList()
            val selectedIndex = words.indexOfLast { getWordClozeNumber(it) != null }
            val displayedNumber = getWordClozeNumber(words[selectedIndex])!!
            val expectedOrdinals = (listOf(1, 2) - displayedNumber + 3).sorted()

            words[selectedIndex] = updateClozeNumber(words[selectedIndex], 3)
            setClozeFieldText(words.joinToString(" "))
            editorNote.setField(0, actualClozeFieldText.value!!)

            assertEquals(originalText.replace("{{c$displayedNumber::", "{{c3::"), actualClozeFieldText.value)
            assertEquals(expectedOrdinals, withCol { clozeNumbersInNote(editorNote) })
        }

    @Test
    fun `tapping a chip removes the cloze shown by its badge`() =
        runViewModelTest {
            setClozeFieldText("{{c1::{x} + {{c2::y}}}}")
            val words = getWordsFromFieldText().toMutableList()
            val selectedIndex = words.indexOfLast { getWordClozeNumber(it) != null }
            val displayedNumber = getWordClozeNumber(words[selectedIndex])!!

            words[selectedIndex] = buildClozeText(words[selectedIndex])
            setClozeFieldText(words.joinToString(" "))
            editorNote.setField(0, actualClozeFieldText.value!!)

            assertEquals(listOf(1, 2) - displayedNumber, withCol { clozeNumbersInNote(editorNote) })
        }

    @Test
    fun `removing a cloze after re-reading the words keeps the text`() =
        runViewModelTest {
            setClozeFieldText("at 20°C")
            val words = getWordsFromFieldText().toMutableList()
            toggleClozeDeletion(words, 1)
            setClozeFieldText(words.joinToString(" "))

            // the words are read again, e.g. when switching back from the edit mode
            val rereadWords = getWordsFromFieldText().toMutableList()
            toggleClozeDeletion(rereadWords, 1)

            assertEquals("at 20°C", rereadWords.joinToString(" "))
        }

    @Test
    fun `changing first chip number preserves second cloze number`() =
        runViewModelTest {
            setClozeFieldText("{{c1::Paris}}ỲÜí{{c2::France}}")
            val words = getWordsFromFieldText().toMutableList()

            // EditorChips performs this update after a long press on the first chip.
            words[0] = updateClozeNumber(words[0], 3)
            setClozeFieldText(words.joinToString(" "))

            assertEquals(
                "renumbering the first chip must preserve the other card",
                listOf(3, 2),
                clozePattern.findAll(actualClozeFieldText.value!!).map { it.groupValues[2].toInt() }.toList(),
            )
        }

    @Test
    fun testGetCleanClozeWords() =
        runViewModelTest {
            val testCases =
                listOf(
                    "{{c1::word}}" to "word",
                    "{{c2::another}}" to "another",
                    "{{c4::help}}!!" to "help!!",
                    "no cloze" to "no cloze",
                    "[{{c6::word}}]" to "[word]",
                )

            testCases.forEach { (input, expected) ->
                val cleanedWord = getCleanClozeWords(input)
                assertEquals(expected, cleanedWord)
            }
        }

    @Test
    fun `test words with internal punctuation`() =
        runViewModelTest {
            val text = "hello-world"
            val result = buildClozeText(text)

            assertEquals("{{c1::hello-world}}", result)
        }

    @Test
    fun `test words with internal underscore punctuation`() =
        runViewModelTest {
            val text = "hello_world"
            val result = buildClozeText(text)
            assertEquals("{{c1::hello_world}}", result)
        }

    @Test
    fun testSwitchingBetweenEditModes() =
        runViewModelTest {
            val word = "Word!"
            val expectedCloze = "{{c1::Word}}!"

            val result = buildClozeText(word)

            assertEquals(expectedCloze, result)

            val cleanWord = getCleanClozeWords(expectedCloze)

            assertEquals(word, cleanWord)
        }

    @Test
    fun `cloze numbering continues from existing clozes`() =
        runViewModelTest {
            val text =
                "This is the {{c1::first cloze}}. " +
                    "This should be the {{c2::second cloze}}. " +
                    "Similarly, this one as {{c3::third cloze}}."

            setClozeFieldText(text)

            val result = buildClozeText("fourth")

            assertEquals("{{c4::fourth}}", result)
        }

    @Test
    fun `removing a cloze does not reset numbering`() =
        runViewModelTest {
            val text = "{{c1::first}} {{c2::second}} {{c3::third}}"

            setClozeFieldText(text)

            val words = getWordsFromFieldText().toMutableList()

            words[1] = buildClozeText(words[1]) // remove c2

            val result = buildClozeText("fourth")

            assertEquals("{{c4::fourth}}", result)
        }

    @Test
    fun `toggling cloze mode does not reset numbering`() =
        runViewModelTest {
            val text = "{{c1::first}} {{c2::second}} {{c3::third}}"

            setClozeFieldText(text)

            toggleClozeMode() // switch to NO_INCREMENT mode
            toggleClozeMode() // switch back to INCREMENT mode

            val result = buildClozeText("fourth")

            assertEquals("{{c4::fourth}}", result)
        }

    @Test
    fun `toggle from increment to no_increment adjusts cloze counter`() =
        runViewModelTest {
            val text = "This is {{c1::first}} and {{c2::second}}"
            setClozeFieldText(text)

            assertEquals(3, currentClozeNumber.value)

            toggleClozeMode() // switch to NO_INCREMENT mode
            assertEquals(2, currentClozeNumber.value)

            toggleClozeMode() // switch back to INCREMENT mode
            assertEquals(3, currentClozeNumber.value)
        }

    @Test
    fun `setClozeFieldText does not increment in NO_INCREMENT mode`() =
        runViewModelTest {
            toggleClozeMode() // switch to NO_INCREMENT mode

            val text = "{{c1::first}} {{c2::second}}"
            setClozeFieldText(text)

            assertEquals(2, currentClozeNumber.value)
        }

    @Test
    fun `setClozeFieldText increments in INCREMENT mode`() =
        runViewModelTest {
            val text = "{{c1::first}} {{c2::second}}"
            setClozeFieldText(text)

            assertEquals(3, currentClozeNumber.value)
        }

    @Test
    fun `toggling cloze mode with no clozes keeps cloze number at 1`() =
        runViewModelTest {
            assertEquals(1, currentClozeNumber.value)

            toggleClozeMode() // INCREMENT -> NO_INCREMENT
            assertEquals(1, currentClozeNumber.value)

            toggleClozeMode() // NO_INCREMENT -> INCREMENT
            assertEquals(1, currentClozeNumber.value)
        }

    @Test
    fun `toggling cloze mode multiple times with no clozes keeps cloze number at 1`() =
        runViewModelTest {
            repeat(5) {
                toggleClozeMode()
            }
            assertEquals(1, currentClozeNumber.value)
        }

    private fun runViewModelTest(
        initViewModel: () -> InstantEditorViewModel = { InstantEditorViewModel() },
        testBody: suspend InstantEditorViewModel.() -> Unit,
    ) = runInstantEditorViewModelTest(initViewModel, testBody)

    private fun saveNoteResult(result: SaveNoteResult): String? =
        when (result) {
            is SaveNoteResult.Failure -> result.message

            SaveNoteResult.Success -> {
                // It doesn't return a string in case of success hence we mimic that that the check was successful
                "Success"
            }

            is SaveNoteResult.Warning -> result.message
        }

    companion object {
        fun AnkiTest.runInstantEditorViewModelTest(
            initViewModel: () -> InstantEditorViewModel = { InstantEditorViewModel() },
            testBody: suspend InstantEditorViewModel.() -> Unit,
        ) = runTest {
            val viewModel = initViewModel()
            testBody(viewModel)
        }
    }
}

private fun InstantEditorViewModel.toggleAllClozeDeletions(words: MutableList<String>) {
    for (index in words.indices) {
        words[index] = buildClozeText(words[index])
    }
}

@Suppress("SameParameterValue")
private fun InstantEditorViewModel.toggleClozeDeletions(
    words: MutableList<String>,
    vararg indices: Int,
) {
    for (index in indices) {
        words[index] = buildClozeText(words[index])
    }
}

@Suppress("SameParameterValue")
private fun InstantEditorViewModel.toggleClozeDeletion(
    words: MutableList<String>,
    index: Int,
) {
    words[index] = buildClozeText(words[index])
}
