// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki

import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.work.NetworkType
import anki.collection.Progress
import anki.sync.SyncCollectionResponse
import anki.sync.syncAuth
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CollectionManager.withColExclusive
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.observability.ChangeManager.notifySubscribersAllValuesChanged
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.sync.MediaSync
import com.ichi2.anki.sync.MeteredSyncPermission
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.sync.SyncAuth
import com.ichi2.anki.sync.fullUploadOrDownload
import com.ichi2.anki.sync.syncCollection
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.preferences.VersatileTextWithASwitchPreference
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.dismissSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

object SyncPreferences {
    const val PREF_CURRENT_SYNC_URI = "currentSyncUri"
    const val CUSTOM_SYNC_URI = "syncBaseUrl"
    const val CUSTOM_SYNC_ENABLED = CUSTOM_SYNC_URI + VersatileTextWithASwitchPreference.SWITCH_SUFFIX
}

enum class ConflictResolution {
    FULL_DOWNLOAD,
    FULL_UPLOAD,
}

fun syncAuth(): SyncAuth? {
    // Grab custom sync certificate from preferences (default is the empty string) and set it in CollectionManager
    val currentSyncCertificate = Prefs.customSyncCertificate ?: ""
    CollectionManager.updateCustomCertificate(currentSyncCertificate)

    val resolvedEndpoint = getEndpoint()
    return Prefs.hkey?.let {
        syncAuth {
            this.hkey = it
            if (resolvedEndpoint != null) {
                this.endpoint = resolvedEndpoint
            }
            this.ioTimeoutSecs = Prefs.networkTimeoutSecs
        }.let(::SyncAuth)
    }
}

fun getEndpoint(): String? {
    val currentEndpoint = Prefs.currentSyncUri?.ifEmpty { null }
    val customEndpoint =
        if (Prefs.isCustomSyncEnabled) {
            Prefs.customSyncUri
        } else {
            null
        }
    return currentEndpoint ?: customEndpoint
}

/**
 * Whether the user has a sync account.
 * Returning true does not guarantee that the user actually synced recently,
 * or even that the ankiweb account is still valid.
 */
fun isLoggedIn(): Boolean = !Prefs.hkey.isNullOrEmpty()

/** Elapsed milliseconds since the last manual or automatic sync attempt. */
fun millisecondsSinceLastSyncAttempt() = TimeManager.time.intTimeMS() - Prefs.lastSyncTime

fun DeckPicker.handleNewSync(
    conflict: ConflictResolution?,
    syncMedia: Boolean,
    meteredSyncPermission: MeteredSyncPermission = MeteredSyncPermission.USE_PREFERENCES,
) {
    val auth = syncAuth() ?: return
    val mediaNetworkType = MeteredSyncPolicy.getNetworkTypeRequiredForSync(forMedia = true, permission = meteredSyncPermission)
    val deckPicker = this
    launchCatchingTask {
        try {
            try {
                when (conflict) {
                    ConflictResolution.FULL_DOWNLOAD ->
                        handleDownload(deckPicker, auth, deckPicker.mediaUsnOnConflict, deckPicker.mediaNetworkTypeOnConflict)
                    ConflictResolution.FULL_UPLOAD ->
                        handleUpload(deckPicker, auth, deckPicker.mediaUsnOnConflict, deckPicker.mediaNetworkTypeOnConflict)
                    null -> {
                        handleNormalSync(deckPicker, auth, syncMedia, mediaNetworkType)
                    }
                }
            } catch (exc: BackendSyncException.BackendSyncAuthFailedException) {
                // auth failed; log out
                updateLogin("", "")
                throw exc
            } catch (exc: BackendSyncException) {
                if (exc.isClockOffException()) {
                    showSyncErrorDialog(
                        SyncErrorDialog.Type.DIALOG_SYNC_CLOCK_OFF,
                        exc.localizedMessage,
                    )
                    return@launchCatchingTask
                }

                if (exc.isCollectionTooLargeException()) {
                    showSyncErrorDialog(
                        SyncErrorDialog.Type.DIALOG_COLLECTION_TOO_LARGE,
                        exc.localizedMessage,
                    )
                    return@launchCatchingTask
                }

                throw exc
            }
            withCol { notetypes.clearCache() }
            notifySubscribersAllValuesChanged(deckPicker)
            refreshState()
        } finally {
            setLastSyncTimeToNow()
        }
    }
}

/** The backend currently exposes ClockIncorrect as a localized sync error message. */
private fun BackendSyncException.isClockOffException(): Boolean =
    try {
        message == TR.syncClockOff()
    } catch (_: Throwable) {
        false
    }

private val collectionTooLargeDetailsRegex =
    Regex("""\b\d+(?:\.\d{2})?(?: MB)? > \d+(?:\.\d{2})?(?: MB)?\b""")

private fun BackendSyncException.isCollectionTooLargeException(): Boolean =
    message?.let { collectionTooLargeDetailsRegex.containsMatchIn(it) } == true

fun updateLogin(
    username: String,
    hkey: String,
) {
    Prefs.username = username
    Prefs.hkey = hkey
}

fun cancelSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortSync()
}

private suspend fun handleNormalSync(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    syncMedia: Boolean,
    mediaNetworkType: NetworkType,
) {
    Timber.i("Sync: Normal collection sync")
    var auth2 = auth
    val output =
        deckPicker.withProgress(
            extractProgress = {
                if (progress.hasNormalSync()) {
                    text = progress.normalSync.run { "$added\n$removed" }
                }
            },
            onCancel = ::cancelSync,
            manualCancelButton = CommonString.dialog_cancel,
        ) {
            withColExclusive(
                operation = CollectionOperation.SYNC,
                onCancel = ::cancelSync,
            ) {
                syncCollection(auth2, syncMedia = false) // media is synced by SyncMediaWorker
            }
        }

    if (output.hasNewEndpoint() && output.newEndpoint.isNotEmpty()) {
        Timber.i("sync endpoint updated")
        Prefs.currentSyncUri = output.newEndpoint
        auth2 = auth.withEndpoint(output.newEndpoint)
    }
    val mediaUsn =
        if (syncMedia) {
            output.serverMediaUsn
        } else {
            null
        }

    Timber.i("sync result: ${output.required}")
    when (output.required) {
        // a successful sync returns this value
        SyncCollectionResponse.ChangesRequired.NO_CHANGES -> {
            // scheduler version may have changed
            withCol { _loadScheduler() }
            val message = if (syncMedia) CommonString.col_synced_media_in_background else CommonString.sync_database_acknowledge
            deckPicker.showSyncLogMessage(message, output.serverMessage)
            deckPicker.refreshState()
            if (syncMedia) {
                SyncMediaWorker.start(deckPicker, auth2, mediaNetworkType)
            }
        }

        SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD -> {
            handleDownload(deckPicker, auth2, mediaUsn, mediaNetworkType)
        }

        SyncCollectionResponse.ChangesRequired.FULL_UPLOAD -> {
            handleUpload(deckPicker, auth2, mediaUsn, mediaNetworkType)
        }

        SyncCollectionResponse.ChangesRequired.FULL_SYNC -> {
            deckPicker.mediaUsnOnConflict = mediaUsn
            deckPicker.mediaNetworkTypeOnConflict = mediaNetworkType
            deckPicker.showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_SYNC_CONFLICT_RESOLUTION)
        }

        SyncCollectionResponse.ChangesRequired.NORMAL_SYNC,
        SyncCollectionResponse.ChangesRequired.UNRECOGNIZED,
        null,
        -> {
            TODO("should never happen")
        }
    }
}

private fun fullDownloadProgress(title: String): ProgressContext.() -> Unit =
    {
        fun Progress.FullSync.toAmount() = ProgressContext.Amount(transferred.toLong(), total.toLong())

        text = title
        if (progress.hasFullSync() && progress.fullSync.total > 0) {
            amount = progress.fullSync.toAmount()
        }
    }

private suspend fun handleDownload(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    mediaUsn: Int?,
    mediaNetworkType: NetworkType,
) {
    Timber.i("Sync: Full collection download requested")
    deckPicker.withProgress(
        progressContext = ProgressContext.ofBytes(context = deckPicker).copy(separator = "\n"),
        extractProgress = fullDownloadProgress(TR.syncDownloadingFromAnkiweb()),
        onCancel = ::cancelSync,
        manualCancelButton = CommonString.dialog_cancel,
    ) {
        withColExclusive(
            operation = CollectionOperation.FULL_DOWNLOAD,
            onCancel = ::cancelSync,
        ) {
            try {
                val created =
                    createBackup(
                        BackupManager.getBackupDirectoryFromCollection(colDb),
                        force = true,
                        waitForCompletion = true,
                    )
                if (!created) {
                    Timber.i("Sync: Backup skipped before full download: collection unchanged")
                }
                close(downgrade = false, forFullSync = true)
                // Media starts through SyncMediaWorker so WorkManager can enforce network constraints.
                fullUploadOrDownload(
                    auth,
                    upload = false,
                    mediaSync = MediaSync.Skip,
                )
            } finally {
                reopen(afterFullSync = true)
            }
        }
        deckPicker.refreshState()
        if (mediaUsn != null) {
            SyncMediaWorker.start(deckPicker, auth, mediaNetworkType)
        }
    }

    Timber.i("Full Download Completed")
    deckPicker.showSyncLogMessage(CommonString.backup_one_way_sync_from_server, "")
}

private suspend fun handleUpload(
    deckPicker: DeckPicker,
    auth: SyncAuth,
    mediaUsn: Int?,
    mediaNetworkType: NetworkType,
) {
    Timber.i("Sync: Full collection upload requested")
    deckPicker.withProgress(
        progressContext = ProgressContext.ofBytes(context = deckPicker).copy(separator = "\n"),
        extractProgress = fullDownloadProgress(TR.syncUploadingToAnkiweb()),
        onCancel = ::cancelSync,
        manualCancelButton = CommonString.dialog_cancel,
    ) {
        withColExclusive(
            operation = CollectionOperation.FULL_UPLOAD,
            onCancel = ::cancelSync,
        ) {
            close(downgrade = false, forFullSync = true)
            try {
                // Media starts through SyncMediaWorker so WorkManager can enforce network constraints.
                fullUploadOrDownload(
                    auth,
                    upload = true,
                    mediaSync = MediaSync.Skip,
                )
            } finally {
                reopen(afterFullSync = true)
            }
        }
        deckPicker.refreshState()
        if (mediaUsn != null) {
            SyncMediaWorker.start(deckPicker, auth, mediaNetworkType)
        }
    }
    Timber.i("Full Upload Completed")
    deckPicker.showSyncLogMessage(CommonString.sync_log_uploading_message, "")
}

fun cancelMediaSync(backend: Backend) {
    backend.setWantsAbort()
    backend.abortMediaSync()
}

/**
 * Whether media should be fetched on sync. Options from preferences are:
 * * Always
 * * Only if unmetered
 * * Never
 */
fun shouldFetchMedia(): Boolean {
    val shouldFetchMedia = Prefs.shouldFetchMedia
    return shouldFetchMedia == ShouldFetchMedia.ALWAYS ||
        (shouldFetchMedia == ShouldFetchMedia.ONLY_UNMETERED && !NetworkUtils.isActiveNetworkMetered())
}

suspend fun monitorMediaSync(deckPicker: DeckPicker) {
    val backend = CollectionManager.getBackend()
    val scope = CoroutineScope(Dispatchers.IO)
    var isAborted = false

    val dialog =
        withContext(Dispatchers.Main) {
            AlertDialog
                .Builder(deckPicker)
                .setTitle(with(deckPicker) { TR.sentenceCase.mediaSyncLog })
                .setMessage("")
                .setPositiveButton(CommonString.dialog_continue) { _, _ ->
                    scope.cancel()
                }.setNegativeButton(TR.syncAbortButton()) { _, _ ->
                    isAborted = true
                    cancelMediaSync(backend)
                }.show()
        }

    fun showMessage(msg: String) = deckPicker.showSnackbar(msg, Snackbar.LENGTH_SHORT)

    scope.launch {
        try {
            while (true) {
                // this will throw if the sync exited with an error
                val resp = backend.mediaSyncStatus()
                if (!resp.active) {
                    break
                }
                val text = resp.progress.run { "$added\n$removed\n$checked" }
                dialog.setMessage(text)
                delay(100.milliseconds)
            }
            showMessage(if (isAborted) TR.syncMediaAborted() else TR.syncMediaComplete())
        } catch (_: BackendInterruptedException) {
            showMessage(TR.syncMediaAborted())
        } catch (_: CancellationException) {
            // do nothing
        } catch (_: Exception) {
            showMessage(TR.syncMediaFailed())
        } finally {
            dialog.dismissSafely()
        }
    }
}

/**
 * Show a simple snackbar message or notification if the activity is not in foreground
 * @param messageResource String resource for message
 */
fun DeckPicker.showSyncLogMessage(
    @StringRes messageResource: Int,
    syncMessage: String?,
) {
    if (activityPaused) {
        val res = AnkiDroidApp.appResources
        showSimpleNotification(
            res.getString(R.string.app_name),
            res.getString(messageResource),
            NotificationChannel.SYNC,
        )
    } else {
        if (syncMessage.isNullOrEmpty()) {
            showSnackbar(messageResource)
        } else {
            val res = AnkiDroidApp.appResources
            showSimpleMessageDialog(title = res.getString(messageResource), message = syncMessage)
        }
    }
}

/** Starts the retry delay after every sync attempt, regardless of outcome. */
fun setLastSyncTimeToNow() {
    Prefs.lastSyncTime = TimeManager.time.intTimeMS()
}
