// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.crashreporting

import android.util.Log
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CrashReportServiceTest {
    private data class LogEntry(
        val priority: Int,
        val message: String,
        val throwable: Throwable?,
    )

    private val logs = mutableListOf<LogEntry>()

    private val recordingTree =
        object : Timber.Tree() {
            override fun log(
                priority: Int,
                tag: String?,
                message: String,
                t: Throwable?,
            ) {
                logs.add(LogEntry(priority, message, t))
            }
        }

    @BeforeEach
    fun setUp() {
        CrashReportService.resetForTesting()
        Timber.plant(recordingTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uproot(recordingTree)
    }

    /**
     * Crash reporting is called from error-handling paths, so it must never throw.
     *
     * A report can be sent before [CrashReportService.setReporter] is called during
     * app initialization: `AnkiDroidApp.onCreate` may return early (#5887), and
     * `ContentProvider`s are created before `Application.onCreate` completes.
     */
    @Test
    fun `reporting before initialization is logged, not thrown`() {
        val exception = Exception("reported before setReporter")

        CrashReportService.sendExceptionReport(exception, origin = "CrashReportServiceTest")

        // WARN is collected into ACRA reports (LOGCAT report field): no PII
        val error = logs.single { it.priority == Log.ERROR }
        assertNull(error.throwable, "ERROR should not contain the throwable: its message may contain PII")
        assertFalse(error.message.contains(exception.message!!), "ERROR should not contain the exception message")
        assertTrue(error.message.contains("java.lang.Exception"), "ERROR should contain the exception class")

        // DEBUG is not collected into ACRA reports: full detail for local debugging
        val debug = logs.single { it.priority == Log.DEBUG }
        assertSame(debug.throwable, exception, "DEBUG should contain the dropped exception")
    }
}
