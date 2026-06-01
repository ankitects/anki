/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
