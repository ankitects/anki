/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.sync

import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * [launchCatchingTask], showing a one-way sync dialog: [R.string.full_sync_confirmation]
 *
 * @param block calls a backend method which unconditionally performs a schema change,
 *  such as [Collection.changeNotetypeRaw]
 */
fun AnkiActivity.launchCatchingRequiringOneWaySync(block: suspend () -> Unit) =
    launchCatchingTask {
        if (withCol { !schemaChanged() }) {
            // .also is used to ensure the activity is used as context
            val confirmModSchemaDialog =
                ConfirmationDialog().also { dialog ->
                    dialog.setArgs(message = getString(R.string.full_sync_confirmation))
                    dialog.setConfirm {
                        launchCatchingTask {
                            block()
                        }
                    }
                }
            showDialogFragment(confirmModSchemaDialog)
            return@launchCatchingTask
        }
        // TODO: use context(SchemaChangedConfirmed) after bug #20247 is fixed (upstream-issue)
        block()
    }

/**
 * [launchCatchingTask], showing a one-way sync dialog: [R.string.full_sync_confirmation]
 *
 * **This method discards the undo and study queues when consent is provided**
 */
fun AnkiActivity.launchCatchingRequiringOneWaySyncDiscardUndo(block: suspend () -> Unit) =
    launchCatchingTask {
        try {
            block()
        } catch (e: ConfirmModSchemaException) {
            e.log()

            // .also is used to ensure the activity is used as context
            val confirmModSchemaDialog =
                ConfirmationDialog().also { dialog ->
                    dialog.setArgs(message = getString(R.string.full_sync_confirmation))
                    dialog.setConfirm {
                        launchCatchingTask {
                            withCol { modSchema(check = false) }
                            block()
                        }
                    }
                }
            showDialogFragment(confirmModSchemaDialog)
        }
    }

/**
 * Returns whether we are allowed to change the schema.
 *
 * If changing the schema would require the next sync to be a full sync, and it's not already required, ask
 * the user whether or not they still allow the schema change.
 */
suspend fun AnkiActivity.userAcceptsSchemaChange(): Boolean {
    if (withCol { schemaChanged() }) {
        return true
    }
    val hasAcceptedSchemaChange =
        suspendCoroutine { coroutine ->
            AlertDialog.Builder(this).show {
                message(text = TR.deckConfigWillRequireFullSync().replace("\\s+".toRegex(), " "))
                positiveButton(R.string.dialog_ok) { coroutine.resume(true) }
                negativeButton(R.string.dialog_cancel) { coroutine.resume(false) }
                setOnCancelListener { coroutine.resume(false) }
            }
        }
    if (hasAcceptedSchemaChange) {
        withCol { modSchema(check = false) }
    }
    return hasAcceptedSchemaChange
}
