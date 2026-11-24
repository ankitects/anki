// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.net.Uri
import androidx.core.net.toUri
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.utils.ExceptionUtil

/**
 * Types of unrecoverable errors which we want to inform the user of
 */
sealed class FatalInitializationError {
    data class WebViewError(
        val error: Throwable,
    ) : FatalInitializationError()

    data class StorageError(
        val error: SystemStorageException,
    ) : FatalInitializationError()

    /** Advanced/developer-facing string representing the error */
    val errorDetail: String
        get() =
            when (this) {
                is WebViewError -> ExceptionUtil.getExceptionMessage(error)
                is StorageError -> error.message
            }

    val infoLink: Uri?
        get() =
            when (this) {
                is WebViewError -> null
                is StorageError -> error.infoUri?.toUri()
            }
}
