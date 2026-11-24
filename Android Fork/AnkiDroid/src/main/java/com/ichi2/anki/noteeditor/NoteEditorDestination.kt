// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki.noteeditor

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.IntentHandler2
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.common.destinations.NoteEditorDestination

/** Resolves a [NoteEditorDestination] to its launch [Intent] for [NoteEditorActivity]. */
fun NoteEditorDestination.toIntent(context: Context): Intent =
    when (this) {
        is NoteEditorDestination.AddNote ->
            Intent(context, NoteEditorActivity::class.java).apply {
                putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.DECKPICKER.value)
                deckId?.let { putExtra(NoteEditorFragment.EXTRA_DID, it) }
            }
        NoteEditorDestination.AddNoteWithSplash ->
            Intent(context, IntentHandler2::class.java).putExtras(NoteEditorFragment.addNoteArgs())
        is NoteEditorDestination.ImageOcclusion ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.IMG_OCCLUSION.value)
                intent.putExtra(NoteEditorFragment.EXTRA_IMG_OCCLUSION, imageUri)
            }
        is NoteEditorDestination.AddInstantNote ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.INSTANT_NOTE_EDITOR.value)
                intent.putExtra(Intent.EXTRA_TEXT, sharedText)
            }
        is NoteEditorDestination.EditNoteFromPreviewer ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.PREVIEWER_EDIT.value)
                intent.putExtra(NoteEditorFragment.EXTRA_EDIT_FROM_CARD_ID, cardId)
            }
        is NoteEditorDestination.AddNoteFromReviewer ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.REVIEWER_ADD.value)
                animation?.let { intent.putExtra(AnkiActivity.EXTRA_FINISH_ANIMATION, it as Parcelable) }
            }
        is NoteEditorDestination.AddNoteFromCardBrowser ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.CARDBROWSER_ADD.value)
                intent.putExtra(NoteEditorFragment.EXTRA_TEXT_FROM_SEARCH_VIEW, searchTerms)
                intent.putExtra(NoteEditorFragment.EXTRA_IN_CARD_BROWSER_ACTIVITY, false)
                deckId?.takeIf { it > 0 }?.let { intent.putExtra(NoteEditorFragment.EXTRA_DID, it) }
            }
        is NoteEditorDestination.EditSelection ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.EDIT.value)
                // To handle single card selection
                intent.putExtra(NoteEditorFragment.EXTRA_CARD_ID, cardIds.first())
                // To handle multi select and note edit
                intent.putExtra(NoteEditorFragment.EXTRA_CARD_IDS, cardIds.toLongArray())
                intent.putExtra(AnkiActivity.EXTRA_FINISH_ANIMATION, animation as Parcelable)
                intent.putExtra(NoteEditorFragment.EXTRA_IN_CARD_BROWSER_ACTIVITY, inCardBrowserActivity)
            }
        is NoteEditorDestination.CopyNote ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.NOTEEDITOR.value)
                intent.putExtra(NoteEditorFragment.EXTRA_DID, deckId)
                intent.putExtra(NoteEditorFragment.EXTRA_CONTENTS, fieldsText)
                intent.putExtra(NoteEditorFragment.EXTRA_TAGS, tags.toTypedArray())
                intent.putExtra(NoteEditorFragment.EXTRA_NOTE_TYPE_ID, noteTypeId)
            }
        is NoteEditorDestination.PassArguments ->
            Intent(context, NoteEditorActivity::class.java).also { intent ->
                intent.putExtras(arguments)
                intent.action = action
            }
    }
