// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 lukstbit <lukstbit@users.noreply.github.com>

package com.ichi2.anki

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import com.ichi2.anki.analytics.AnkiDroidCrashReportDialog
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.analytics.UsageAnalytics.sendAnalyticsException
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_ALWAYS
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_ASK
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_KEY
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_NEVER
import com.ichi2.anki.common.exception.ManuallyReportedException
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.exception.UserSubmittedException
import com.ichi2.anki.servicelayer.ThrowableFilterService
import com.ichi2.utils.WebViewDebugging.setDataDirectorySuffix
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.config.LimiterConfigurationBuilder
import org.acra.config.LimiterData
import org.acra.config.ToastConfigurationBuilder
import org.acra.sender.HttpSender
import timber.log.Timber

private object AcraCrashReporter : CrashReporter {
    /** Our ACRA configurations, initialized during Application.onCreate()  */
    @JvmStatic
    private var logcatArgs =
        arrayOf(
            "-t",
            "500",
            "-v",
            "time",
            "ActivityManager:I",
            "SQLiteLog:W",
            AnkiDroidApp.TAG + ":D",
            "rsdroid:E",
            "*:S",
        )

    @JvmStatic
    private var dialogEnabled = true

    @JvmStatic
    private lateinit var toastText: String

    @JvmStatic
    lateinit var acraCoreConfigBuilder: CoreConfigurationBuilder
        private set
    private lateinit var application: Application
    private const val WEBVIEW_VER_NAME = "WEBVIEW_VER_NAME"
    private const val MIN_INTERVAL_MS = 60000
    private const val EXCEPTION_MESSAGE = "Exception report sent by user manually. See: 'Comment/USER_COMMENT'"

    private enum class ToastType(
        @StringRes private val toastMessageRes: Int,
    ) {
        AUTO_TOAST(R.string.feedback_auto_toast_text),
        MANUAL_TOAST(R.string.feedback_for_manual_toast_text),
        ;

        fun getToastMessage(context: Context) = context.getString(toastMessageRes)
    }

    private fun createAcraCoreConfigBuilder(): CoreConfigurationBuilder {
        val builder =
            CoreConfigurationBuilder()
                .withBuildConfigClass(com.ichi2.anki.BuildConfig::class.java) // AnkiDroid BuildConfig - Acrarium#319
                .withExcludeMatchingSharedPreferencesKeys("username", "hkey", "currentSyncUri", "browser_search_history")
                .withSharedPreferencesName("acra")
                .withReportContent(
                    ReportField.REPORT_ID,
                    ReportField.APP_VERSION_CODE,
                    ReportField.APP_VERSION_NAME,
                    ReportField.PACKAGE_NAME,
                    ReportField.FILE_PATH,
                    ReportField.PHONE_MODEL,
                    ReportField.ANDROID_VERSION,
                    ReportField.BUILD,
                    ReportField.BRAND,
                    ReportField.PRODUCT,
                    ReportField.TOTAL_MEM_SIZE,
                    ReportField.AVAILABLE_MEM_SIZE,
                    ReportField.BUILD_CONFIG,
                    ReportField.CUSTOM_DATA,
                    ReportField.STACK_TRACE,
                    ReportField.STACK_TRACE_HASH,
                    ReportField.CRASH_CONFIGURATION,
                    ReportField.USER_COMMENT,
                    ReportField.USER_APP_START_DATE,
                    ReportField.USER_CRASH_DATE,
                    ReportField.LOGCAT,
                    ReportField.INSTALLATION_ID,
                    ReportField.ENVIRONMENT,
                    ReportField.SHARED_PREFERENCES,
                    // ReportField.MEDIA_CODEC_LIST,
                    ReportField.THREAD_DETAILS,
                ).withLogcatArguments(*logcatArgs)
                .withPluginConfigurations(
                    DialogConfigurationBuilder()
                        .withReportDialogClass(AnkiDroidCrashReportDialog::class.java)
                        .withCommentPrompt(application.getString(R.string.empty_string))
                        .withTitle(application.getString(R.string.feedback_title))
                        .withText(application.getString(R.string.feedback_default_text))
                        .withPositiveButtonText(application.getString(R.string.feedback_report))
                        .withResIcon(R.drawable.logo_star_144dp)
                        .withEnabled(dialogEnabled)
                        .build(),
                    HttpSenderConfigurationBuilder()
                        .withHttpMethod(HttpSender.Method.PUT)
                        .withUri(BuildConfig.ACRA_URL)
                        .withEnabled(true)
                        .build(),
                    ToastConfigurationBuilder()
                        .withText(toastText)
                        .withEnabled(true)
                        .build(),
                    LimiterConfigurationBuilder()
                        .withExceptionClassLimit(1000)
                        .withStacktraceLimit(1)
                        .withDeleteReportsOnAppUpdate(true)
                        .withResetLimitsOnAppUpdate(true)
                        .withEnabled(true)
                        .build(),
                )
        ACRA.init(application, builder)
        acraCoreConfigBuilder = builder
        fetchWebViewInformation().let {
            ACRA.errorReporter.putCustomData(WEBVIEW_VER_NAME, it[WEBVIEW_VER_NAME] ?: "")
            ACRA.errorReporter.putCustomData("WEBVIEW_VER_CODE", it["WEBVIEW_VER_CODE"] ?: "")
        }
        return builder
    }

    /**
     * Use this method to initialize the ACRA CoreConfigurationBuilder in Application.onCreate().
     * The ACRA process needs a WebView for optimal UsageAnalytics values but it can't have the same
     * data directory. Analytics falls back to a sensible default if this is not set.
     */
    @JvmStatic
    fun initialize(application: Application) {
        this.application = application
        // FIXME ACRA needs to reinitialize after language is changed, but with the new language
        //   this is difficult because the Application (AnkiDroidApp) does not change it's baseContext
        //   perhaps a solution could be to change AnkiDroidApp to have a context wrapper that it sets
        //   as baseContext, and that wrapper allows a resources/configuration update, then
        //   in GeneralSettingsFragment for the language dialog change listener, the context wrapper
        //   could be updated directly with the new locale code so that calling getString on would fetch
        //   the new language string ?
        toastText = ToastType.AUTO_TOAST.getToastMessage(application)

        // Setup logging and crash reporting
        if (BuildConfig.DEBUG) {
            setDebugACRAConfig(application.sharedPrefs())
        } else {
            setProductionACRAConfig(application.sharedPrefs())
        }
        if (ACRA.isACRASenderServiceProcess() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                setDataDirectorySuffix("acra")
            } catch (e: java.lang.Exception) {
                Timber.w(e, "Failed to set WebView data directory")
            }
        }
    }

    /**
     * Set the reporting mode for ACRA based on the value of the FEEDBACK_REPORT_KEY preference
     * @param value value of FEEDBACK_REPORT_KEY preference
     */
    override fun setReportingMode(value: String) {
        application.sharedPrefs().edit {
            // Set the ACRA disable value
            if (value == FEEDBACK_REPORT_NEVER) {
                putBoolean(ACRA.PREF_DISABLE_ACRA, true)
            } else {
                putBoolean(ACRA.PREF_DISABLE_ACRA, false)
                // Switch between auto-report via toast and manual report via dialog
                if (value == FEEDBACK_REPORT_ALWAYS) {
                    dialogEnabled = false
                    toastText = ToastType.AUTO_TOAST.getToastMessage(application)
                } else if (value == FEEDBACK_REPORT_ASK) {
                    createAcraCoreConfigBuilder()
                    dialogEnabled = true
                    toastText = ToastType.MANUAL_TOAST.getToastMessage(application)
                }
                createAcraCoreConfigBuilder()
            }
        }
    }

    /**
     * Turns ACRA reporting off completely and persists it to shared prefs
     * But expands logcat search in case developer manually re-enables it
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setDebugACRAConfig(prefs: SharedPreferences) {
        // Disable crash reporting
        setReportingMode(FEEDBACK_REPORT_NEVER)
        prefs.edit { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_NEVER) }
        // Use a wider logcat filter in case crash reporting manually re-enabled
        logcatArgs = arrayOf("-t", "1500", "-v", "long", "ACRA:S")
        createAcraCoreConfigBuilder()
    }

    /**
     * Puts ACRA Reporting mode into user-specified mode, with default of "ask first"
     *
     * @param prefs SharedPreferences object the reporting state is persisted in
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setProductionACRAConfig(prefs: SharedPreferences) {
        // Enable or disable crash reporting based on user setting
        setReportingMode(prefs.getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK)!!)
    }

    private fun fetchWebViewInformation(): HashMap<String, String> {
        val webViewInfo = hashMapOf<String, String>()
        webViewInfo[WEBVIEW_VER_NAME] = ""
        webViewInfo["WEBVIEW_VER_CODE"] = ""
        try {
            val pi = WebViewCompat.getCurrentWebViewPackage(application)
            if (pi == null) {
                Timber.w("Could not get WebView package information")
                return webViewInfo
            }
            pi.versionName?.let { webViewInfo[WEBVIEW_VER_NAME] = it }
            webViewInfo["WEBVIEW_VER_CODE"] = PackageInfoCompat.getLongVersionCode(pi).toString()
        } catch (e: Throwable) {
            Timber.w(e)
        }
        return webViewInfo
    }

    /** Used when we don't have an exception to throw, but we know something is wrong and want to diagnose it  */
    override fun sendExceptionReport(
        message: String?,
        origin: String?,
    ) = sendExceptionReport(ManuallyReportedException(message), origin)

    override fun sendExceptionReport(
        e: Throwable,
        origin: String?,
        additionalInfo: String?,
        onlyIfSilent: Boolean,
    ) = sendExceptionReport(e, origin, additionalInfo, onlyIfSilent, application.applicationContext)

    override fun sendExceptionReport(
        e: Throwable,
        origin: String?,
        additionalInfo: String?,
        onlyIfSilent: Boolean,
        context: Context,
    ) {
        sendAnalyticsException(e, false)
        AnkiDroidApp.sentExceptionReportHack = true
        val reportMode =
            context
                .sharedPrefs()
                .getString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK)
        if (onlyIfSilent) {
            if (FEEDBACK_REPORT_ALWAYS != reportMode) {
                Timber.i("sendExceptionReport - onlyIfSilent true, but ACRA is not 'always accept'. Skipping report send.")
                return
            }
        }
        if (FEEDBACK_REPORT_NEVER != reportMode) {
            if (ThrowableFilterService.shouldDiscardThrowable(e)) return

            ACRA.errorReporter.putCustomData("origin", origin ?: "")
            ACRA.errorReporter.putCustomData("additionalInfo", additionalInfo ?: "")
            ACRA.errorReporter.handleException(e)
        }
    }

    fun isProperServiceProcess(): Boolean = ACRA.isACRASenderServiceProcess()

    override fun isEnabled(
        context: Context,
        defaultValue: Boolean,
    ): Boolean {
        if (!context.sharedPrefs().contains(ACRA.PREF_DISABLE_ACRA)) {
            // we shouldn't use defaultValue below, as it would be inverted which complicated understanding.
            Timber.w("No default value for '%s'", ACRA.PREF_DISABLE_ACRA)
            return defaultValue
        }
        return !context.sharedPrefs().getBoolean(ACRA.PREF_DISABLE_ACRA, true)
    }

    /**
     * If you want to make sure that the next exception of any time is posted, you need to clear limiter data
     *
     * @param context the context leading to the directory with ACRA limiter data
     */
    override fun deleteLimiterData(context: Context) {
        try {
            LimiterData().store(context)
        } catch (e: Exception) {
            Timber.w(e, "Unable to clear ACRA limiter data")
        }
    }

    override fun onPreferenceChanged(
        ctx: Context,
        newValue: String,
    ) {
        setReportingMode(newValue)
        // If the user changed error reporting, make sure future reports have a chance to post
        deleteLimiterData(ctx)
        // We also need to re-chain our UncaughtExceptionHandlers
        UsageAnalytics.reInitialize()
        ThrowableFilterService.reInitialize()
    }

    /**
     * @return the status of the report, true if the report was sent, false if the report is already
     *  submitted
     */
    override fun sendReport(activity: android.app.Activity): Boolean {
        val ankiActivity = activity as AnkiActivity
        val preferences = ankiActivity.sharedPrefs()
        val reportMode = preferences.getString(FEEDBACK_REPORT_KEY, "")
        return if (FEEDBACK_REPORT_NEVER == reportMode) {
            preferences.edit { putBoolean(ACRA.PREF_DISABLE_ACRA, false) }
            toastText = ToastType.MANUAL_TOAST.getToastMessage(application)
            createAcraCoreConfigBuilder()
            val sendStatus = sendReportFor(ankiActivity)
            dialogEnabled = false
            createAcraCoreConfigBuilder()
            preferences.edit { putBoolean(ACRA.PREF_DISABLE_ACRA, true) }
            sendStatus
        } else {
            sendReportFor(ankiActivity)
        }
    }

    private fun sendReportFor(activity: AnkiActivity): Boolean {
        val currentTimestamp = TimeManager.time.intTimeMS()
        val lastReportTimestamp = getTimestampOfLastReport(activity)
        return if (currentTimestamp - lastReportTimestamp > MIN_INTERVAL_MS) {
            deleteLimiterData(activity)
            sendExceptionReport(
                UserSubmittedException(EXCEPTION_MESSAGE),
                "AnkiDroidApp.HelpDialog",
            )
            true
        } else {
            false
        }
    }

    /**
     * Check the ACRA report store and return the timestamp of the last report.
     *
     * @param activity the Activity used for Context access when interrogating ACRA reports
     * @return the timestamp of the most recent report, or -1 if no reports at all
     */
    private fun getTimestampOfLastReport(activity: AnkiActivity): Long =
        LimiterData
            .load(activity)
            .reportMetadata
            .filter { it.exceptionClass == UserSubmittedException::class.java.name }
            .maxOfOrNull { it.timestamp?.timeInMillis ?: -1L } ?: -1L
}

/**
 * Initializes ACRA crash reporting and wires it up as the
 * global [CrashReportService] reporter.
 */
context(application: Application)
fun initializeAcraCrashReporter() {
    AcraCrashReporter.initialize(application)
    CrashReportService.setReporter(AcraCrashReporter)
}

fun isAcraSenderProcess(): Boolean = AcraCrashReporter.isProperServiceProcess()

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
val CrashReportService.acraCoreConfigBuilder: CoreConfigurationBuilder
    get() = AcraCrashReporter.acraCoreConfigBuilder

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
fun setDebugACRAConfig(sharedPrefs: SharedPreferences) = AcraCrashReporter.setDebugACRAConfig(sharedPrefs)

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
fun setProductionACRAConfig(sharedPrefs: SharedPreferences) = AcraCrashReporter.setProductionACRAConfig(sharedPrefs)
