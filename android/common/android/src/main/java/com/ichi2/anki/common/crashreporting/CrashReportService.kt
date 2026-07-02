// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 lukstbit <lukstbit@users.noreply.github.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.crashreporting

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
import timber.log.Timber

/**
 * Interface for crash/exception reporting.
 *
 * Implemented in the app module by the ACRA-backed crash reporter.
 *
 * TODO: Remove Context/Activity parameters from this interface and use the
 *  application context set during initialization instead. Context is only used
 *  for SharedPreferences and LimiterData, so an Application context is likely
 *  sufficient. Removing them enables use from java-library modules.
 *  Note: profile-aware contexts may affect this decision.
 */
interface CrashReporter {
    fun sendExceptionReport(
        message: String?,
        origin: String?,
    )

    fun sendExceptionReport(
        e: Throwable,
        origin: String?,
        additionalInfo: String? = null,
        onlyIfSilent: Boolean = false,
    )

    fun sendExceptionReport(
        e: Throwable,
        origin: String?,
        additionalInfo: String? = null,
        onlyIfSilent: Boolean = false,
        context: Context,
    )

    fun onPreferenceChanged(
        ctx: Context,
        newValue: String,
    )

    fun deleteLimiterData(context: Context)

    fun setReportingMode(value: String)

    fun isEnabled(
        context: Context,
        defaultValue: Boolean,
    ): Boolean

    fun sendReport(activity: Activity): Boolean

    companion object {
        const val FEEDBACK_REPORT_KEY = "reportErrorMode"
        const val FEEDBACK_REPORT_ASK = "2"
        const val FEEDBACK_REPORT_NEVER = "1"
        const val FEEDBACK_REPORT_ALWAYS = "0"
    }
}

/**
 * Global crash reporting service. Delegates to the [CrashReporter] implementation
 * set during app initialization.
 *
 * Usage:
 * ```
 * CrashReportService.sendExceptionReport(exception, "MyClass.myMethod")
 * ```
 */
object CrashReportService {
    lateinit var instance: CrashReporter
        private set

    fun setReporter(reporter: CrashReporter) {
        instance = reporter
    }

    @VisibleForTesting
    fun getReporter(): CrashReporter = instance

    /**
     * Reports a non-fatal issue without a [Throwable].
     * @param message Description of what went wrong.
     * @param origin The class or method name where the issue occurred.
     */
    fun sendExceptionReport(
        message: String?,
        origin: String?,
    ) = instance.sendExceptionReport(message, origin)

    // TODO: remove these from instance and handle the conversion
    //  of parameters in this class/an extension method

    /**
     * Reports a caught [Throwable] to the crash reporting service.
     * @param e The exception or error to report.
     * @param origin The tag or location where it occurred.
     * @param additionalInfo Optional extra metadata to include in the report.
     * @param onlyIfSilent If true, only sends the report if the user has opted into automatic reporting.
     */
    fun sendExceptionReport(
        e: Throwable,
        origin: String?,
        additionalInfo: String? = null,
        onlyIfSilent: Boolean = false,
    ) = instance.sendExceptionReport(e, origin, additionalInfo, onlyIfSilent)

    /**
     * Reports a caught [Throwable] with a specific [Context].
     * Use this when the application context is insufficient
     */
    fun sendExceptionReport(
        e: Throwable,
        origin: String?,
        additionalInfo: String? = null,
        onlyIfSilent: Boolean = false,
        context: Context,
    ) = instance.sendExceptionReport(e, origin, additionalInfo, onlyIfSilent, context)

    /**
     * Manually triggers a crash report, often used for user-initiated feedback.
     * @return True if the report was successfully queued; false if rate-limited.
     */
    fun sendReport(activity: Activity): Boolean = instance.sendReport(activity)

    fun onPreferenceChanged(
        ctx: Context,
        newValue: String,
    ) = instance.onPreferenceChanged(ctx, newValue)

    fun deleteLimiterData(context: Context) = instance.deleteLimiterData(context)

    fun setReportingMode(value: String) = instance.setReportingMode(value)

    fun isEnabled(
        context: Context,
        defaultValue: Boolean,
    ): Boolean = instance.isEnabled(context, defaultValue)
}

/**
 * Runs the provided block, catching [Exception], logging it and reporting it to [CrashReportService]
 *
 * **Example**
 * ```
 * runCatchingWithReport("callingMethod", onlyIfSilent = true) {
 *     doSomethingRisky()
 * }
 * ```
 *
 * **Note**: This differs from [runCatching] - `Error` is thrown
 *
 * @param origin Data logged to Timber, and provided as the 'origin' field in the error report
 * @param onlyIfSilent Skip crash report if the crash reporting service is not 'always accept'
 * @param block Code to execute
 *
 * @throws Error If raised, this will be reported and rethrown
 *
 * @return A Result containing either the successful result of [block] or the [Exception] thrown
 */
fun <T> runCatchingWithReport(
    origin: String?,
    onlyIfSilent: Boolean = false,
    block: () -> T,
): Result<T> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        Timber.w(e, origin)
        CrashReportService.sendExceptionReport(e, origin, onlyIfSilent = onlyIfSilent)
        if (e is Error) throw e
        Result.failure(e)
    }
