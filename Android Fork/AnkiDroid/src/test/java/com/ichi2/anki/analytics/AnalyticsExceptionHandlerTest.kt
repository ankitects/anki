// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class AnalyticsExceptionHandlerTest {
    private var original: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        original = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(original)
    }

    @Test
    fun `install chains to the existing handler`() {
        val base = RecordingHandler()
        Thread.setDefaultUncaughtExceptionHandler(base)

        AnalyticsExceptionHandler.install { _, _ -> }

        val installed = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(installed, instanceOf(AnalyticsExceptionHandler::class.java))
        assertThat((installed as AnalyticsExceptionHandler).originalHandler, sameInstance<Any>(base))
    }

    @Test
    fun `install is a no-op when already the outermost handler`() {
        Thread.setDefaultUncaughtExceptionHandler(RecordingHandler())
        AnalyticsExceptionHandler.install { _, _ -> }
        val installed = Thread.getDefaultUncaughtExceptionHandler()

        AnalyticsExceptionHandler.install { _, _ -> }

        assertThat(Thread.getDefaultUncaughtExceptionHandler(), sameInstance<Any>(installed))
    }

    @Test
    fun `uninstall restores the handler it wrapped`() {
        val base = RecordingHandler()
        Thread.setDefaultUncaughtExceptionHandler(base)
        AnalyticsExceptionHandler.install { _, _ -> }

        AnalyticsExceptionHandler.uninstall()

        assertThat(Thread.getDefaultUncaughtExceptionHandler(), sameInstance<Any>(base))
    }

    @Test
    fun `the wrapped handler still runs after reporting`() {
        var reports = 0
        var delegated = 0
        Thread.setDefaultUncaughtExceptionHandler(RecordingHandler { delegated++ })
        AnalyticsExceptionHandler.install { _, _ -> reports++ }

        Thread
            .getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertThat(reports, equalTo(1))
        assertThat(delegated, equalTo(1))
    }

    /** Swallows the throwable so it can't fail the test run. */
    private class RecordingHandler(
        private val onHandled: () -> Unit = {},
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(
            thread: Thread,
            throwable: Throwable,
        ) {
            onHandled()
        }
    }
}
