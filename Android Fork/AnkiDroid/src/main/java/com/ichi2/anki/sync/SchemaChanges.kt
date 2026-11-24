// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
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
 * [launchCatchingTask], showing a one-way sync dialog: [CommonString.full_sync_confirmation]
 *
 * @param block calls a backend method which unconditionally performs a schema change,
 *  such as [Collection.changeNotetypeRaw]
 */
fun FragmentActivity.launchCatchingRequiringOneWaySync(block: suspend () -> Unit) =
    launchCatchingTask {
        if (withCol { !schemaChanged() }) {
            // .also is used to ensure the activity is used as context
            val confirmModSchemaDialog =
                ConfirmationDialog().also { dialog ->
                    dialog.setArgs(message = getString(CommonString.full_sync_confirmation))
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
 * [launchCatchingTask], showing a one-way sync dialog: [CommonString.full_sync_confirmation]
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
                    dialog.setArgs(message = getString(CommonString.full_sync_confirmation))
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
                positiveButton(CommonString.dialog_ok) { coroutine.resume(true) }
                negativeButton(CommonString.dialog_cancel) { coroutine.resume(false) }
                setOnCancelListener { coroutine.resume(false) }
            }
        }
    if (hasAcceptedSchemaChange) {
        withCol { modSchema(check = false) }
    }
    return hasAcceptedSchemaChange
}
