// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.pages.viewmodel

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.pages.ImageOcclusion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import timber.log.Timber

/**
 * Arguments for either adding or editing an image occlusion note
 *
 * @see ImageOcclusionArgs.Add
 * @see ImageOcclusionArgs.Edit
 */
@Parcelize
sealed class ImageOcclusionArgs : Parcelable {
    @Parcelize
    data class Add(
        val imagePath: String,
        val noteTypeId: NoteTypeId,
        /**
         * The ID of the deck that was selected when the editor was opened.
         * Used to restore the deck after saving a note to prevent unexpected deck changes.
         */
        val originalDeckId: DeckId,
    ) : ImageOcclusionArgs()

    @Parcelize
    data class Edit(
        val noteId: NoteId,
    ) : ImageOcclusionArgs()

    /**
     * A [JSONObject] containing options for loading the [image occlusion page][ImageOcclusion].
     * This includes the type of operation ("add" or "edit"), and relevant IDs and paths.
     * IDs are decimal strings so JavaScript can convert them to bigint without losing precision.
     *
     * See 'IOMode' in https://github.com/ankitects/anki/blob/main/ts/routes/image-occlusion/lib.ts
     */
    fun toImageOcclusionMode() =
        when (this) {
            is Add ->
                JSONObject().also {
                    it.put("kind", "add")
                    it.put("imagePath", this.imagePath)
                    it.put("notetypeId", this.noteTypeId.toString())
                }
            is Edit ->
                JSONObject().also {
                    it.put("kind", "edit")
                    it.put("noteId", this.noteId.toString())
                }
        }
}

/**
 * ViewModel for the Image Occlusion fragment.
 */
class ImageOcclusionViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val args: ImageOcclusionArgs =
        checkNotNull(savedStateHandle[ARG_IMAGE_OCCLUSION]) { "$ARG_IMAGE_OCCLUSION required" }

    private val originalDeckId: DeckId? = (args as? ImageOcclusionArgs.Add)?.originalDeckId

    /**
     * The currently selected deck ID
     *
     * Only valid in 'ADD' mode
     */
    val selectedDeckIdFlow: MutableStateFlow<DeckId>? =
        originalDeckId?.let { MutableStateFlow(it) }

    /**
     * The currently selected deck name
     *
     * Only valid in 'ADD' mode
     */
    val deckNameFlow =
        selectedDeckIdFlow?.map { did -> withCol { decks.name(did) } }

    init {
        // if we are in 'add' mode, the current deck is used to add the note.
        // This is reverted in 'resetTemporaryDeckOverride'
        selectedDeckIdFlow
            ?.onEach { withCol { decks.select(it) } }
            ?.launchIn(viewModelScope)
    }

    /**
     * Handles the selection of a new deck.
     *
     * @param deckId The [DeckId] object representing the selected deck. Can be null if no deck is selected.
     */
    fun handleDeckSelection(deckId: DeckId) {
        selectedDeckIdFlow?.let { it.value = deckId } ?: run {
            Timber.w("deck selection is unavailable")
        }
    }

    /**
     * Executed when the 'save' operation is completed, before the UI receives the response
     */
    fun onSaveOperationCompleted() {
        Timber.i("save operation completed")
        if (originalDeckId != null) {
            resetTemporaryDeckOverride(originalDeckId)
        }
    }

    /**
     * Resets the current deck to the deck the screen was opened with
     *
     * Only for [ImageOcclusionArgs.Add] mode
     */
    private fun resetTemporaryDeckOverride(originalDeckId: DeckId) {
        // no need to reset if the DeckId was unchanged
        if (originalDeckId == selectedDeckIdFlow?.value) return

        // reset to the previous deck that the backend "saw" as selected, this
        // avoids other screens unexpectedly having their working decks modified
        // For example, the study screen: if a user backgrounds the screen, then
        // adds an occluded image via 'Share'
        viewModelScope.launch {
            Timber.i("resetting temporary deck override")
            withCol { backend.setCurrentDeck(originalDeckId) }
        }
    }

    companion object {
        const val ARG_IMAGE_OCCLUSION = "arg_image_occlusion"
    }
}
