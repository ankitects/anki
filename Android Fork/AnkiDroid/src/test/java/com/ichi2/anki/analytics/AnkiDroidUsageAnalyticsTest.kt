// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class AnkiDroidUsageAnalyticsTest : RobolectricTest() {
    @Test
    fun `settings switch key matches the key analytics reads`() {
        // the Settings switch is a plain SwitchPreferenceCompat: it writes this key
        // itself, so a mismatch silently stops it controlling analytics
        assertThat(
            targetContext.getString(R.string.analytics_opt_in_key),
            equalTo(AnkiDroidUsageAnalytics.PREF_ANALYTICS_OPTIN),
        )
    }

    @Test
    fun `opt-in key is the v2 key`() {
        assertThat(AnkiDroidUsageAnalytics.PREF_ANALYTICS_OPTIN, equalTo("analytics_opt_in_v2"))
    }

    @Test
    fun `analytics is disabled by default`() {
        assertThat(getPreferences().getBoolean(AnkiDroidUsageAnalytics.PREF_ANALYTICS_OPTIN, false), equalTo(false))
    }

    @Test
    fun `a preference write from elsewhere rebuilds the client`() {
        withStubbedRebuild {
            getPreferences().edit(commit = true) { putBoolean(AnkiDroidUsageAnalytics.PREF_ANALYTICS_OPTIN, true) }

            assertThat(AnkiDroidUsageAnalytics.isEnabled, equalTo(true))
            verify(exactly = 1) { AnkiDroidUsageAnalytics.reinitialize(any()) }
        }
    }

    @Test
    fun `a write that leaves the value unchanged does not rebuild the client`() {
        withStubbedRebuild {
            getPreferences().edit(commit = true) { putBoolean(AnkiDroidUsageAnalytics.PREF_ANALYTICS_OPTIN, false) }

            verify(exactly = 0) { AnkiDroidUsageAnalytics.reinitialize(any()) }
        }
    }

    /**
     * `ThrowableFilterService` chains itself above the analytics handler at startup to
     * strip PII. Rebuilding the client must leave that chain alone: re-installing the
     * handler would put analytics ahead of the filter, and report each crash twice.
     */
    @Test
    fun `rebuilding the client leaves the exception handler chain untouched`() =
        runTest {
            val base = Thread.getDefaultUncaughtExceptionHandler()
            try {
                AnkiDroidUsageAnalytics.initialize(targetContext)
                val analytics = Thread.getDefaultUncaughtExceptionHandler()
                assertThat(analytics, instanceOf(AnalyticsExceptionHandler::class.java))
                // stand in for ThrowableFilterService, which chains itself above us
                val filter = PassThroughHandler(analytics)
                Thread.setDefaultUncaughtExceptionHandler(filter)

                AnkiDroidUsageAnalytics.rebuild(targetContext)

                assertThat(
                    "the PII filter must stay outermost",
                    Thread.getDefaultUncaughtExceptionHandler(),
                    sameInstance<Any>(filter),
                )
                assertThat(
                    "analytics must not be duplicated in the chain",
                    (analytics as AnalyticsExceptionHandler).originalHandler,
                    sameInstance<Any>(base),
                )
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(base)
            }
        }

    private class PassThroughHandler(
        private val next: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(
            thread: Thread,
            throwable: Throwable,
        ) {
            next?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Runs [block] with the analytics listener registered and `reinitialize` stubbed out:
     * the real one rebuilds on [kotlinx.coroutines.Dispatchers.IO] and would outlive the test.
     */
    private fun withStubbedRebuild(block: () -> Unit) {
        mockkObject(AnkiDroidUsageAnalytics)
        every { AnkiDroidUsageAnalytics.reinitialize(any()) } returns Unit
        try {
            AnkiDroidUsageAnalytics.initialize(targetContext)
            block()
        } finally {
            // clear the preference before unmocking, so the resulting change
            // doesn't reach the real `reinitialize`
            getPreferences().edit(commit = true) { remove(AnkiDroidUsageAnalytics.PREF_ANALYTICS_OPTIN) }
            unmockkObject(AnkiDroidUsageAnalytics)
            AnalyticsExceptionHandler.uninstall()
        }
    }
}
