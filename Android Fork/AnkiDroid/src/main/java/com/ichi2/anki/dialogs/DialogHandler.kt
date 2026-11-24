// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Handler
import android.os.Message
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionLoadingErrorDialog
import com.ichi2.anki.CommonString
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.utils.android.HandlerUtils.getDefaultLooper
import com.ichi2.anki.dialogs.DialogHandler.Companion.storeMessage
import com.ichi2.anki.dialogs.DialogHandlerMessage.WhichDialogHandler
import com.ichi2.anki.showError
import com.ichi2.utils.ImportUtils
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * We're not allowed to commit fragment transactions from Loader.onLoadCompleted(),
 * and it's unsafe to commit them from an AsyncTask onComplete event, so we work
 * around this by using a message handler.
 */
class DialogHandler(
    activity: AnkiActivity,
) : Handler(getDefaultLooper()) {
    // Use weak reference to main activity to prevent leaking the activity when it's closed
    private val activity: WeakReference<AnkiActivity> = WeakReference(activity)

    override fun handleMessage(message: Message) {
        val msg = DialogHandlerMessage.fromMessage(message)
        Analytics.sendAnalyticsScreenView(msg.analyticName)
        Timber.i("Handling Message: %s", msg.analyticName)
        msg.handleAsyncMessage(activity.get() as AnkiActivity)
    }

    /**
     * Returns the current message (if any) and stops further processing of it
     */
    fun popMessage(): Message? {
        val toReturn = sStoredMessage
        sStoredMessage = null
        return toReturn
    }

    /**
     * Read and handle Message which was stored via [storeMessage]
     */
    fun executeMessage() {
        val message = sStoredMessage ?: return
        when (WhichDialogHandler.fromInt(message.what)) {
            WhichDialogHandler.MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG,
            WhichDialogHandler.MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG,
            -> {
                // Only DeckPicker collects the ImportViewModel flows after confirmation.
                if (activity.get() !is DeckPicker) return
            }
            else -> Unit
        }
        Timber.d("Reading persistent message")
        sendStoredMessage(message)
        sStoredMessage = null
    }

    fun sendStoredMessage(message: Message) {
        Timber.i("Dispatching persistent message: %d", message.what)
        sendMessage(message)
    }

    companion object {
        private var sStoredMessage: Message? = null

        /**
         * Store a persistent message to static variable
         * @param message Message to store
         */
        fun storeMessage(message: Message?) {
            Timber.d("Storing persistent message")
            sStoredMessage = message
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun discardMessage() {
            sStoredMessage = null
        }
    }
}

/**
 * A message which can be passed to [DialogHandler] for the [DeckPicker] to handle asynchronously
 * once the app is restored.
 *
 * Restoration + handling is performed in [AnkiActivity.onResume].
 * It is assumed that the [DeckPicker] will be the inheritor of AnkiActivity at this time.
 * As this is provided as the intent from [AnkiActivity.showExportReadyNotification]
 */
abstract class DialogHandlerMessage protected constructor(
    val which: WhichDialogHandler,
    val analyticName: String,
) {
    val what = which.what

    abstract fun handleAsyncMessage(activity: AnkiActivity)

    protected fun emptyMessage(what: Int): Message = Message.obtain().apply { this.what = what }

    // TODO: See if toMessage + fromMessage can be made parcelable
    abstract fun toMessage(): Message

    companion object {
        fun fromMessage(message: Message): DialogHandlerMessage =
            when (WhichDialogHandler.fromInt(message.what)) {
                WhichDialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG -> CollectionLoadingErrorDialog()
                WhichDialogHandler.MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG -> ImportUtils.CollectionImportReplace.fromMessage(message)
                WhichDialogHandler.MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG -> ImportUtils.CollectionImportAdd.fromMessage(message)
                WhichDialogHandler.MSG_SHOW_SYNC_ERROR_DIALOG -> SyncErrorDialog.SyncErrorDialogMessageHandler.fromMessage(message)
                WhichDialogHandler.MSG_SHOW_DATABASE_ERROR_DIALOG -> DatabaseErrorDialog.ShowDatabaseErrorDialog.fromMessage(message)
                WhichDialogHandler.MSG_DO_SYNC -> IntentHandler.Companion.DoSync()
            }
    }

    /** A list of unique values to be used in [DialogHandler]
     * @param what Ensures that a [Message] is provided with a unique value */
    enum class WhichDialogHandler(
        val what: Int,
    ) {
        MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG(0),
        MSG_SHOW_COLLECTION_IMPORT_REPLACE_DIALOG(1),
        MSG_SHOW_COLLECTION_IMPORT_ADD_DIALOG(2),
        MSG_SHOW_SYNC_ERROR_DIALOG(3),
        MSG_SHOW_DATABASE_ERROR_DIALOG(6),
        MSG_DO_SYNC(8),
        ;

        companion object {
            fun fromInt(value: Int) = entries.first { it.what == value }
        }
    }
}

/**
 * If the receiver is a [DeckPicker], return it.
 * Otherwise, show an error and return `null`
 */
fun AnkiActivity.requireDeckPickerOrShowError(): DeckPicker? {
    if (this is DeckPicker) return this

    showError(
        message = getString(CommonString.something_wrong),
        crashReportData =
            ClassCastException(
                this.javaClass.simpleName + " is not " + DeckPicker::class.java.simpleName,
            ).toCrashReportData(this),
    )
    return null
}
