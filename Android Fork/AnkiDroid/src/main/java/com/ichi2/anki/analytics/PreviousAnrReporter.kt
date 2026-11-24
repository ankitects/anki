// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.ext.requireSystemService
import com.ichi2.anki.exception.AnrReportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Launches reporting of a previous run's ANR in the background during main-process startup on Android 11+.
 * Logs the reporting duration using the standard startup timing.
 */
internal fun AnkiDroidApp.launchPreviousAnrReporting() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    applicationScope.launch(Dispatchers.IO) {
        try {
            setup("reportPreviousAnr") {
                reportPreviousAnr()
            }
        } catch (_: Exception) {
            // setup() already logged the failure; this diagnostic must not interrupt startup.
        }
    }
}

/**
 * Reports the latest main-process exit caused by an ANR as an [AnrReportException].
 *
 * Uses the captured main-thread stack; other report metadata describes this reporting launch.
 *
 * ANRs that recover before the process exits for another reason are not included.
 */
@RequiresApi(Build.VERSION_CODES.R)
@WorkerThread
context(context: AnkiDroidApp)
internal fun reportPreviousAnr() {
    // Android's exit history is shared across profiles, so the processed marker must be too.
    val state = SharedPreferencesAnrReportState(context.baseContext)
    val since = state.lastProcessedTimestamp

    // ApplicationExitInfo has no app version. Do not attribute an older build's ANR to this one.
    @Suppress("DEPRECATION")
    val updatedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    val activityManager = context.requireSystemService<ActivityManager>()
    val latestAnrExitInfo =
        activityManager
            .getHistoricalProcessExitReasons(null, 0, 0)
            .filter {
                it.reason == ApplicationExitInfo.REASON_ANR &&
                    it.processName == context.packageName &&
                    it.timestamp > since && it.timestamp >= updatedAt
            }.maxByOrNull { it.timestamp } ?: return

    // Avoid processing the same ANR on every launch.
    if (!state.markProcessed(latestAnrExitInfo.timestamp)) return
    val anrReport = latestAnrExitInfo.readAnrReport() ?: return

    // TODO(#22027): Evaluate API 37 AnrInfo metadata (ANR type, timeout, user perceptibility)
    CrashReportService.sendExceptionReport(
        anrReport,
        origin = "PreviousAnrReporter",
        additionalInfo = "Previous ANR: process exited at ${latestAnrExitInfo.instant}.",
        onlyIfSilent = true,
    )
}

/** The timestamp of the process's death. */
@get:RequiresApi(Build.VERSION_CODES.R)
private val ApplicationExitInfo.instant: Instant
    get() = Instant.ofEpochMilli(timestamp)

@RequiresApi(Build.VERSION_CODES.R)
private fun ApplicationExitInfo.readAnrReport(): AnrReportException? =
    traceInputStream?.reader()?.use { AnrReportException(it.readAnrMainThreadStackTrace()) }
