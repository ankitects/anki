/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.core.app.TaskStackBuilder
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.work.WorkManager
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.utils.trimToLength
import com.ichi2.anki.dialogs.DialogHandler.Companion.storeMessage
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.requireDeckPickerOrShowError
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.utils.MimeTypeUtils
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.utils.FileUtil
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils.handleFileImport
import com.ichi2.utils.ImportUtils.isInvalidViewIntent
import com.ichi2.utils.ImportUtils.showImportUnsuccessfulDialog
import com.ichi2.utils.IntentUtil.resolveMimeType
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.hasLegacyStorageAccessPermission
import com.ichi2.utils.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Class which handles how the application responds to different intents, forcing it to always be single task,
 * but allowing custom behavior depending on the intent
 * It inherits from [AbstractIntentHandler]
 *
 * @author Tim
 */
class IntentHandler : AbstractIntentHandler() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Note: This is our entry point from the launcher with intent: android.intent.action.MAIN
        super.onCreate(savedInstanceState)
        val intent = intent
        Timber.v(intent.toString())
        val reloadIntent = Intent(this, DeckPicker::class.java)
        reloadIntent.setDataAndType(getIntent().data, getIntent().type)
        val action = intent.action
        // #6157 - We want to block actions that need permissions we don't have, but not the default case
        // as this requires nothing
        val runIfStoragePermissions = { runnable: () -> Unit -> performActionIfStorageAccessible(reloadIntent, action) { runnable() } }
        val launchType = getLaunchType(intent)
        // TODO block the UI with some kind of ProgressDialog instead of cancelling the sync work
        if (requiresCollectionAccess(launchType)) {
            // # 18899
            if (WorkManager.isInitialized()) {
                SyncWorker.cancel(this)
            }
        }
        when (launchType) {
            LaunchType.FILE_IMPORT ->
                runIfStoragePermissions {
                    handleFileImport(intent, reloadIntent, action)
                    finish()
                }
            LaunchType.TEXT_IMPORT ->
                runIfStoragePermissions {
                    onSelectedCsvForImport(intent)
                    finish()
                }
            LaunchType.IMAGE_IMPORT ->
                runIfStoragePermissions {
                    handleImageImport(intent)
                    finish()
                }
            LaunchType.SHARED_TEXT ->
                runIfStoragePermissions {
                    handleSharedText(intent)
                    finish()
                }
            LaunchType.SYNC -> runIfStoragePermissions { handleSyncIntent(reloadIntent, action) }
            LaunchType.REVIEW -> runIfStoragePermissions { handleReviewIntent(reloadIntent, intent) }
            LaunchType.DEFAULT_START_APP_IF_NEW -> {
                Timber.d("onCreate() performing default action")
                launchDeckPickerIfNoOtherTasks(reloadIntent)
            }
            LaunchType.COPY_DEBUG_INFO -> {
                copyDebugInfoToClipboard(intent)
                finish()
            }
        }
    }

    private fun copyDebugInfoToClipboard(intent: Intent) {
        Timber.i("Copying debug info to clipboard")
        // null string is handled by copyToClipboard in try-catch
        this.copyToClipboard(
            text = (intent.getStringExtra(CLIPBOARD_INTENT_EXTRA_DATA)!!),
            failureMessageId = R.string.about_ankidroid_error_copy_debug_info,
        )
    }

    /**
     * Execute the runnable if one of the two following conditions are satisfied:
     *
     *  * AnkiDroid is using an app-private directory to store user data
     *  * AnkiDroid is using a legacy directory to store user data but has access to it since storage permission
     * has been granted (as long as AnkiDroid targeted API < 30, requested legacy storage, and has not been uninstalled since)
     *
     */
    @NeedsTest("clicking a file in 'Files' to import")
    private fun performActionIfStorageAccessible(
        reloadIntent: Intent,
        action: String?,
        block: () -> Unit,
    ) {
        if (grantedStoragePermissions(this, showToast = true)) {
            Timber.i("User has storage permissions. Running intent: %s", action)
            block()
        } else {
            Timber.i("No Storage Permission, cancelling intent '%s'", action)
            launchDeckPickerIfNoOtherTasks(reloadIntent)
        }
    }

    private fun handleReviewIntent(
        reloadIntent: Intent,
        reviewerIntent: Intent,
    ) {
        val deckId = intent.getLongExtra(REVIEW_DECK_INTENT_EXTRA_DECK_ID, 0)
        Timber.i("Handling intent to review deck '%d'", deckId)

        val reviewIntent =
            if (Prefs.isNewStudyScreenEnabled) {
                ReviewerFragment.getIntent(this)
            } else {
                Intent(this, Reviewer::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
        CollectionManager.getColUnsafe().decks.select(deckId)
        // Clean the stack out under the reviewer to avoid any incorrect activities / dialogs /
        // data state from prior app usage showing after reviewer exits if going to reviewer directly
        TaskStackBuilder
            .create(applicationContext)
            .addNextIntent(reloadIntent)
            .addNextIntent(reviewIntent)
            .startActivities()
        finish()
    }

    private fun handleSyncIntent(
        reloadIntent: Intent,
        action: String?,
    ) {
        Timber.i("Handling Sync Intent")
        sendDoSyncMsg()
        reloadIntent.action = action
        reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(reloadIntent)
        finish()
    }

    private fun handleFileImport(
        intent: Intent,
        reloadIntent: Intent,
        action: String?,
    ) {
        Timber.i("Handling file import")
        if (!hasShownAppIntro()) {
            Timber.i("Trying to import a file when the app was not started at all")
            showThemedToast(this, R.string.app_not_initialized_new, false)
            return
        }
        val importResult = handleFileImport(this, intent)
        // attempt to delete the downloaded deck if it is a shared deck download import
        if (intent.hasExtra(SharedDecksDownloadFragment.EXTRA_IS_SHARED_DOWNLOAD)) {
            deleteDownloadedDeck(intent.data)
        }

        // Start DeckPicker if we correctly processed ACTION_VIEW
        when (importResult) {
            is ImportResult.Success -> {
                deleteImportedDeck(intent.data?.path)
                reloadIntent.action = action
                reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(reloadIntent)
                finish()
            }
            is ImportResult.Failure -> {
                Timber.i("File import failed")
                // Don't import the file if it didn't load properly or doesn't have apkg extension
                showImportUnsuccessfulDialog(this, importResult, true)
            }
        }
    }

    private fun deleteImportedDeck(path: String?) {
        // TODO improve the handling of the imported temporary files
        // Launching this scope without tying it to a lifecycle since ,
        // IntentHandler finishes quickly, but deletion may still be in progress
        applicationScope.launch(Dispatchers.IO) {
            try {
                val file = File(path!!)
                val fileUri =
                    applicationContext?.let {
                        FileProvider.getUriForFile(
                            it,
                            it.applicationContext?.packageName + ".apkgfileprovider",
                            File(
                                it.getExternalFilesDir(FileUtil.getDownloadDirectory()),
                                file.name,
                            ),
                        )
                    }
                contentResolver.delete(fileUri!!, null, null)
                Timber.i("onCreate() import successful and downloaded file deleted")
            } catch (e: Exception) {
                Timber.w(e, "onCreate() import successful and cannot delete file")
            }
        }
    }

    private fun handleImageImport(data: Intent) {
        val imageUri =
            if (intent.action == Intent.ACTION_SEND) {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                data.data
            }

        val intentImageOcclusion = NoteEditorLauncher.ImageOcclusion(imageUri).toIntent(this)

        TaskStackBuilder
            .create(this)
            .addNextIntentWithParentStack(Intent(this, DeckPicker::class.java))
            .addNextIntent(intentImageOcclusion)
            .startActivities()
    }

    private fun handleSharedText(data: Intent) {
        Timber.i("Handling shared text content for note creation")
        val noteEditorIntent =
            if (data.extras != null) {
                NoteEditorLauncher.PassArguments(data.extras!!).toIntent(this, data.action)
            } else {
                // Fallback if no extras, though this shouldn't happen for ACTION_SEND
                NoteEditorLauncher.AddNote().toIntent(this)
            }
        noteEditorIntent.setDataAndType(data.data, data.type)
        startActivity(noteEditorIntent)
    }

    private fun deleteDownloadedDeck(sharedDeckUri: Uri?) {
        if (sharedDeckUri == null) {
            Timber.i("onCreate: downloaded a shared deck but uri was null when trying to delete its file")
            return
        }
        // TODO improve the handling of the imported temporary files
        // Launching this scope without tying it to a lifecycle since ,
        // IntentHandler finishes quickly, but deletion may still be in progress
        applicationScope.launch(Dispatchers.IO) {
            try {
                contentResolver.delete(sharedDeckUri, null, null)
                Timber.i("onCreate: downloaded shared deck deleted")
            } catch (e: Exception) {
                Timber.w(e, "onCreate: failed to delete downloaded shared deck")
            }
        }
    }

    private fun launchDeckPickerIfNoOtherTasks(reloadIntent: Intent) {
        // Launcher intents should start DeckPicker if no other task exists,
        // otherwise go to previous task
        Timber.i("Launching DeckPicker")
        reloadIntent.action = Intent.ACTION_MAIN
        reloadIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        reloadIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivityIfNeeded(reloadIntent, 0)
        finish()
    }

    // COULD_BE_BETTER: Also extract the parameters into here to reduce coupling
    @VisibleForTesting
    enum class LaunchType {
        DEFAULT_START_APP_IF_NEW,

        /** colpkg/apkg/unknown */
        FILE_IMPORT,

        /** csv/tsv */
        TEXT_IMPORT,

        /** image */
        IMAGE_IMPORT,

        /** shared text content */
        SHARED_TEXT,

        SYNC,
        REVIEW,
        COPY_DEBUG_INFO,
    }

    companion object {
        const val REVIEW_DECK_INTENT_EXTRA_DECK_ID = "EXTRA_DECK_ID"
        private const val CLIPBOARD_INTENT = "com.ichi2.anki.COPY_DEBUG_INFO"
        private const val CLIPBOARD_INTENT_EXTRA_DATA = "clip_data"

        private val textMimeTypes = MimeTypeUtils.CSV_TSV_MIME_TYPES

        private fun isValidViewIntent(intent: Intent): Boolean {
            // Negating a negative because we want to call specific attention to the fact that it's invalid
            // #6312 - Smart Launcher provided an empty ACTION_VIEW, no point in importing here.
            return !isInvalidViewIntent(intent)
        }

        /** Checks whether storage permissions are granted on the device. If the device is not using legacy storage,
         *  it verifies if the app has been granted the necessary storage access permission.
         *  @return `true`: if granted, otherwise `false` and shows a missing permission toast
         *
         * @throws SystemStorageException if `getExternalFilesDir` returns null
         */
        fun grantedStoragePermissions(
            context: Context,
            showToast: Boolean,
        ): Boolean {
            val granted =
                !ScopedStorageService.isLegacyStorage(context) ||
                    hasLegacyStorageAccessPermission(context) ||
                    Permissions.isExternalStorageManagerCompat()

            if (!granted && showToast) {
                showThemedToast(context, context.getString(R.string.intent_handler_failed_no_storage_permission), false)
            }

            return granted
        }

        @VisibleForTesting
        @CheckResult
        fun getLaunchType(intent: Intent): LaunchType {
            val action = intent.action
            return if (action == Intent.ACTION_SEND || (Intent.ACTION_VIEW == action && isValidViewIntent(intent))) {
                val mimeType = intent.resolveMimeType()
                when {
                    mimeType?.startsWith("image/") == true -> LaunchType.IMAGE_IMPORT
                    action == Intent.ACTION_SEND &&
                        intent.hasExtra(Intent.EXTRA_TEXT) &&
                        !intent.hasExtra(Intent.EXTRA_STREAM) -> LaunchType.SHARED_TEXT
                    textMimeTypes.contains(mimeType) -> LaunchType.TEXT_IMPORT
                    else -> LaunchType.FILE_IMPORT
                }
            } else if ("com.ichi2.anki.DO_SYNC" == action) {
                LaunchType.SYNC
            } else if (intent.hasExtra(REVIEW_DECK_INTENT_EXTRA_DECK_ID)) {
                LaunchType.REVIEW
            } else if (action == CLIPBOARD_INTENT) {
                LaunchType.COPY_DEBUG_INFO
            } else {
                LaunchType.DEFAULT_START_APP_IF_NEW
            }
        }

        /**
         * Send a Message to AnkiDroidApp so that the DialogMessageHandler forces a sync
         */
        fun sendDoSyncMsg() {
            // Store the message in AnkiDroidApp message holder, which is loaded later in AnkiActivity.onResume
            storeMessage(DoSync().toMessage())
        }

        fun copyStringToClipboardIntent(
            context: Context,
            textToCopy: String,
        ) = Intent(context, IntentHandler::class.java).also {
            it.action = CLIPBOARD_INTENT
            // max length for an intent is 500KB.
            // 25000 * 2 (bytes per char) = 50,000 bytes <<< 500KB
            it.putExtra(CLIPBOARD_INTENT_EXTRA_DATA, textToCopy.trimToLength(25000))
        }

        fun requiresCollectionAccess(launchType: LaunchType): Boolean =
            when (launchType) {
                LaunchType.SYNC,
                LaunchType.REVIEW,
                LaunchType.DEFAULT_START_APP_IF_NEW,
                LaunchType.FILE_IMPORT,
                LaunchType.TEXT_IMPORT,
                LaunchType.IMAGE_IMPORT,
                LaunchType.SHARED_TEXT,
                -> true
                LaunchType.COPY_DEBUG_INFO -> false
            }

        class DoSync :
            DialogHandlerMessage(
                which = WhichDialogHandler.MSG_DO_SYNC,
                analyticName = "DoSyncDialog",
            ) {
            override fun handleAsyncMessage(activity: AnkiActivity) {
                // we may be called via any AnkiActivity but sync is a DeckPicker thing
                val deckPicker = activity.requireDeckPickerOrShowError() ?: return
                val res = deckPicker.resources
                val hkey = Prefs.hkey
                val millisecondsSinceLastSync = millisecondsSinceLastSync()
                val limited = millisecondsSinceLastSync < INTENT_SYNC_MIN_INTERVAL
                if (!limited && !hkey.isNullOrEmpty() && NetworkUtils.isOnline) {
                    deckPicker.sync()
                } else {
                    val err = res.getString(R.string.sync_error)
                    if (limited) {
                        val remainingTimeInSeconds =
                            max((INTENT_SYNC_MIN_INTERVAL - millisecondsSinceLastSync) / 1000, 1)
                        // getQuantityString needs an int
                        val remaining = min(Int.MAX_VALUE.toLong(), remainingTimeInSeconds).toInt()
                        val message =
                            res.getQuantityString(
                                R.plurals.sync_automatic_sync_needs_more_time,
                                remaining,
                                remaining,
                            )
                        deckPicker.showSimpleNotification(err, message, Channel.SYNC)
                    } else {
                        deckPicker.showSimpleNotification(
                            err,
                            res.getString(R.string.youre_offline),
                            Channel.SYNC,
                        )
                    }
                }
                deckPicker.finish()
            }

            override fun toMessage(): Message = emptyMessage(this.what)

            companion object {
                const val INTENT_SYNC_MIN_INTERVAL =
                    (
                        2 * 60000 // 2min minimum sync interval
                    ).toLong()
            }
        }

        /**
         * Returns an intent to review a specific deck.
         *
         * @param context
         * @param deckId the deck ID of the deck to review
         */
        fun getReviewDeckIntent(
            context: Context,
            deckId: DeckId,
        ): Intent = Intent(context, IntentHandler::class.java).putExtra(REVIEW_DECK_INTENT_EXTRA_DECK_ID, deckId)

        /**
         * Returns an intent to review a specific deck.
         * This does not states which reviewer to use, instead IntentHandler will choose whether to use the
         * legacy or the new reviewer based on the "newReviewer" preference.
         * It is expected to be used from widget, shortcut, reminders but not from ankidroid directly because of the CLEAR_TOP flag.
         */
        fun intentToReviewDeckFromShortcuts(
            context: Context,
            deckId: DeckId,
        ) = Intent(context, IntentHandler::class.java).apply {
            setAction(Intent.ACTION_VIEW)
            putExtra(REVIEW_DECK_INTENT_EXTRA_DECK_ID, deckId)
        }
    }
}
