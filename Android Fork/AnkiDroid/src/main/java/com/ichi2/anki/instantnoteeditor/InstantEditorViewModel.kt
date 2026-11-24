// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.instantnoteeditor

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteFieldsCheckResult
import com.ichi2.anki.OnErrorListener
import com.ichi2.anki.checkNoteFieldsResponse
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.ext.replaceWith
import com.ichi2.anki.instantnoteeditor.InstantNoteEditorActivity.DialogType
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import com.ichi2.anki.utils.ext.getAllClozeTextFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.max

/**
 * ViewModel for managing instant note editing functionality.
 * This ViewModel provides methods for handling note editing operations and
 * managing the state related to instant note editing.
 */
class InstantEditorViewModel :
    ViewModel(),
    OnErrorListener,
    HasProgress<InstantEditorProgress> {
    override val onError = Channel<Exception>(Channel.BUFFERED)

    override val progressManager = ProgressManager<InstantEditorProgress>()

    /** Errors or Warnings related to the edit fields that might occur when trying to save note */
    val instantEditorError = MutableSharedFlow<String?>()

    val currentClozeNumber: StateFlow<Int>
        field = MutableStateFlow(1)

    // List to store the cloze integers
    private val intClozeList = mutableListOf<Int>()

    /** The number of words which are marked as cloze deletions */
    @VisibleForTesting
    val clozeDeletionCount get() = intClozeList.size

    val currentClozeMode: StateFlow<InstantNoteEditorActivity.ClozeMode>
        field = MutableStateFlow(InstantNoteEditorActivity.ClozeMode.INCREMENT)

    val actualClozeFieldText: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val editorMode: StateFlow<InstantNoteEditorActivity.EditMode>
        field = MutableStateFlow(InstantNoteEditorActivity.EditMode.SINGLE_TAP)

    /**
     * Gets the current editor note.
     */
    @VisibleForTesting
    lateinit var editorNote: Note

    /**
     * Representing the currently selected note type.
     *
     * @see NotetypeJson
     */
    val currentlySelectedNotetype: LiveData<NotetypeJson>
        field = MutableLiveData<NotetypeJson>()

    var deckId: DeckId? = null

    /** Representing the type of dialog to be displayed.
     * @see DialogType*/
    val dialogType: StateFlow<DialogType?>
        field = MutableStateFlow<DialogType?>(null)

    init {
        viewModelScope.launch {
            // get the current selected deck unless it's a filtered deck in which case use the
            // 'Default' deck
            val selectedDeck =
                withCol {
                    val selectedDeck = decks.getLegacy(decks.selected())
                    if (selectedDeck == null || selectedDeck.isFiltered) {
                        decks.getDefault()
                    } else {
                        selectedDeck
                    }
                }
            deckId = selectedDeck.id

            // setup the note type
            // TODO: Use did here
            val noteType = withCol { notetypes.all().firstOrNull { it.isCloze } }
            if (noteType == null) {
                dialogType.value = DialogType.NO_CLOZE_NOTE_TYPES_DIALOG
                return@launch
            }

            @Suppress("RedundantRequireNotNullCall") // postValue lint requires this
            val clozeNoteType = requireNotNull(noteType) { "noteType" }
            Timber.d("Changing to cloze type note")
            currentlySelectedNotetype.postValue(clozeNoteType)
            Timber.i("Using note type '%d", clozeNoteType.id)
            editorNote = withCol { Note.fromNotetypeId(this@withCol, clozeNoteType.id) }

            dialogType.value = DialogType.SHOW_EDITOR_DIALOG
        }
    }

    /**
     * Checks the note fields and calls [saveNote] if all fields are valid.
     * If [skipClozeCheck] is set to true, the cloze field check is skipped.
     *
     * @param skipClozeCheck Indicates whether to skip the cloze field check.
     * @return A [SaveNoteResult] indicating the outcome of the operation.
     */
    suspend fun checkAndSaveNote(skipClozeCheck: Boolean = false): SaveNoteResult =
        progressManager.withProgress(InstantEditorProgress.SAVING_NOTE) {
            if (skipClozeCheck) {
                return@withProgress saveNote()
            }

            val note = editorNote
            val result = checkNoteFieldsResponse(note)
            if (result is NoteFieldsCheckResult.Failure) {
                return@withProgress SaveNoteResult.Warning(result.localizedMessage)
            }
            Timber.d("Note fields check successful, saving note")
            instantEditorError.emit(null)
            saveNote()
        }

    /** Adds the note to the collection.
     * @return If the operation is successful, returns [SaveNoteResult.Success],
     * otherwise returns [SaveNoteResult.Failure].
     */
    private suspend fun saveNote(): SaveNoteResult {
        return try {
            val note = editorNote
            val deckId = deckId ?: return SaveNoteResult.Failure()

            Timber.d("Note and deck id not null, adding note")
            undoableOp { addNote(note, deckId) }

            SaveNoteResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Error saving note")
            CrashReportService.sendExceptionReport(e, "InstantEditorViewModel::saveNote")
            SaveNoteResult.Failure()
        }
    }

    private fun shouldResetClozeNumber(number: Int) {
        intClozeList.remove(number)

        currentClozeNumber.value =
            when {
                // Reset cloze number if the list is empty
                intClozeList.isEmpty() -> 1
                currentClozeMode.value == InstantNoteEditorActivity.ClozeMode.INCREMENT ->
                    (intClozeList.maxOrNull() ?: 0) + 1
                else -> currentClozeNumber.value
            }
    }

    /**
     * Extracts the cloze ordinals from text (if any).
     *
     * `"{{c2::text}} {{c1::more}}"` => `[2, 1]`
     *
     * Nested and touching clozes are included: `"{{c1::H2{{c2::O}}}}"` => `[1, 2]`
     */
    @CheckResult
    private fun getClozeOrdinals(text: String): List<Int> =
        clozeStartPattern
            .findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toList()

    /**
     * Retrieves all cloze text fields from the current editor note's note type.
     *
     * This method accesses the `editorNote` property to fetch its associated note type
     * and then retrieves all cloze text fields using the [getAllClozeTextFields] method.
     *
     * @return A list of strings representing the cloze text fields in the current editor note's note type.
     */
    fun getClozeFields(): List<String> = editorNote.notetype.getAllClozeTextFields()

    /**
     * Set the warning message to be displayed in editor dialog
     */
    fun setWarningMessage(message: String?) {
        viewModelScope.launch {
            instantEditorError.emit(message)
        }
    }

    private fun incrementClozeNumber() {
        Timber.d("Incrementing cloze number: $currentClozeNumber")
        currentClozeNumber.value++
    }

    fun setClozeFieldText(text: String?) {
        actualClozeFieldText.value = text
        intClozeList.replaceWith(getClozeOrdinals(text ?: ""))
        currentClozeNumber.value =
            when (currentClozeMode.value) {
                InstantNoteEditorActivity.ClozeMode.INCREMENT -> (intClozeList.maxOrNull() ?: 0) + 1
                InstantNoteEditorActivity.ClozeMode.NO_INCREMENT -> intClozeList.maxOrNull() ?: 1
            }
    }

    /**
     * Creates or removes the cloze deletion for a word around the given offset.
     *
     * This method first checks if the provided text already contains a cloze deletion.
     * If it does, it returns the clean text. If not, it creates a new cloze
     * deletion for the text, optionally including punctuation if present at the end of the text.
     *
     * The method also handles incrementing the cloze number based on the current cloze mode.
     *
     * @param text the text to be converted into a cloze deletion
     * @return the cloze-deleted version of the input text, clean text if already cloze,
     * or the unchanged text if it contains no word to cloze (e.g. an emoji)
     */
    fun buildClozeText(text: String): String {
        val cloze = processClozeUndo(text)
        if (cloze != null) {
            Timber.d("Text contains cloze, removed cloze")
            return cloze
        }

        Timber.d("Text doesn't have cloze, selecting and adding cloze")

        val matcher = clozeBuilderPattern.find(text)
        if (matcher == null) {
            Timber.d("Text has no word to cloze")
            return text
        }

        val clozeNumber = currentClozeNumber.value
        if (currentClozeMode.value == InstantNoteEditorActivity.ClozeMode.INCREMENT) {
            incrementClozeNumber()
        }
        intClozeList.add(clozeNumber)

        // Extract the first, second, and third regex groups from the matcher
        val punctuationAtStart = matcher.groupValues[1]
        val capturedWord = matcher.groupValues[2]
        val punctuationAtEnd = matcher.groupValues[4]

        // keep any text outside the match, e.g. the '°C' of '20°C'
        return text.replaceRange(matcher.range, "$punctuationAtStart{{c$clozeNumber::$capturedWord}}$punctuationAtEnd")
    }

    /**
     * This method extracts the cloze number (if present) from a given word
     * that indicates a cloze deletion (a blank replaced with a number).
     * If the pattern matches, it extracts the number from the captured group
     * and converts it to an integer, otherwise it returns null.
     *
     * Example: word is {{c`number`::`text`}} then it extracts `number`
     *
     * The outer cloze is used, as it's the one [updateClozeNumber] changes:
     * '{{c1::{x} + {{c2::y}}}}' is 1
     *
     * @param word The word to be analyzed for a cloze number.
     * @return The extracted cloze number as an integer if found, otherwise null.
     */
    fun getWordClozeNumber(word: String): Int? {
        val outerCloze = outerClozeRanges(word).firstOrNull() ?: return null
        return clozeStartPattern
            .matchAt(word, outerCloze.first)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }

    fun getWordsFromFieldText(): List<String> {
        val sentence = actualClozeFieldText.value ?: ""

        // Only split on spaces outside of clozes, as a cloze may contain spaces.
        // Text touching a cloze stays in the same word, e.g. '{{c1::20}}°C'
        val clozeRanges = outerClozeRanges(sentence)

        val words = mutableListOf<String>()
        var wordStart = 0

        spaceRegex
            .findAll(sentence)
            .filter { space -> clozeRanges.none { space.range.first in it } }
            .forEach { space ->
                words.addAll(splitBetweenClozes(sentence.substring(wordStart, space.range.first)))
                wordStart = space.range.last + 1
            }
        words.addAll(splitBetweenClozes(sentence.substring(wordStart)))

        return combineWordsWithPunctuation(words.filter { it.isNotEmpty() })
    }

    /**
     * Splits [word] before each cloze after the first, so a word has at most one cloze
     * and can be renumbered on its own: '{{c1::a}}-{{c2::b}}' -> ['{{c1::a}}-', '{{c2::b}}']
     *
     * A nested cloze stays in its outer cloze: '{{c1::H2{{c2::O}}}}' is one word
     */
    private fun splitBetweenClozes(word: String): List<String> {
        val outerClozes = outerClozeRanges(word)
        // clozePattern isn't used: its trailing punctuation would include the '{{' of the next cloze
        val clozeStarts =
            clozeStartPattern
                .findAll(word)
                .map { it.range.first }
                .filter { start -> outerClozes.none { start in it && start != it.first } }
        val wordStarts = listOf(0) + clozeStarts.drop(1) + word.length
        return wordStarts.zipWithNext { start, end -> word.substring(start, end) }
    }

    private fun combineWordsWithPunctuation(words: List<String>): List<String> {
        val combinedWords = mutableListOf<String>()

        var clozeEncountered = false

        words.forEachIndexed { _, word ->
            if (punctuationPattern.matches(word) && clozeEncountered) {
                // Combine punctuation with the previous cloze
                combinedWords[combinedWords.size - 1] += word
            } else {
                clozeEncountered = clozePattern.matches(word)
                combinedWords.add(word)
            }
        }

        return combinedWords
    }

    fun updateClozeNumber(
        word: String,
        newClozeNumber: Int,
    ): String =
        // a nested cloze is a separate card, so only the outer cloze is renumbered.
        // Reversed, so a renumbered cloze doesn't move the clozes before it
        outerClozeRanges(word).reversed().fold(word) { text, cloze ->
            val clozeStart = clozeStartPattern.matchAt(text, cloze.first) ?: return@fold text
            text.replaceRange(clozeStart.range, "{{c$newClozeNumber::")
        }

    /**
     * Removes the cloze deletion marker and surrounding delimiters from a word.
     *
     * @param word The word having a potential cloze deletion.
     * @return The cleaned word with the cloze deletion marker and delimiters removed,
     * or the original word if no match is found.
     */
    fun getCleanClozeWords(word: String): String {
        val regex = clozePattern
        return regex.replace(word) { matchResult ->
            (matchResult.groups[1]?.value ?: "") + (matchResult.groups[3]?.value ?: "") + (matchResult.groups[4]?.value ?: "")
        }
    }

    /**
     * Processes a cloze-deleted word and removes cloze if any, if a user tap the words twice then
     * it will detect the cloze and remove it and revert the text to its original state.
     *
     * @param text The text to be analyzed for cloze deletion.
     * @return The processed text with the cloze deletion marker and delimiters removed
     *         (if applicable), the original text if no match is found, or null
     *         if an undo is confirmed.
     */
    private fun processClozeUndo(text: String): String? {
        // the outer cloze is removed, as it's the one shown by the chip: [getWordClozeNumber]
        val outerCloze = outerClozeRanges(text).firstOrNull()
        val outerClozeStart = outerCloze?.let { clozeStartPattern.matchAt(text, it.first) }
        if (outerCloze != null && outerClozeStart != null) {
            outerClozeStart.groupValues[1].toIntOrNull()?.let { clozeNumber ->
                if (currentClozeNumber.value - clozeNumber == 1) {
                    decrementClozeNumber()
                }
                shouldResetClozeNumber(clozeNumber)
            }
            // remove its '}}' and then its '{{cN::', keeping the answer and any nested cloze
            return text
                .removeRange(outerCloze.last - 1..outerCloze.last)
                .removeRange(outerClozeStart.range)
        }

        val matchResult = clozePattern.find(text)
        val capturedClozeNumber = matchResult?.groups?.get(2)?.value
        if (capturedClozeNumber != null && currentClozeNumber.value - capturedClozeNumber.toInt() == 1) {
            decrementClozeNumber()
        }

        if (matchResult == null) {
            Timber.d("No match found for the input text")
            return null
        }

        matchResult.groups[2]
            ?.value
            ?.toInt()
            ?.let { shouldResetClozeNumber(it) }

        val punctuationAtStart: String = matchResult.groups[1]?.value ?: ""
        val capturedWord: String = matchResult.groups[3]?.value ?: ""
        val punctuationAtEnd: String = matchResult.groups[4]?.value ?: ""

        return text.replaceRange(matchResult.range, punctuationAtStart + capturedWord + punctuationAtEnd)
    }

    fun setEditorMode(mode: InstantNoteEditorActivity.EditMode) {
        editorMode.value = mode
    }

    private fun decrementClozeNumber() {
        val newValue = currentClozeNumber.value - 1
        currentClozeNumber.value = max(1, newValue)
    }

    fun toggleClozeMode() {
        val newMode =
            when (currentClozeMode.value) {
                InstantNoteEditorActivity.ClozeMode.INCREMENT -> {
                    if (intClozeList.isNotEmpty()) {
                        decrementClozeNumber()
                    }
                    InstantNoteEditorActivity.ClozeMode.NO_INCREMENT
                }
                InstantNoteEditorActivity.ClozeMode.NO_INCREMENT -> {
                    if (intClozeList.isNotEmpty()) {
                        incrementClozeNumber()
                    }
                    InstantNoteEditorActivity.ClozeMode.INCREMENT
                }
            }
        currentClozeMode.value = newMode
    }
}

enum class InstantEditorProgress {
    SAVING_NOTE,
}

/**
 * Represents the result of saving a note operation.
 * Has three possible outcomes: `Success`, `Failure`, and `Warning`.
 */
sealed class SaveNoteResult {
    /**
     * Indicates that the save note operation was successful.
     */
    data object Success : SaveNoteResult()

    /**
     * Indicates that the save note operation failed.
     *
     * @property message An optional message describing the reason for the failure.
     */
    data class Failure(
        val message: String? = null,
    ) : SaveNoteResult()

    /**
     * Indicates that the save note operation completed with a warning.
     *
     * Example, when user tries to save cloze field with no cloze
     *
     * @property message A message describing the warning.
     */
    data class Warning(
        val message: String?,
    ) : SaveNoteResult()
}

/**
 * A compiled regular expression pattern used to match cloze deletions within a string.
 *
 * This pattern is designed to identify text formatted as cloze deletions, which are commonly
 * used in educational materials. The pattern follows the format:
 * {{c`number`::`content`}} (optional punctuation)
 */
val clozePattern = Regex("""(\p{Punct}+)?\{\{c(\d+)::([^}]+?)\}\}(\p{Punct}+)?""")

private val punctuationPattern = Regex("""\p{Punct}+$""")

/** Used when splitting words **/
private val spaceRegex = Regex("\\s+")

/** The start of a cloze: {{c`number`:: **/
private val clozeStartPattern = Regex("""\{\{c(\d+)::""")

/**
 * The ranges of the clozes in [text] which aren't inside another cloze, from '{{' to the
 * matching '}}', so '{{c1::H2{{c2::O}}}}' is one range. A cloze without its '}}' has no range.
 */
private fun outerClozeRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var depth = 0
    var outerStart = 0
    var i = 0
    while (i < text.length) {
        val clozeStart = clozeStartPattern.matchAt(text, i)
        when {
            clozeStart != null -> {
                if (depth == 0) outerStart = i
                depth++
                i = clozeStart.range.last + 1
            }
            depth > 0 && text.startsWith("}}", i) -> {
                depth--
                if (depth == 0) ranges.add(outerStart..i + 1)
                i += 2
            }
            else -> i++
        }
    }
    return ranges
}

/** Used to build cloze text here word is not null **/
private val clozeBuilderPattern = "(\\p{Punct}*)((?:\\w|\\p{Pd}|\\p{Pc}|'|(\\(\\w+\\)))+)(\\p{Punct}*)".toRegex()
