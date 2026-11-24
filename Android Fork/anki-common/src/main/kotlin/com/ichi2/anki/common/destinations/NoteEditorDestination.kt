// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.ichi2.anki.common.ui.TransitionDirection
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteTypeId

/** Opens the Add Note/Note Editor screen. */
sealed class NoteEditorDestination : Destination() {
    /**
     * Opens the Note Editor to add a new note.
     * @property deckId Optional deck to pre-select for the new note.
     */
    data class AddNote(
        val deckId: DeckId? = null,
    ) : NoteEditorDestination()

    /** Opens the Note Editor through the splash screen. */
    data object AddNoteWithSplash : NoteEditorDestination()

    /**
     * Opens the Note Editor for image occlusion editing.
     * @property imageUri The image to occlude. May be null if not yet known.
     */
    // TODO: `imageUri` should be non-null. Handle in IntentHandler.handleImageImport.
    data class ImageOcclusion(
        val imageUri: Uri?,
    ) : NoteEditorDestination()

    /**
     * Continues editing in the standard Note Editor from the instant editor,
     * carrying the in-progress text.
     * @property sharedText The text to pre-populate.
     */
    data class AddInstantNote(
        val sharedText: String,
    ) : NoteEditorDestination()

    /**
     * Opens the Note Editor to edit the note belonging to a card shown in the previewer.
     * @property cardId The card whose note should be edited.
     */
    data class EditNoteFromPreviewer(
        val cardId: CardId,
    ) : NoteEditorDestination()

    /**
     * Opens the Note Editor to add a new note from the reviewer.
     * @property animation The animation direction to use when transitioning. Defaults
     * to no specific animation.
     */
    data class AddNoteFromReviewer(
        val animation: TransitionDirection? = null,
    ) : NoteEditorDestination()

    /**
     * Opens the Note Editor to add a new note from the card browser, pre-populated
     * with the search-query text and the most-recently-selected deck.
     * @property searchTerms The current search query in the card browser.
     * @property deckId The card browser's last deck, used as the deck for the new note.
     */
    data class AddNoteFromCardBrowser(
        val searchTerms: String,
        val deckId: DeckId?,
    ) : NoteEditorDestination()

    /**
     * Opens the Note Editor to edit the note(s) associated with the given cards.
     * @property cardIds Selected card id(s) — the first is the focused card when
     * editing in card mode, the full list is used in note-edit mode.
     * @property animation The animation direction for the transition.
     * @property inCardBrowserActivity True if opened in-place inside the Card Browser.
     */
    data class EditSelection(
        val cardIds: List<CardId>,
        val animation: TransitionDirection,
        val inCardBrowserActivity: Boolean = false,
    ) : NoteEditorDestination()

    /**
     * Opens the Note Editor pre-populated to copy an existing note into a new one.
     * @property deckId Target deck for the new note.
     * @property fieldsText Pre-filled field text (the source note's content).
     * @property tags Optional tags to apply to the copied note.
     */
    data class CopyNote(
        val deckId: DeckId,
        val fieldsText: String,
        val noteTypeId: NoteTypeId,
        val tags: List<String> = listOf(),
    ) : NoteEditorDestination()

    /**
     * Forwards an incoming intent's payload to the Note Editor.
     *
     * Relays `ACTION_SEND` (or similar) intent to the Note Editor, preserving the
     * originating intent's [action] alongside the [arguments] bundle.
     *
     * @property arguments Extras to forward to the Note Editor.
     * @property action Optional [Intent.setAction] value.
     */
    data class PassArguments(
        val arguments: Bundle,
        val action: String? = null,
    ) : NoteEditorDestination() {
        companion object {
            /**
             * Snapshots [intent]'s action alongside the [extras] for forwarding
             * to the Note Editor.
             */
            fun from(
                intent: Intent,
                // extras should come from the intent, have the caller perform the null-check
                extras: Bundle,
            ): PassArguments =
                PassArguments(
                    arguments = extras,
                    action = intent.action,
                )
        }
    }
}
