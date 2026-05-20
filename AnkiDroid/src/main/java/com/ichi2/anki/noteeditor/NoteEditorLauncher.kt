/*
 *  Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.noteeditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.bundleOf
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.browser.CardBrowserViewModel
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
            bundleOf(
                NoteEditorFragment.EXTRA_CALLER to NoteEditorCaller.IMG_OCCLUSION.value,
                NoteEditorFragment.EXTRA_IMG_OCCLUSION to imageUri,
            )
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
            bundleOf(
                NoteEditorFragment.EXTRA_CALLER to NoteEditorCaller.DECKPICKER.value,
            ).also { bundle ->
                deckId?.let { deckId -> bundle.putLong(NoteEditorFragment.EXTRA_DID, deckId) }
            }
    }

    /**
     * Represents adding a note to the NoteEditor from the card browser.
     * @property viewModel The view model containing data from the card browser.
     */
    data class AddNoteFromCardBrowser(
        val viewModel: CardBrowserViewModel,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.CARDBROWSER_ADD.value)
                putString(NoteEditorFragment.EXTRA_TEXT_FROM_SEARCH_VIEW, viewModel.searchTerms)
                putBoolean(NoteEditorFragment.IN_CARD_BROWSER_ACTIVITY, false)
                if (viewModel.lastDeckId?.let { id -> id > 0 } == true) {
                    putLong(NoteEditorFragment.EXTRA_DID, viewModel.lastDeckId!!)
                }
            }
    }

    /**
     * Represents adding a note to the NoteEditor from the reviewer.
     * @property animation The animation direction to use when transitioning.
     */
    data class AddNoteFromReviewer(
        val animation: ActivityTransitionAnimation.Direction? = null,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putInt(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.REVIEWER_ADD.value)
                animation?.let { putParcelable(AnkiActivity.FINISH_ANIMATION_EXTRA, it as Parcelable) }
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
            bundleOf(
                NoteEditorFragment.EXTRA_CALLER to NoteEditorCaller.INSTANT_NOTE_EDITOR.value,
                Intent.EXTRA_TEXT to sharedText,
            )
    }

    /**
     * Opens the NoteEditor for the current selection (card or note).
     * @property cardIds The selected card ID when editing a card, or the IDs of cards of the same note when editing a note.
     * @property animation The animation direction.
     * @property inCardBrowserActivity True if opened within Card Browser Activity.
     */
    data class EditSelection(
        val cardIds: List<CardId>,
        val animation: ActivityTransitionAnimation.Direction,
        val inCardBrowserActivity: Boolean = false,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            bundleOf(
                NoteEditorFragment.EXTRA_CALLER to NoteEditorCaller.EDIT.value,
                // To handle single card selection
                NoteEditorFragment.EXTRA_CARD_ID to cardIds.first(),
                // To handle multi select and note edit
                NoteEditorFragment.EXTRA_CARD_IDS to cardIds.toLongArray(),
                AnkiActivity.FINISH_ANIMATION_EXTRA to animation as Parcelable,
                NoteEditorFragment.IN_CARD_BROWSER_ACTIVITY to inCardBrowserActivity,
            )
    }

    /**
     * Represents editing a note in the NoteEditor from the previewer.
     * @property cardId The ID of the card associated with the note to edit.
     */
    data class EditNoteFromPreviewer(
        val cardId: CardId,
    ) : NoteEditorLauncher {
        override fun toBundle(): Bundle =
            bundleOf(
                NoteEditorFragment.EXTRA_CALLER to NoteEditorCaller.PREVIEWER_EDIT.value,
                NoteEditorFragment.EXTRA_EDIT_FROM_CARD_ID to cardId,
            )
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
            bundleOf(
                NoteEditorFragment.EXTRA_CALLER to NoteEditorCaller.NOTEEDITOR.value,
                NoteEditorFragment.EXTRA_DID to deckId,
                NoteEditorFragment.EXTRA_CONTENTS to fieldsText,
            ).also { bundle ->
                tags?.let { tags -> bundle.putStringArray(NoteEditorFragment.EXTRA_TAGS, tags.toTypedArray()) }
            }
    }
}
