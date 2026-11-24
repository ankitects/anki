// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import android.annotation.SuppressLint
import android.util.Log
import com.ichi2.anki.logging.ProductionCrashReportingTree
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.MockedStatic
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.whenever
import timber.log.Timber
import java.lang.Exception
import java.lang.RuntimeException

@SuppressLint("LogNotTimber", "LogConditional")
class ProductionCrashReportingTreeTest {
    @Before
    fun setUp() {
        // setup - simply instrument the class and do same log init as production
        Timber.plant(ProductionCrashReportingTree())
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
    }

    /**
     * The Production logger ignores verbose and debug logs on purpose
     * Make sure these ignored log levels are not passed to the platform logger
     */
    @Test
    fun testProductionDebugVerboseIgnored() {
        mockStatic(Log::class.java).use {
            // set up the platform log so that if anyone calls these 2 methods at all, it throws
            whenever(Log.v(anyString(), anyString(), any()))
                .thenThrow(RuntimeException("Verbose logging should have been ignored"))
            whenever(Log.d(anyString(), anyString(), any()))
                .thenThrow(RuntimeException("Debug logging should be ignored"))
            whenever(
                Log.i(anyString(), anyString(), any()),
            ).thenThrow(RuntimeException("Info logging should throw!"))

            // now call our wrapper - if it hits the platform logger it will throw
            assertDoesNotThrow { Timber.v("verbose") }
            assertDoesNotThrow { Timber.d("debug") }
            try {
                Timber.i("info")
                Assert.fail("we should have gone to Log.i and thrown but did not? Testing mechanism failure.")
            } catch (e: Exception) {
                // this means everything worked, we were counting on an exception
            }
        }
    }

    /**
     * The levels that are fully logged have special "tag" behavior per-level
     *
     *
     * Info: always [AnkiDroidApp.TAG] as the logging tag
     * Warn/Error: tag is LoggingClass.className()'s most specific dot-separated String subsection
     */
    @Test
    fun testProductionLogTag() {
        var testWithProperClassNameCalled = false

        // this is required to ensure 'NativeMethodAccessorImpl' isn't the class name
        fun testWithProperClassName(autoClosed: MockedStatic<Log>) {
            // Now let's run through our API calls...
            Timber.i("info level message")
            Timber.w("warn level message")
            Timber.e("error level message")

            // ...and make sure they hit the logger class post-processed correctly
            autoClosed.verify {
                Log.i(AnkiDroidApp.TAG, "info level message", null)
            }

            autoClosed.verify {
                Log.w(
                    AnkiDroidApp.TAG,
                    this.javaClass.simpleName + "/ " + "warn level message",
                    null,
                )
            }
            autoClosed.verify {
                Log.e(
                    AnkiDroidApp.TAG,
                    this.javaClass.simpleName + "/ " + "error level message",
                    null,
                )
            }
            testWithProperClassNameCalled = true
        }

        mockStatic(Log::class.java).use {
            testWithProperClassName(it)
        }

        assertThat(testWithProperClassNameCalled, equalTo(true))
    }

    @Test
    fun testProductionLogExplicitTag() {
        mockStatic(Log::class.java).use { autoClosed ->
            Timber.tag("ExplicitTag").w("warn level message")
            Timber.tag("ExplicitTag").e("error level message")

            autoClosed.verify {
                Log.w(AnkiDroidApp.TAG, "ExplicitTag/ warn level message", null)
            }
            autoClosed.verify {
                Log.e(AnkiDroidApp.TAG, "ExplicitTag/ error level message", null)
            }
        }
    }
}
