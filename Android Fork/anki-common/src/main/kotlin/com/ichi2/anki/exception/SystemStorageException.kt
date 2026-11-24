// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

import android.os.Environment
import timber.log.Timber

/**
 * A failure when Android is unable to provide a storage path
 *
 * For issue #13207: special handling when `getExternalFilesDir` is `null`
 * Commonly triggered by an `Android/data` directory corruption bug on Pixel phones:
 * https://issuetracker.google.com/issues/460912704
 *
 * @see [StorageAccessException] for issues with a known storage path
 */
class SystemStorageException private constructor(
    errorDetail: String,
    val externalStorageState: String,
    val infoUri: String?,
) : IllegalStateException("$errorDetail. Media state: $externalStorageState") {
    override val message: String
        get() = super.message!!

    companion object {
        fun build(
            errorDetail: String,
            infoUri: String? = null,
        ): SystemStorageException {
            val storageState =
                try {
                    Environment.getExternalStorageState()
                } catch (e: Exception) {
                    Timber.w(e, "getExternalStorageState")
                    "ERROR"
                }

            return SystemStorageException(
                errorDetail = errorDetail,
                externalStorageState = storageState,
                infoUri = infoUri,
            )
        }
    }
}
