// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.common

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(FailOnUnhandledExceptionRule::class.java)

/**
 * (#16253) Under unit tests, ACRA may infinitely loop when trying to handle an unhandled exception
 *
 * The default test behavior is to suppress an exception
 *
 * This rule replaces both AnkiDroidUsageAnalytics and ACRA, ensuring a failure is reported
 *
 * When applying this rule, it SHOULD be applied after Application.onCreate, otherwise the exception
 * handlers will override it
 *
 * This is not validated as `@Config(application = EmptyApplication::class)` is a valid
 * use case where `exceptionHandler` is null
 */
class FailOnUnhandledExceptionRule : TestRule {
    private var uncaughtException: Throwable? = null
    private var exceptionHandler: Thread.UncaughtExceptionHandler? = null

    var isEnabled = true

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        return object : Statement() {
            override fun evaluate() {
                if (!isEnabled) return base.evaluate()

                logger.trace("test: applying exception handler override")
                exceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { _: Thread?, throwable: Throwable ->
                    logger.error("test: unhandled exception", throwable)
                    uncaughtException = throwable
                }

                val failures = mutableListOf<Throwable>()
                try {
                    base.evaluate()
                } catch (failure: Throwable) {
                    failures.add(failure)
                } finally {
                    logger.trace("test: removing exception handler override")
                    Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)
                }

                // Preserve uncaught exceptions even when an assertion or assumption also failed.
                uncaughtException?.let { failures.add(IllegalStateException("unhandled exception", it)) }
                MultipleFailureException.assertEmpty(failures)
            }
        }
    }
}
