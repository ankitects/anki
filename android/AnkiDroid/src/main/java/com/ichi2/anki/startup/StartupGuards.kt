// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.app.Activity
import android.content.Intent
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.storage.StorageDecision
import timber.log.Timber

/**
 * Ensures storage is ready for use, finishes the activity is otherwise.
 *
 * - the user has decided where the collection is stored
 * - the app has permission to access
 *
 * This should be called AFTER a call to `super.`[onCreate][Activity.onCreate]
 *
 * @return `true`: activity may continue to start, `false`: [onCreate][Activity.onCreate]
 * should stop executing
 *
 * @throws SystemStorageException if `getExternalFilesDir` returns null
 */
fun Activity.ensureStorageIsReady(): Boolean {
    // The storage decision is checked first
    // as the permissions required depend on the chosen folder
    return ensureStorageIsConfigured() && ensureStoragePermissions()
}

/**
 * If the user has not yet decided where the collection is stored, finishes the
 * activity and redirects to the app start.
 *
 * Catches launches which bypass [IntentHandler]: pinned shortcuts, task restoration
 * and internal navigation.
 *
 * @return `true`: activity may continue to start, `false`: [onCreate][Activity.onCreate]
 * should stop executing as storage is not configured
 *
 * @see redirectToMainEntryPoint
 */
private fun Activity.ensureStorageIsConfigured(): Boolean {
    if (CollectionHelper.storageDecision() == StorageDecision.Decided) {
        return true
    }
    Timber.w("finishing activity. Storage is not configured")
    redirectToMainEntryPoint()
    return false
}

/**
 * If storage permissions are not granted, shows a toast message and finishes the activity.
 *
 * @return `true`: activity may continue to start, `false`: [onCreate][Activity.onCreate]
 * should stop executing as storage permissions are mot granted
 *
 * @throws SystemStorageException if `getExternalFilesDir` returns null
 */
private fun Activity.ensureStoragePermissions(): Boolean {
    if (IntentHandler.grantedStoragePermissions(this, showToast = true)) {
        return true
    }
    Timber.w("finishing activity. No storage permission")
    finish()
    return false
}

/**
 * Finishes this activity and opens the primary entry point.
 *
 * Use when an entry point cannot run (e.g. storage is not configured), the target screen handles
 * the failures.
 */
fun Activity.redirectToMainEntryPoint() {
    Timber.i("redirecting to app start")
    startActivity(
        Intent(this, IntentHandler::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        },
    )
    finish()
}
