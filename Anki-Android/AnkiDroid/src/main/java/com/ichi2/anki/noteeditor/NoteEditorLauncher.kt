// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki.noteeditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.common.ui.TransitionDirection
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.Destination

/**
 * Defines various configurations for opening the NoteEditor fragment with specific data or actions.
 */
sealed interface NoteEditorLauncher : Destination {
    override fun toIntent(context: Context): Intent = toIntent(context, action = null)

    /**
     * Generates an intent to open the NoteEditor activity with the configured parameters
     *
     * @param context The context from which the intent is launched.
     * @param action Optional action string for the intent.
     * @return Intent configured to launch the NoteEditor  activity.
     */
    fun toIntent(
        context: Context,
        action: String? = null,
    ) = Intent(context, NoteEditorActivity::class.java).apply {
        putExtras(toBundle())
        action?.let { this.action = it }
    }

    /**
     * Converts the configuration into a Bundle to pass arguments to the NoteEditor fragment.
     *
     * @return Bundle containing arguments specific to this configuration.
     */
    fun toBundle(): Bundle

    /**
     * Represents opening the NoteEditor with an image occlusion.
     * @property imageUri The URI of the image to occlude.
     */
    data class ImageOcclusion(
        val imageUri: Uri?,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.IMG_OCCLUSION.value)
                putParcelable(NoteEditorFragment.EXTRA_IMG_OCCLUSION, imageUri)
            }
    }

    /**
     * Represents opening the NoteEditor with custom arguments.
     * @property arguments The bundle of arguments to pass.
     */
    data class PassArguments(
        val arguments: Bundle,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle = arguments
    }

    /**
     * Represents adding a note to the NoteEditor within a specific deck (Optional).
     * @property deckId The ID of the deck where the note should be added.
     */
    data class AddNote(
        val deckId: DeckId? = null,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.DECKPICKER.value)
                deckId?.let { deckId -> putLong(NoteEditorFragment.EXTRA_DID, deckId) }
            }
    }

    /**
     * Represents adding a note to the NoteEditor from the card browser.
     * @property searchTerms The current search terms from the card browser.
     * @property deckId The card browser's last deck, used as the deck for the new note.
     */
    data class AddNoteFromCardBrowser(
        val searchTerms: String,
        val deckId: DeckId?,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.CARDBROWSER_ADD.value)
                putString(NoteEditorFragment.EXTRA_TEXT_FROM_SEARCH_VIEW, searchTerms)
                putBoolean(NoteEditorFragment.IN_CARD_BROWSER_ACTIVITY, false)
                if (deckId != null && deckId > 0) {
                    putLong(NoteEditorFragment.EXTRA_DID, deckId)
                }
            }
    }

    /**
     * Represents adding a note to the NoteEditor from the reviewer.
     * @property animation The animation direction to use when transitioning.
     */
    data class AddNoteFromReviewer(
        val animation: TransitionDirection? = null,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.REVIEWER_ADD.value)
                animation?.let { putParcelable(AnkiActivity.EXTRA_FINISH_ANIMATION, it as Parcelable) }
            }
    }

    /**
     * Allows to move from Instant note editor to standard note editor while keeping the text content
     *
     * @property sharedText The shared text content for the instant note.
     */
    data class AddInstantNote(
        val sharedText: String,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.INSTANT_NOTE_EDITOR.value)
                putString(Intent.EXTRA_TEXT, sharedText)
            }
    }

    /**
     * Opens the NoteEditor for the current selection (card or note).
     * @property cardIds The selected card ID when editing a card, or the IDs of cards of the same note when editing a note.
     * @property animation The animation direction.
     * @property inCardBrowserActivity True if opened within Card Browser Activity.
     */
    data class EditSelection(
        val cardIds: List<CardId>,
        val animation: TransitionDirection,
        val inCardBrowserActivity: Boolean = false,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
                // To handle single card selection
                putLong(NoteEditorFragment.EXTRA_CARD_ID, cardIds.first())
                // To handle multi select and note edit
                putLongArray(NoteEditorFragment.EXTRA_CARD_IDS, cardIds.toLongArray())
                putParcelable(AnkiActivity.EXTRA_FINISH_ANIMATION, animation as Parcelable)
                putBoolean(NoteEditorFragment.IN_CARD_BROWSER_ACTIVITY, inCardBrowserActivity)
            }
    }

    /**
     * Represents editing a note in the NoteEditor from the previewer.
     * @property cardId The ID of the card associated with the note to edit.
     */
    data class EditNoteFromPreviewer(
        val cardId: CardId,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.PREVIEWER_EDIT.value)
                putLong(NoteEditorFragment.EXTRA_EDIT_FROM_CARD_ID, cardId)
            }
    }

    /**
     * Represents copying a note to the NoteEditor.
     * @property deckId The ID of the deck where the note should be copied.
     * @property fieldsText The text content of the fields to copy.
     * @property tags Optional list of tags to assign to the copied note.
     */
    data class CopyNote(
        val deckId: DeckId,
        val fieldsText: String,
        val tags: List<String>? = null,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR.value)
                putLong(NoteEditorFragment.EXTRA_DID, deckId)
                putString(NoteEditorFragment.EXTRA_CONTENTS, fieldsText)
                tags?.let { tags -> putStringArray(NoteEditorFragment.EXTRA_TAGS, tags.toTypedArray()) }
            }
    }
}
