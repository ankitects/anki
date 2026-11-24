// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException

/**
 * The collection database is locked by another process: normally a parallel AnkiDroid install.
 *
 * Thrown in place of [BackendDbLockedException] for a better user-facing error:
 *
 * * Inform the user two apps using the same folder are not supported.
 * * Inform them they need to change the folder (via 'AnkiDroid directory')
 * * Inform them that they should use 'Force close' to temporarily solve it.
 * * Inform the user the app may not be running.
 *
 * Note: "Anki already open, or media currently syncing." is currently hardcoded as an error.
 * `rslib/src/error/db.rs`
 */
class CollectionLockedException(
    cause: BackendDbLockedException,
) : BackendException(messageProvider?.invoke() ?: cause.localizedMessage) {
    init {
        initCause(cause)
    }

    companion object {
        /**
         * Localized, user-facing guidance shown wherever this exception is displayed.
         *
         * Set at app startup: this module cannot access the app's string resources.
         * TODO: after #21500, use the string directly
         */
        var messageProvider: (() -> String)? = null
    }
}
