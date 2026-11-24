// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import androidx.annotation.VisibleForTesting

/** Errors recorded during startup, owned by a single application instance. */
internal class StartupErrors {
    /** Set if an unrecoverable error occurs during application startup. */
    var fatalError: FatalInitializationError? = null
        private set

    /** Returns whether at least one fatal startup error is currently recorded. */
    fun hasFatalError(): Boolean = fatalError != null

    fun recordFatalError(error: FatalInitializationError) {
        fatalError = error
    }

    @VisibleForTesting
    fun clear() {
        fatalError = null
    }
}
