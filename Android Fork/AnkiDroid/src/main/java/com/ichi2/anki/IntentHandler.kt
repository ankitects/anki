// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

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
import androidx.work.WorkManager
import com.ichi2.anki.common.analytics.reportPotentiallyDeadCode
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.addNextIntent
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.anki.common.storage.grantedStoragePermissions
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.getParcelableExtraCompat
import com.ichi2.anki.dialogs.DialogHandler.Companion.storeMessage
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.requireDeckPickerOrShowError
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.shareddeck.SharedDecksDownloadFragment
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.utils.FileUtil
import com.ichi2.utils.ImportResolution
import com.ichi2.utils.ImportResult
import com.ichi2.utils.ImportUtils.isInvalidViewIntent
import com.ichi2.utils.ImportUtils.prepareImport
import com.ichi2.utils.ImportUtils.showImportUnsuccessfulDialog
import com.ichi2.utils.IntentUtil.resolveMimeType
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.TruncatedString
import com.ichi2.utils.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // A delayed work query must not reopen DeckPicker after this activity is dismissed.
    private val syncIntentScope = MainScope()
    private val importScope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Note: This is our entry point from the launcher with intent: android.intent.action.MAIN
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
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
        // A sync intent must not interrupt an existing sync; handleSyncIntent ignores duplicates.
        if (launchType != LaunchType.SYNC && requiresCollectionAccess(launchType)) {
            if (WorkManager.isInitialized()) {
                SyncWorker.cancel(this)
            } else {
                // #18899: suspected dead code now that backup recovery runs first.
                reportPotentiallyDeadCode("IntentHandler.WorkManagerNotInitialized")
            }
        }
        when (launchType) {
            LaunchType.FILE_IMPORT ->
                runIfStoragePermissions {
                    handleFileImport(intent, reloadIntent, action)
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
            LaunchType.OPEN_BROWSER -> runIfStoragePermissions { handleBrowserIntent(intent) }
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

    override fun onDestroy() {
        importScope.cancel()
        syncIntentScope.cancel()
        super.onDestroy()
    }

    private fun copyDebugInfoToClipboard(intent: Intent) {
        Timber.i("Copying debug info to clipboard")
        val clipboardData =
            intent.getStringExtra(EXTRA_CLIPBOARD_DATA) ?: run {
                Timber.w("copyDebugInfoToClipboard: missing '%s' extra", EXTRA_CLIPBOARD_DATA)
                return
            }
        this.copyToClipboard(
            text = TruncatedString.from(clipboardData),
            failureMessageId = CommonString.about_ankidroid_error_copy_debug_info,
        )
    }

    /**
     * Execute the runnable if one of the two following conditions are satisfied:
     *
     *  * AnkiDroid is using an app-private directory to store user data
     *  * AnkiDroid is using a legacy directory to store user data but has access to it since storage permission
     * has been granted (as long as AnkiDroid targeted API < 30, requested legacy storage, and has not been uninstalled since)
     *
     * The user must also have [decided][CollectionHelper.storageDecision] where data is stored;
     * otherwise they are routed to the [DeckPicker].
     */
    @NeedsTest("clicking a file in 'Files' to import")
    private fun performActionIfStorageAccessible(
        reloadIntent: Intent,
        action: String?,
        block: () -> Unit,
    ) {
        if (CollectionHelper.storageDecision(sharedPrefs()) != StorageDecision.Decided) {
            // checked before permissions: the permissions required depend on the chosen folder
            Timber.i("Storage is not configured, cancelling intent '%s'", action)
            launchDeckPickerIfNoOtherTasks(reloadIntent)
            return
        }
        if (grantedStoragePermissions(this, showToast = true)) {
            Timber.i("User has storage permissions. Running intent: %s", action)
            block()
        } else {
            Timber.i("No Storage Permission, cancelling intent '%s'", action)
            launchDeckPickerIfNoOtherTasks(reloadIntent)
        }
    }

    /**
     * Opens [CardBrowser] standalone in response to `anki://x-callback-url/browser`.
     */
    private fun handleBrowserIntent(intent: Intent) {
        Timber.i("Handling intent to open the Card Browser")
        val search = intent.data?.getQueryParameter("search")
        val destination =
            if (search != null) {
                BrowserDestination.Search(query = search, allDecks = false)
            } else {
                BrowserDestination.Open
            }
        // 'back' should close this activity.
        navigate(destination)
        finish()
    }

    private fun handleReviewIntent(
        reloadIntent: Intent,
        reviewerIntent: Intent,
    ) {
        val deckId = intent.getLongExtra(EXTRA_DECK_ID, 0)
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
        syncIntentScope.launch {
            val existingSync = WorkManager.getInstance(this@IntentHandler).getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC).first()
            // finish() may be called before onDestroy() cancels the query.
            if (isFinishing) return@launch
            val foregroundSync = CollectionManager.collectionLease?.operation?.isSync == true
            if (foregroundSync || existingSync.any { !it.state.isFinished }) {
                // Opening DeckPicker would also access the collection while the existing sync is using it.
                Timber.w("Ignoring sync intent: collection sync is already pending or running")
                finish()
                return@launch
            }
            sendDoSyncMsg()
            reloadIntent.action = action
            reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(reloadIntent)
            finish()
        }
    }

    private fun handleFileImport(
        intent: Intent,
        reloadIntent: Intent,
        action: String?,
    ) {
        Timber.i("Handling file import")
        if (!hasShownAppIntro()) {
            Timber.i("Trying to import a file when the app was not started at all")
            showThemedToast(this, CommonString.app_not_initialized_new, false)
            finish()
            return
        }
        importScope.launch {
            val importResult = withContext(ioDispatcher) { prepareImport(this@IntentHandler, intent) }
            // finish() may precede onDestroy(), which cancels the import scope.
            if (isFinishing) return@launch
            if (intent.hasExtra(SharedDecksDownloadFragment.EXTRA_IS_SHARED_DOWNLOAD)) {
                deleteDownloadedDeck(intent.data)
            }
            when (importResult) {
                is ImportResult.Success -> {
                    openImport(importResult)
                    if (importResult.file !is ImportResolution.Text) {
                        deleteImportedDeck(intent.data?.path)
                        reloadIntent.action = action
                        reloadIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(reloadIntent)
                    }
                    finish()
                }
                is ImportResult.Failure -> {
                    Timber.i("File import failed")
                    showImportUnsuccessfulDialog(this@IntentHandler, importResult, true)
                }
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
                intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)
            } else {
                data.data
            }

        TaskStackBuilder
            .create(this)
            .addNextIntentWithParentStack(Intent(this, DeckPicker::class.java))
            .addNextIntent(NoteEditorDestination.ImageOcclusion(imageUri))
            .startActivities()
    }

    private fun handleSharedText(data: Intent) {
        Timber.i("Handling shared text content for note creation")
        val destination =
            data.extras
                ?.let { NoteEditorDestination.PassArguments.from(data, it) }
                // Fallback if no extras, though this shouldn't happen for ACTION_SEND
                ?: NoteEditorDestination.AddNote()
        navigate(destination)
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

        /** File attachment; resolved as a deck package, collection package, text, or error. */
        FILE_IMPORT,

        /** image */
        IMAGE_IMPORT,

        /** shared text content */
        SHARED_TEXT,

        SYNC,
        REVIEW,

        /** `anki://x-callback-url/browser` deep link */
        OPEN_BROWSER,
        COPY_DEBUG_INFO,
    }

    companion object {
        private const val ACTION_DO_SYNC = "com.ichi2.anki.DO_SYNC"
        const val EXTRA_DECK_ID = "EXTRA_DECK_ID"
        private const val ACTION_COPY_DEBUG_INFO = "com.ichi2.anki.COPY_DEBUG_INFO"
        private const val EXTRA_CLIPBOARD_DATA = "clip_data"

        private fun isValidViewIntent(intent: Intent): Boolean {
            // Negating a negative because we want to call specific attention to the fact that it's invalid
            // #6312 - Smart Launcher provided an empty ACTION_VIEW, no point in importing here.
            return !isInvalidViewIntent(intent)
        }

        /** Whether this is the `anki://x-callback-url/browser` deep link that opens the [CardBrowser]. */
        private fun Intent.isBrowserDeepLink(): Boolean {
            val data = data ?: return false
            return action == Intent.ACTION_VIEW &&
                data.scheme == "anki" &&
                data.host == "x-callback-url" &&
                data.path == "/browser"
        }

        @VisibleForTesting
        @CheckResult
        fun getLaunchType(intent: Intent): LaunchType {
            val action = intent.action
            return if (intent.isBrowserDeepLink()) {
                LaunchType.OPEN_BROWSER
            } else if (action == Intent.ACTION_SEND || (Intent.ACTION_VIEW == action && isValidViewIntent(intent))) {
                val mimeType = intent.resolveMimeType()
                when {
                    mimeType?.startsWith("image/") == true -> LaunchType.IMAGE_IMPORT
                    action == Intent.ACTION_SEND &&
                        intent.hasExtra(Intent.EXTRA_TEXT) &&
                        !intent.hasExtra(Intent.EXTRA_STREAM) -> LaunchType.SHARED_TEXT
                    else -> LaunchType.FILE_IMPORT
                }
            } else if (ACTION_DO_SYNC == action) {
                LaunchType.SYNC
            } else if (intent.hasExtra(EXTRA_DECK_ID)) {
                LaunchType.REVIEW
            } else if (action == ACTION_COPY_DEBUG_INFO) {
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

        fun requiresCollectionAccess(launchType: LaunchType): Boolean =
            when (launchType) {
                LaunchType.SYNC,
                LaunchType.REVIEW,
                LaunchType.DEFAULT_START_APP_IF_NEW,
                LaunchType.FILE_IMPORT,
                LaunchType.IMAGE_IMPORT,
                LaunchType.SHARED_TEXT,
                LaunchType.OPEN_BROWSER,
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
                val millisecondsSinceLastSyncAttempt = millisecondsSinceLastSyncAttempt()
                val limited = millisecondsSinceLastSyncAttempt < INTENT_SYNC_MIN_INTERVAL
                if (!limited && !hkey.isNullOrEmpty() && NetworkUtils.isOnline) {
                    startBackgroundSync(deckPicker)
                } else {
                    val err = res.getString(CommonString.sync_error)
                    if (limited) {
                        val remainingTimeInSeconds =
                            max((INTENT_SYNC_MIN_INTERVAL - millisecondsSinceLastSyncAttempt) / 1000, 1)
                        // getQuantityString needs an int
                        val remaining = min(Int.MAX_VALUE.toLong(), remainingTimeInSeconds).toInt()
                        Timber.i("Ignoring sync intent: rate limited for another %d seconds", remaining)
                        val message =
                            res.getQuantityString(
                                CommonPlurals.sync_automatic_sync_needs_more_time,
                                remaining,
                                remaining,
                            )
                        deckPicker.showSimpleNotification(err, message, NotificationChannel.SYNC)
                    } else {
                        Timber.i("Ignoring sync intent: %s", if (hkey.isNullOrEmpty()) "not logged in" else "offline")
                        deckPicker.showSimpleNotification(
                            err,
                            res.getString(CommonString.youre_offline),
                            NotificationChannel.SYNC,
                        )
                    }
                }
                deckPicker.finish()
            }

            private fun startBackgroundSync(deckPicker: DeckPicker) {
                if (MeteredSyncPolicy.shouldBlock()) {
                    Timber.i("Ignoring sync intent: metered sync is disabled")
                    return
                }
                val auth = syncAuth()
                if (auth == null) {
                    Timber.i("Ignoring sync intent: no sync credentials")
                    return
                }

                // Keep collection and media sync running after DeckPicker finishes.
                val syncMedia = shouldFetchMedia()
                Timber.i("Enqueuing background sync from intent (syncMedia=%s)", syncMedia)
                SyncWorker.start(deckPicker, auth, syncMedia = syncMedia)
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
        ): Intent = Intent(context, IntentHandler::class.java).putExtra(EXTRA_DECK_ID, deckId)
    }
}
