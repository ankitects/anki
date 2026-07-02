/*
 * Copyright (c) 2024 voczi <dev@voczi.com>
 * Copyright (c) 2024 Mike Hardy <github@mikehardy.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.servicelayer

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.exception.StorageAccessException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncServerMessageException
import timber.log.Timber

object ThrowableFilterService {
    @FunctionalInterface
    fun interface FilteringExceptionHandler : Thread.UncaughtExceptionHandler

    @VisibleForTesting
    var originalUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null

    var uncaughtExceptionHandler =
        FilteringExceptionHandler { thread: Thread?, throwable: Throwable ->
            if (thread == null) {
                Timber.w("unexpected: thread was null")
                return@FilteringExceptionHandler
            }
            if (shouldDiscardThrowable(throwable)) {
                Timber.i("discarding throwable")
                return@FilteringExceptionHandler
            }
            originalUncaughtExceptionHandler?.uncaughtException(thread, throwable)
        }

    fun initialize() {
        Timber.i("initialize()")
        installDefaultExceptionHandler()
    }

    /**
     * We want to filter any exceptions with PII, then chain to other handlers
     */
    @Synchronized
    @VisibleForTesting
    fun installDefaultExceptionHandler() {
        originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Timber.d("Chaining to uncaughtExceptionHandler (%s)", originalUncaughtExceptionHandler)
        Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler)
    }

    /**
     * Reset the default exception handler
     */
    @Synchronized
    @VisibleForTesting
    fun unInstallDefaultExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)
        originalUncaughtExceptionHandler = null
    }

    /**
     * Re-Initialize the throwable filter
     */
    @Synchronized
    fun reInitialize() {
        // send any pending async hits, re-chain default exception handlers and re-init
        Timber.i("reInitialize()")
        unInstallDefaultExceptionHandler()
        initialize()
    }

    fun shouldDiscardThrowable(t: Throwable): Boolean {
        // Note that an exception may have a nested BackendSyncException,
        // so we check if it is safe from PII despite also filtering by type
        return exceptionIsUnwanted(t) || !t.safeFromPII()
    }

    // There are few exception types that are common, but are unwanted in
    // our analytics or crash report service because they are not actionable
    fun exceptionIsUnwanted(t: Throwable): Boolean {
        Timber.v("exceptionIsUnwanted - examining %s", t.javaClass.simpleName)
        when (t) {
            is BackendNetworkException -> return true
            is BackendSyncException -> return true
            is StorageAccessException -> return true
        }
        Timber.v("exceptionIsUnwanted - exception was wanted")
        return false
    }

    /**
     * Checks if the [Throwable] is safe from Personally Identifiable Information (PII)
     * @return `false` if the [Throwable] contains PII, otherwise `true`
     */
    fun Throwable.safeFromPII(): Boolean {
        if (this.containsPIINonRecursive()) return false
        return this.cause?.safeFromPII() != false
    }

    private fun Throwable.containsPIINonRecursive(): Boolean {
        // BackendSyncServerMessage may contain PII and we do not want this leaked to ACRA.
        // Related: https://github.com/ankidroid/Anki-Android/issues/17392
        // and also https://github.com/ankitects/anki/commit/ba1f5f4
        return this is BackendSyncServerMessageException
    }
}
