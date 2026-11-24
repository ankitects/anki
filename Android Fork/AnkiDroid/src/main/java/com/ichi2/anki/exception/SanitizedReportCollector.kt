// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

import android.content.Context
import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.collector.Collector
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData

/**
 * Filters logcat from sanitized reports due to PII: private messages may have been logged before
 * the uncaught exception handler ran. ACRA saves the report after all collectors have finished.
 */
class SanitizedReportCollector : Collector {
    // LogCatCollector runs FIRST; filter its output after collection, including in parallel mode.
    override val order = Collector.Order.LAST

    override fun collect(
        context: Context,
        config: CoreConfiguration,
        reportBuilder: ReportBuilder,
        crashReportData: CrashReportData,
    ) {
        if (reportBuilder.exception is SanitizedThrowable && crashReportData.containsKey(ReportField.LOGCAT)) {
            crashReportData.put(
                ReportField.LOGCAT,
                "Logcat filtered because it may contain personally identifiable information (PII).",
            )
        }
    }
}
