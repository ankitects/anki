// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>
// SPDX-FileCopyrightText: Copyright (c) 2024 Mike Hardy <github@mikehardy.net>

package com.ichi2.anki.servicelayer

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.exception.SanitizedThrowable
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.utils.ExceptionUtil.causeChain
import com.ichi2.utils.ExceptionUtil.causesAndSuppressed
import net.ankiweb.rsdroid.BackendException.BackendSchedulerUpgradeRequiredException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncServerMessageException
import timber.log.Timber
import java.util.Collections
import java.util.IdentityHashMap

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
            val reportable =
                if (shouldDiscardThrowable(throwable)) {
                    // Retain diagnostic class names and frames without exposing private messages.
                    Timber.i("sanitizing uncaught throwable")
                    SanitizedThrowable.from(throwable)
                } else {
                    throwable
                }
            originalUncaughtExceptionHandler?.uncaughtException(thread, reportable)
        }

    fun initialize() {
        Timber.i("initialize()")
        installDefaultExceptionHandler()
    }

    /**
     * Sanitize filtered uncaught exceptions before delegating to the reporting handlers.
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

    /**
     * Whether a handled report should be discarded. Uncaught errors are sanitized instead, so they
     * can still reach the reporting handlers.
     */
    fun shouldDiscardThrowable(t: Throwable): Boolean {
        // PII-bearing exceptions may be nested or suppressed. ACRA's stack-trace hashing also
        // assumes the main cause chain is acyclic, so circular causes cannot be reported as-is.
        return exceptionIsUnwanted(t) || !t.safeFromPII() || t.hasCyclicCause()
    }

    // These common types are noise in handled reports. Uncaught instances still indicate crashes
    // and must reach reporting after sanitization.
    fun exceptionIsUnwanted(t: Throwable): Boolean {
        Timber.v("exceptionIsUnwanted - examining %s", t.javaClass.simpleName)
        when (t) {
            is BackendNetworkException -> return true
            is BackendSyncException -> return true
            is BackendSchedulerUpgradeRequiredException -> return true
            is StorageAccessException -> return true
        }
        Timber.v("exceptionIsUnwanted - exception was wanted")
        return false
    }

    /**
     * Checks this throwable, its causes and suppressed exceptions for known PII-bearing types.
     * @return `false` if the [Throwable] contains PII, otherwise `true`
     */
    fun Throwable.safeFromPII(): Boolean = causesAndSuppressed().none { it.containsPIINonRecursive() }

    private fun Throwable.hasCyclicCause(): Boolean {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        return causeChain.any { !seen.add(it) }
    }

    private fun Throwable.containsPIINonRecursive(): Boolean {
        // BackendSyncServerMessage may contain PII and we do not want this leaked to ACRA.
        // Related: https://github.com/ankidroid/Anki-Android/issues/17392
        // and also https://github.com/ankitects/anki/commit/ba1f5f4
        return this is BackendSyncServerMessageException
    }
}
