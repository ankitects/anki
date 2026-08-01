// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <github@mikehardy.net>

package com.ichi2.anki.tests

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.acraCoreConfigBuilder
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_ALWAYS
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_ASK
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.logging.ProductionCrashReportingTree
import com.ichi2.anki.servicelayer.ThrowableFilterService
import com.ichi2.anki.setDebugACRAConfig
import com.ichi2.anki.setProductionACRAConfig
import com.ichi2.anki.testutil.GrantStoragePermission
import org.acra.ACRA
import org.acra.builder.ReportBuilder
import org.acra.config.ACRAConfigurationException
import org.acra.config.LimitingReportAdministrator
import org.acra.config.ToastConfiguration
import org.acra.data.CrashReportDataFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
@SuppressLint("DirectSystemCurrentTimeMillisUsage")
class ACRATest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance
    private var app: AnkiDroidApp? = null
    private val debugLogcatArguments = arrayOf("-t", "1500", "-v", "long", "ACRA:S")

    // private String[] prodLogcatArguments = { "-t", "100", "-v", "time", "ActivityManager:I", "SQLiteLog:W", AnkiDroidApp.TAG + ":D", "*:S" };
    @Before
    @UiThreadTest
    fun setUp() {
        app = testContext.applicationContext as AnkiDroidApp
        // Note: attachBaseContext can't be called twice as we're using the same instance between all tests.
        app!!.onCreate()
    }

    @Test
    @Throws(Exception::class)
    fun testDebugConfiguration() {
        // Debug mode overrides all saved state so no setup needed
        setDebugACRAConfig(sharedPrefs)
        assertArrayEquals(
            "Debug logcat arguments not set correctly",
            CrashReportService.acraCoreConfigBuilder
                .build()
                .logcatArguments
                .toTypedArray(),
            debugLogcatArguments,
        )
        verifyDebugACRAPreferences()
    }

    private fun verifyDebugACRAPreferences() {
        assertTrue(
            "ACRA was not disabled correctly",
            sharedPrefs
                .getBoolean(ACRA.PREF_DISABLE_ACRA, true),
        )
        assertEquals(
            "ACRA feedback was not turned off correctly",
            CrashReporter.FEEDBACK_REPORT_NEVER,
            sharedPrefs
                .getString(CrashReporter.FEEDBACK_REPORT_KEY, "undefined"),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProductionConfigurationUserDisabled() {
        // set up as if the user had prefs saved to disable completely
        setReportConfig(CrashReporter.FEEDBACK_REPORT_NEVER)

        // ACRA initializes production logcat via annotation and we can't mock Build.DEBUG
        // That means we are restricted from verifying production logcat args and this is the debug case again
        setProductionACRAConfig(sharedPrefs)
        verifyDebugACRAPreferences()
    }

    @Test
    @Throws(Exception::class)
    fun testProductionConfigurationUserAsk() {
        // set up as if the user had prefs saved to ask
        setReportConfig(FEEDBACK_REPORT_ASK)

        // If the user is set to ask, then it's production, with interaction mode dialog
        setProductionACRAConfig(sharedPrefs)
        verifyACRANotDisabled()

        assertToastMessage(R.string.feedback_for_manual_toast_text)
        assertToastIsEnabled()
        assertDialogEnabledStatus("Dialog should be enabled", true)
    }

    @Test
    @Throws(Exception::class)
    fun testCrashReportLimit() {
        // To test ACRA switch on  reporting, plant a production tree, and trigger a report
        Timber.plant(ProductionCrashReportingTree())

        // set up as if the user had prefs saved to full auto
        setReportConfig(FEEDBACK_REPORT_ALWAYS)

        // If the user is set to always, then it's production, with interaction mode toast
        // will be useful with ACRA 5.2.0
        setProductionACRAConfig(sharedPrefs)

        // The same class/method combo is only sent once, so we face a new method each time (should test that system later)
        val crash = Exception("testCrashReportSend at " + System.currentTimeMillis())
        val trace =
            arrayOf(
                StackTraceElement(
                    "Class",
                    "Method" + System.currentTimeMillis().toInt(),
                    "File",
                    System.currentTimeMillis().toInt(),
                ),
            )
        crash.stackTrace = trace

        // one send should work
        val crashData =
            CrashReportDataFactory(
                testContext,
                CrashReportService.acraCoreConfigBuilder.build(),
            ).createCrashData(ReportBuilder().exception(crash))
        assertTrue(
            LimitingReportAdministrator().shouldSendReport(
                testContext,
                CrashReportService.acraCoreConfigBuilder.build(),
                crashData,
            ),
        )

        // A second send should not work
        assertFalse(
            LimitingReportAdministrator().shouldSendReport(
                testContext,
                CrashReportService.acraCoreConfigBuilder.build(),
                crashData,
            ),
        )

        // Now let's clear data
        CrashReportService.deleteLimiterData(testContext)

        // A third send should work again
        assertTrue(
            LimitingReportAdministrator().shouldSendReport(
                testContext,
                CrashReportService.acraCoreConfigBuilder.build(),
                crashData,
            ),
        )
    }

    @Test
    @Throws(Exception::class)
    fun testProductionConfigurationUserAlways() {
        // set up as if the user had prefs saved to full auto
        setReportConfig(FEEDBACK_REPORT_ALWAYS)

        // If the user is set to always, then it's production, with interaction mode toast
        setProductionACRAConfig(sharedPrefs)
        verifyACRANotDisabled()

        assertToastMessage(R.string.feedback_auto_toast_text)
        assertToastIsEnabled()
        assertDialogEnabledStatus("Dialog should not be enabled", false)
    }

    @Test
    @Throws(Exception::class)
    fun testDialogEnabledWhenMovingFromAlwaysToAsk() {
        // Raised in #6891 - we ned to ensure that the dialog is re-enabled after this transition.
        setReportConfig(FEEDBACK_REPORT_ALWAYS)

        // If the user is set to ask, then it's production, with interaction mode dialog
        setProductionACRAConfig(sharedPrefs)
        verifyACRANotDisabled()

        assertDialogEnabledStatus("dialog should be disabled when status is ALWAYS", false)
        assertToastMessage(R.string.feedback_auto_toast_text)

        setAcraReportingMode(FEEDBACK_REPORT_ASK)

        assertDialogEnabledStatus("dialog should be re-enabled after changed to ASK", true)
        assertToastMessage(R.string.feedback_for_manual_toast_text)
    }

    @Test
    @Throws(Exception::class)
    fun testToastTextWhenMovingFromAskToAlways() {
        // Raised in #6891 - we ned to ensure that the text is fixed after this transition.
        setReportConfig(FEEDBACK_REPORT_ASK)

        // If the user is set to ask, then it's production, with interaction mode dialog
        setProductionACRAConfig(sharedPrefs)
        verifyACRANotDisabled()

        assertToastMessage(R.string.feedback_for_manual_toast_text)

        setAcraReportingMode(FEEDBACK_REPORT_ALWAYS)

        assertToastMessage(R.string.feedback_auto_toast_text)
    }

    @Test
    fun verifyExceptionHandlerChain() {
        // contains assumptions about ordering in ACRA, ThrowableFilter and UsageAnalytics
        // making sure they are correct is vital though, so we will accept the need to change
        // this test if you re-order them
        var firstExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat("First handler is ThrowableFilterService", firstExceptionHandler is ThrowableFilterService.FilteringExceptionHandler)
        ThrowableFilterService.unInstallDefaultExceptionHandler()
        var secondExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(
            "Second handler is AnalyticsLoggingExceptionHandler",
            secondExceptionHandler is UsageAnalytics.AnalyticsLoggingExceptionHandler,
        )
        UsageAnalytics.unInstallDefaultExceptionHandler()
        var thirdExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(
            "Third handler is neither Analytics nor ThrowableFilter",
            thirdExceptionHandler !is UsageAnalytics.AnalyticsLoggingExceptionHandler &&
                thirdExceptionHandler !is ThrowableFilterService.FilteringExceptionHandler,
        )

        // chain them again
        UsageAnalytics.installDefaultExceptionHandler()
        ThrowableFilterService.installDefaultExceptionHandler()

        // reinitialize things and make sure they came through correctly again
        CrashReportService.onPreferenceChanged(app!!.applicationContext, FEEDBACK_REPORT_ASK)
        firstExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat("First handler is ThrowableFilterService", firstExceptionHandler is ThrowableFilterService.FilteringExceptionHandler)
        ThrowableFilterService.unInstallDefaultExceptionHandler()
        secondExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Timber.i("Second handler is a %s", secondExceptionHandler)
        assertThat(
            "Second handler is AnalyticsLoggingExceptionHandler",
            secondExceptionHandler is UsageAnalytics.AnalyticsLoggingExceptionHandler,
        )
        UsageAnalytics.unInstallDefaultExceptionHandler()
        thirdExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(
            "Third handler is neither Analytics nor ThrowableFilter",
            thirdExceptionHandler !is UsageAnalytics.AnalyticsLoggingExceptionHandler &&
                thirdExceptionHandler !is ThrowableFilterService.FilteringExceptionHandler,
        )
    }

    private fun setAcraReportingMode(feedbackReportAlways: String) {
        CrashReportService.setReportingMode(feedbackReportAlways)
    }

    @Throws(ACRAConfigurationException::class)
    private fun assertDialogEnabledStatus(
        message: String,
        isEnabled: Boolean,
    ) {
        val config = CrashReportService.acraCoreConfigBuilder.build()
        for (configuration in config.pluginConfigurations) {
            // Make sure the dialog is set to pop up
            if (configuration.javaClass.toString().contains("Dialog")) {
                assertThat(message, configuration.enabled(), equalTo(isEnabled))
            }
        }
    }

    @Throws(ACRAConfigurationException::class)
    private fun assertToastIsEnabled() {
        val config = CrashReportService.acraCoreConfigBuilder.build()
        for (configuration in config.pluginConfigurations) {
            if (configuration.javaClass.toString().contains("Toast")) {
                assertThat("Toast should be enabled", configuration.enabled(), equalTo(true))
            }
        }
    }

    @Throws(ACRAConfigurationException::class)
    private fun assertToastMessage(
        @StringRes res: Int,
    ) {
        val config = CrashReportService.acraCoreConfigBuilder.build()
        for (configuration in config.pluginConfigurations) {
            if (configuration.javaClass.toString().contains("Toast")) {
                assertEquals(
                    app!!.resources.getString(res),
                    (configuration as ToastConfiguration).text,
                )
                assertTrue("Toast should be enabled", configuration.enabled())
            }
        }
    }

    private fun verifyACRANotDisabled() {
        assertFalse(
            "ACRA was not enabled correctly",
            sharedPrefs.getBoolean(ACRA.PREF_DISABLE_ACRA, false),
        )
    }

    private fun setReportConfig(feedbackReportAsk: String) {
        sharedPrefs.edit { putString(CrashReporter.FEEDBACK_REPORT_KEY, feedbackReportAsk) }
    }

    private val sharedPrefs: SharedPreferences
        get() = testContext.sharedPrefs()
}
