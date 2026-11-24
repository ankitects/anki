// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>

package com.ichi2.anki

import anki.backend.backendError
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.servicelayer.ThrowableFilterService
import com.ichi2.anki.servicelayer.ThrowableFilterService.safeFromPII
import com.ichi2.utils.ExceptionUtil.causeChain
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendSchedulerUpgradeRequiredException
import net.ankiweb.rsdroid.exceptions.BackendDeckIsFilteredException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncServerMessageException
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThrowableFilterServiceTest {
    @Test
    fun `scheduler upgrade required exceptions are discarded`() {
        val exception = BackendSchedulerUpgradeRequiredException(backendError {})
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception))
    }

    @Test
    fun `other backend exceptions are retained`() {
        assertFalse(ThrowableFilterService.shouldDiscardThrowable(BackendException(backendError {})))
    }

    @Test
    fun `Normal exceptions are flagged as PII-safe`() {
        val exception = BackendDeckIsFilteredException(backendError {})
        assertTrue(exception.safeFromPII(), "Exception reported as safe from PII")
    }

    @Test
    fun `BackendSyncServerMessage exceptions are flagged as PII-unsafe`() {
        val exception1 = BackendSyncServerMessageException(backendError { })
        assertFalse(exception1.safeFromPII(), "Exception reported as not safe from PII")

        val exception2 = Exception("", Exception("", exception1))
        assertFalse(exception2.safeFromPII(), "Nested exception reported as not safe from PII")
    }

    @Test
    fun `exceptions are discarded correctly by type`() {
        // regular exceptions should go through
        assertFalse(ThrowableFilterService.shouldDiscardThrowable(Exception("wanted")))

        // exceptions of known unwanted types should not go through
        val exception1 = BackendNetworkException(backendError {})
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception1))
        val exception2 = BackendSyncException(backendError {})
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception2))
        val exception3 = StorageAccessException("test exception")
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception3))
    }

    @Test
    fun `circular causes are discarded before reporting`() {
        val exception = Exception()
        val cause = Exception()
        exception.initCause(cause)
        cause.initCause(exception)

        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception))
    }

    @Test
    fun `PII in suppressed exceptions is filtered`() {
        val exception = Exception()
        val suppressed = Exception()
        suppressed.addSuppressed(BackendSyncServerMessageException(backendError {}))
        exception.addSuppressed(suppressed)

        assertFalse(exception.safeFromPII())
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception))

        suppressed.addSuppressed(exception)
        assertFalse(exception.safeFromPII())
    }

    @Test
    fun `ordinary uncaught exceptions reach the next handler unchanged`() {
        val cause = IllegalArgumentException("wanted cause")
        val exception = IllegalStateException("wanted", cause)

        assertSame(exception, forwardUnhandled(exception))
        exception.addSuppressed(cause)
        assertFalse(ThrowableFilterService.shouldDiscardThrowable(exception))
        assertSame(exception, forwardUnhandled(exception))
    }

    @Test
    fun `unwanted uncaught exceptions reach crash reporting without messages`() {
        val exceptions =
            listOf(
                BackendNetworkException(backendError { message = "private network details" }),
                BackendSyncException(backendError { message = "private sync details" }),
                BackendSchedulerUpgradeRequiredException(backendError { message = "private scheduler details" }),
                StorageAccessException("private storage path"),
                BackendSyncServerMessageException(backendError { message = "private@example.com" }),
            )
        for (exception in exceptions) {
            val forwarded = forwardUnhandled(exception)

            assertNotSame(exception, forwarded)
            assertEquals(exception.javaClass.name, forwarded.toString())
            assertContentEquals(exception.stackTrace, forwarded.stackTrace)
            assertFalse(forwarded.stackTraceToString().contains("private"))
            // Handled reports of the same types must continue to be filtered.
            assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception))
        }
    }

    @Test
    fun `uncaught PII exceptions have their entire cause chain sanitized`() {
        val serverError = BackendSyncServerMessageException(backendError { message = "private@example.com" })
        val cause =
            object : IllegalArgumentException("private cause", serverError) {
                override fun toString(): String = error("Must not read a custom description")

                override fun getLocalizedMessage(): String = error("Must not read a localized message")
            }
        val exception = IllegalStateException("private outer message", cause)
        // Sharing a cause as a suppressed exception must not truncate the main cause chain.
        exception.addSuppressed(serverError)

        val forwarded = forwardUnhandled(exception)

        assertEquals(exception.javaClass.name, forwarded.toString())
        assertContentEquals(exception.stackTrace, forwarded.stackTrace)
        val forwardedCause = assertNotNull(forwarded.cause)
        assertEquals(cause.javaClass.name, forwardedCause.toString())
        assertContentEquals(cause.stackTrace, forwardedCause.stackTrace)
        val forwardedServerError = assertNotNull(forwardedCause.cause)
        assertEquals(serverError.javaClass.name, forwardedServerError.toString())
        assertContentEquals(serverError.stackTrace, forwardedServerError.stackTrace)
        assertFalse(forwarded.stackTraceToString().contains("private"))
        assertEquals("private outer message", exception.message)
        assertSame(cause, exception.cause)
        assertSame(serverError, cause.cause)
    }

    @Test
    fun `suppressed PII exceptions are detected and sanitized`() {
        val exception = IllegalStateException("private outer message")
        val suppressed = IllegalArgumentException("private suppressed message")
        suppressed.addSuppressed(BackendSyncServerMessageException(backendError { message = "private@example.com" }))
        exception.addSuppressed(suppressed)

        assertFalse(exception.safeFromPII())
        assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception))
        val forwarded = forwardUnhandled(exception)

        val forwardedSuppressed = forwarded.suppressed.single()
        assertEquals(suppressed.javaClass.name, forwardedSuppressed.toString())
        assertContentEquals(suppressed.stackTrace, forwardedSuppressed.stackTrace)
        val suppressedServerError = suppressed.suppressed.single()
        assertEquals(suppressedServerError.javaClass.name, forwardedSuppressed.suppressed.single().toString())
        assertFalse(forwarded.stackTraceToString().contains("private"))
        assertSame(suppressed, exception.suppressed.single())
    }

    @Test
    fun `circular exception graphs are sanitized with and without PII types`() {
        val exception = Exception("private outer message")
        val cause = Exception("private cause")
        exception.initCause(cause)
        cause.initCause(exception)
        cause.addSuppressed(exception)
        assertTrue(exception.safeFromPII())

        repeat(2) {
            val forwarded = forwardUnhandled(exception)
            val forwardedCause = assertNotNull(forwarded.cause)
            assertNotSame(exception, forwarded)
            assertEquals(cause.javaClass.name, forwardedCause.toString())
            assertEquals(null, forwardedCause.cause)
            assertTrue(forwardedCause.suppressed.isEmpty())
            assertTrue(ThrowableFilterService.shouldDiscardThrowable(exception))
            assertFalse(forwarded.stackTraceToString().contains("private"))

            exception.addSuppressed(BackendSyncServerMessageException(backendError { message = "private@example.com" }))
            assertFalse(exception.safeFromPII())
        }
    }

    @Test
    fun `deep exception chains do not overflow filtering or sanitization`() {
        var cause: Throwable = BackendSyncServerMessageException(backendError { message = "private@example.com" })
        repeat(2000) { cause = Exception("private cause", cause) }

        assertFalse(cause.safeFromPII())
        val forwarded = forwardUnhandled(cause)

        val forwardedCauses = forwarded.causeChain.toList()
        assertEquals(2001, forwardedCauses.size)
        assertTrue(forwardedCauses.none { it.message?.contains("private") == true })
    }

    @Test
    fun `reinitialization keeps filtered fatal errors reportable`() {
        val exception = StorageAccessException("private path")
        val reports = mutableListOf<Throwable>()
        withReportingHandler(
            Thread.UncaughtExceptionHandler { _, throwable -> reports.add(throwable) },
        ) {
            ThrowableFilterService.reInitialize()
            val handler = assertNotNull(Thread.getDefaultUncaughtExceptionHandler())

            handler.uncaughtException(Thread.currentThread(), exception)

            val report = reports.single()
            assertEquals(exception.javaClass.name, report.toString())
            assertFalse(report.stackTraceToString().contains("private"))
        }
    }

    @Test
    fun `installed filter forwards an uncaught worker thread exception`() {
        val exception = StorageAccessException("private storage path")
        val worker = Thread { throw exception }
        var forwardedThread: Thread? = null
        var forwarded: Throwable? = null
        var reports = 0
        withReportingHandler(
            Thread.UncaughtExceptionHandler { thread, throwable ->
                reports++
                forwardedThread = thread
                forwarded = throwable
            },
        ) {
            worker.start()
            worker.join(5000)

            assertFalse(worker.isAlive)
            assertEquals(1, reports)
            assertSame(worker, forwardedThread)
            assertEquals(exception.javaClass.name, assertNotNull(forwarded).toString())
            assertFalse(assertNotNull(forwarded).stackTraceToString().contains("private"))
        }
    }

    private fun withReportingHandler(
        reportingHandler: Thread.UncaughtExceptionHandler,
        block: () -> Unit,
    ) {
        val originalDefault = Thread.getDefaultUncaughtExceptionHandler()
        val originalHandler = ThrowableFilterService.originalUncaughtExceptionHandler
        try {
            Thread.setDefaultUncaughtExceptionHandler(reportingHandler)
            ThrowableFilterService.initialize()
            block()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalDefault)
            ThrowableFilterService.originalUncaughtExceptionHandler = originalHandler
        }
    }

    private fun forwardUnhandled(exception: Throwable): Throwable {
        val thread = Thread.currentThread()
        var forwarded: Throwable? = null
        var calls = 0
        withReportingHandler(
            Thread.UncaughtExceptionHandler { actualThread, throwable ->
                assertSame(thread, actualThread)
                forwarded = throwable
                calls++
            },
        ) {
            ThrowableFilterService.uncaughtExceptionHandler.uncaughtException(thread, exception)
        }
        assertEquals(1, calls, "The uncaught exception must reach the next handler exactly once")
        return assertNotNull(forwarded)
    }
}
