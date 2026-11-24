// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.logging.ProductionCrashReportingTree
import com.ichi2.anki.servicelayer.ThrowableFilterService
import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.CrashReportData
import org.acra.data.CrashReportDataFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Exercises the packaged ACRA collectors against Android's logcat and the uncaught-error filter. */
@RunWith(AndroidJUnit4::class)
class SanitizedReportCollectorIntegrationTest {
    private val tree = ProductionCrashReportingTree()

    @Before
    fun setUp() {
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
    }

    @Test
    fun sanitizedReportsExcludePrivateMessagesAlreadyWrittenToLogcat() {
        val privateMessage = "private-${UUID.randomUUID()}@example.com"
        val exception = StorageAccessException(privateMessage)
        Timber.w(exception)

        // Prove that the real LogCatCollector can read the private message before filtering.
        val unfiltered = collectReport(exception, parallel = false)
        assertTrue(assertNotNull(unfiltered.getString(ReportField.LOGCAT)).contains(privateMessage))

        for (parallel in listOf(false, true)) {
            val report = collectUnhandledReport(exception, parallel)

            assertEquals(
                "Logcat filtered because it may contain personally identifiable information (PII).",
                report.getString(ReportField.LOGCAT),
            )
            assertFalse(report.toJSON().contains(privateMessage))
            assertTrue(assertNotNull(report.getString(ReportField.STACK_TRACE)).contains(exception.javaClass.name))
            assertFalse(report.getString(ReportField.STACK_TRACE_HASH).isNullOrEmpty())
        }
    }

    @Test
    fun ordinaryReportsRetainTheirLogcatDiagnostics() {
        val message = "ordinary diagnostics ${UUID.randomUUID()}"
        val exception = IllegalStateException(message)
        Timber.w(exception)

        for (parallel in listOf(false, true)) {
            val report = collectUnhandledReport(exception, parallel)

            assertTrue(assertNotNull(report.getString(ReportField.LOGCAT)).contains(message))
            assertTrue(assertNotNull(report.getString(ReportField.STACK_TRACE)).contains(message))
        }
    }

    @Test
    fun unrelatedThreadFailuresReachOriginalHandler() {
        val backgroundFailure = IllegalStateException("unrelated background failure")
        val worker =
            Thread { throw backgroundFailure }.apply {
                uncaughtExceptionHandler = ThrowableFilterService.uncaughtExceptionHandler
            }
        val originalHandler = ThrowableFilterService.originalUncaughtExceptionHandler
        var delegatedThread: Thread? = null
        var delegatedFailure: Throwable? = null
        val delegate =
            Thread.UncaughtExceptionHandler { thread, throwable ->
                if (thread === worker) {
                    delegatedThread = thread
                    delegatedFailure = throwable
                } else {
                    originalHandler?.uncaughtException(thread, throwable)
                }
            }
        try {
            ThrowableFilterService.originalUncaughtExceptionHandler = delegate
            val expectedFailure = IllegalStateException("expected report failure")
            val report =
                collectUnhandledReport(expectedFailure, parallel = false) {
                    worker.start()
                    worker.join(5000)
                    assertFalse(worker.isAlive, "The unrelated exception handler must finish")
                    assertSame(worker, delegatedThread)
                    assertSame(backgroundFailure, delegatedFailure)
                }

            assertTrue(assertNotNull(report.getString(ReportField.STACK_TRACE)).contains("expected report failure"))
            assertSame(delegate, ThrowableFilterService.originalUncaughtExceptionHandler)
        } finally {
            ThrowableFilterService.originalUncaughtExceptionHandler = originalHandler
        }
    }

    private fun collectUnhandledReport(
        exception: Throwable,
        parallel: Boolean,
        beforeReport: () -> Unit = {},
    ): CrashReportData {
        val reportingThread = Thread.currentThread()
        val originalHandler = ThrowableFilterService.originalUncaughtExceptionHandler
        var report: CrashReportData? = null
        try {
            // Capture this thread's report before ACRA's sending and process-termination stages.
            ThrowableFilterService.originalUncaughtExceptionHandler =
                Thread.UncaughtExceptionHandler { thread, reportable ->
                    if (thread === reportingThread) {
                        report = collectReport(reportable, parallel)
                    } else {
                        originalHandler?.uncaughtException(thread, reportable)
                    }
                }
            beforeReport()
            ThrowableFilterService.uncaughtExceptionHandler.uncaughtException(reportingThread, exception)
        } finally {
            ThrowableFilterService.originalUncaughtExceptionHandler = originalHandler
        }
        return assertNotNull(report, "The uncaught exception must reach report collection")
    }

    private fun collectReport(
        exception: Throwable,
        parallel: Boolean,
    ): CrashReportData {
        val config =
            CoreConfigurationBuilder()
                .withReportContent(ReportField.STACK_TRACE, ReportField.STACK_TRACE_HASH, ReportField.LOGCAT)
                .withLogcatArguments("-t", "500", "-v", "time", "${AnkiDroidApp.TAG}:D", "*:S")
                .withParallel(parallel)
                .build()
        return CrashReportDataFactory(getApplicationContext<Context>(), config).createCrashData(ReportBuilder().exception(exception))
    }
}
