// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import timber.log.Timber

/**
 * A custom [Thread.UncaughtExceptionHandler] designed to intercept fatal app crashes,
 * log them to the analytics provider (e.g., GA4), and safely pass them down the chain
 * to the original exception handler.
 *
 * @property originalHandler The pre-existing exception handler in the thread's chain.
 * This is called immediately after the analytics report is dispatched.
 * @property sendExceptionReport A delegated function responsible for transmitting the
 * [Throwable] to the analytics engine. The boolean parameter
 * indicates if the exception is fatal (always `true` here).
 */
class AnalyticsExceptionHandler(
    val originalHandler: Thread.UncaughtExceptionHandler?,
    private val sendExceptionReport: (Throwable, Boolean) -> Unit,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        sendExceptionReport(throwable, true)
        originalHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        /**
         * Safely installs the [AnalyticsExceptionHandler] as the default handler for the thread.
         *
         * @param sendExceptionReport The function reference to be used for sending the crash data.
         */
        fun install(sendExceptionReport: (Throwable, Boolean) -> Unit) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is AnalyticsExceptionHandler) {
                Thread.setDefaultUncaughtExceptionHandler(
                    AnalyticsExceptionHandler(currentHandler, sendExceptionReport),
                )
                Timber.d("AnalyticsExceptionHandler installed.")
            }
        }

        /**
         * Uninstalls the [AnalyticsExceptionHandler] by restoring the thread's default
         * exception handler back to the [originalHandler] that was present before installation.
         */
        fun uninstall() {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler is AnalyticsExceptionHandler) {
                Thread.setDefaultUncaughtExceptionHandler(currentHandler.originalHandler)
                Timber.d("AnalyticsExceptionHandler uninstalled.")
            }
        }
    }
}
