// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki

import android.os.Bundle
import com.ichi2.anki.NoteEditorFragment.Companion.NoteEditorCaller
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.utils.android.showThemedToast
import timber.log.Timber

/**
 * This activity serves as an intermediate handler to process various types of intents and forward them to the NoteEditor fragment hosted within the SingleFragmentActivity.
 *
 * The main reason for using this IntentHandler2 is to avoid conflicts in the manifest file. We can't have multiple
 * ACTION_SEND intents in same activity (IntentHandler) that only differ by their labels. By using this handler, we can manage these intents
 * and make sure they are sent to the NoteEditor correctly.
 */
class IntentHandler2 : AbstractIntentHandler() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        Timber.v(intent.toString())
        if (NoteEditorFragment.intentLaunchedWithImage(intent)) {
            Timber.i("Intent contained an image")
            intent.putExtra(NoteEditorFragment.EXTRA_CALLER, NoteEditorCaller.ADD_IMAGE.value)
        }
        val intentExtras = intent.extras
        if (intentExtras == null) {
            Timber.w("Intent unexpectedly has no extras. Notifying user, defaulting to add note.")
            showThemedToast(this, getString(CommonString.something_wrong), false)
            navigate(NoteEditorDestination.AddNote())
            finish()
        } else {
            Timber.i("Opening Note Editor from intent")
            navigate(NoteEditorDestination.PassArguments.from(intent, intentExtras))
            finish()
        }
    }
}
