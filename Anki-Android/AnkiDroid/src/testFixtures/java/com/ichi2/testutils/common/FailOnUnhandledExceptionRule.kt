/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.testutils.common

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import timber.log.Timber

/**
 * (#16253) Under unit tests, ACRA may infinitely loop when trying to handle an unhandled exception
 *
 * The default test behavior is to suppress an exception
 *
 * This rule replaces both UsageAnalytics and ACRA, ensuring a failure is reported
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

                Timber.v("test: applying exception handler override")
                exceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { _: Thread?, throwable: Throwable ->
                    Timber.e(throwable, "test: unhandled exception")
                    uncaughtException = throwable
                }

                try {
                    base.evaluate()
                } finally {
                    Timber.v("test: removing exception handler override")
                    Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)
                }

                // throw instead of asserting to get the full stack trace
                uncaughtException?.let { throw IllegalStateException("unhandled exception", it) }
            }
        }
    }
}
