// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki

import androidx.annotation.CheckResult
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.BackendException
import kotlin.time.Duration.Companion.seconds

/**
 * Attempts a backup and awaits its completion.
 *
 * @param force Bypass the minimum backup interval. Unchanged collections are still skipped.
 * @return `true` if a backup completed successfully; `false` if the collection has not changed
 * since the last backup, or if [force] is `false` and the minimum backup interval has not elapsed.
 * @throws BackendException if backup creation or completion fails. See [Collection.createBackup]
 * for full exception details.
 */
@CheckResult
suspend fun performBackupInBackground(force: Boolean = false): Boolean {
    // Wait a second to allow the deck list to finish loading first, or it
    // will hang until the first stage of the backup completes.
    delay(1.seconds)
    return createBackup(force = force)
}

fun <Activity> Activity.importColpkg(colpkgPath: String) where Activity : AnkiActivity, Activity : ImportColpkgListener {
    launchCatchingTask {
        withProgress(
            extractProgress = {
                if (progress.hasImporting()) {
                    text = progress.importing
                }
            },
        ) {
            CollectionManager.importColpkg(colpkgPath)
        }

        onImportColpkg(colpkgPath)
    }
}

/**
 * Held for the whole of a background backup, including the stage the backend
 * finishes after it has released the collection.
 *
 * Waiting on the backend directly does not work from elsewhere: it hands its
 * running backup to whichever caller asks first, and [createBackup] asks as soon
 * as the backup starts, so a second caller returns without waiting.
 */
val backupLock = Mutex()

private suspend fun createBackup(force: Boolean) =
    backupLock.withLock {
        createBackupHoldingLock(force)
    }

private suspend fun createBackupHoldingLock(force: Boolean): Boolean {
    val created =
        withCol {
            // this two-step approach releases the backend lock after the initial copy
            createBackup(
                BackupManager.getBackupDirectoryFromCollection(colDb),
                force,
                waitForCompletion = false,
            )
        }
    // move this outside 'withCol' to avoid blocking
    withContext(Dispatchers.IO) {
        CollectionManager.getBackend().awaitBackupCompletion()
    }
    return created
}
