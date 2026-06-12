// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.app.Activity
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.exception.SystemStorageException
import timber.log.Timber

/**
 * If storage permissions are not granted, shows a toast message and finishes the activity.
 *
 * This should be called AFTER a call to `super.`[onCreate][Activity.onCreate]
 *
 * @return `true`: activity may continue to start, `false`: [onCreate][Activity.onCreate]
 * should stop executing as storage permissions are mot granted
 *
 * @throws SystemStorageException if `getExternalFilesDir` returns null
 */
fun Activity.ensureStoragePermissions(): Boolean {
    if (IntentHandler.grantedStoragePermissions(this, showToast = true)) {
        return true
    }
    Timber.w("finishing activity. No storage permission")
    finish()
    return false
}
