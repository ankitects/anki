/*
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.webkit.CookieManager
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import anki.collection.OpChanges
import com.ichi2.anki.AnkiDroidApp.Companion.sharedPreferencesTestingOverride
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.common.android.ApplicationContextInitializer
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.LegacyNotifications
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService.sendExceptionReport
import com.ichi2.anki.common.permissions.hasLegacyStorageAccessPermission
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.logging.FragmentLifecycleLogger
import com.ichi2.anki.logging.LogType
import com.ichi2.anki.logging.ProductionCrashReportingTree
import com.ichi2.anki.logging.RobolectricDebugTree
import com.ichi2.anki.navigation.initializeNavigator
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.servicelayer.ThrowableFilterService
import com.ichi2.anki.services.AlarmManagerService
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.dialogs.ActivityAgnosticDialogs
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.ExceptionUtil
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.LanguageUtil.withAppLocale
import com.ichi2.utils.measureTime
import com.ichi2.utils.setWebContentsDebuggingEnabled
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.deckpicker.DeckPickerWidget
import com.ichi2.widget.restoreRecurringAlarms
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.util.Locale

/**
 * Application class.
 */
@KotlinCleanup("IDE Lint")
open class AnkiDroidApp :
    Application(),
    ChangeManager.Subscriber {
    /** An exception if AnkiDroidApp fails to load  */
    private var fatalInitializationError: FatalInitializationError? = null

    @LegacyNotifications("The widget triggers notifications by posting null to this, but we plan to stop relying on the widget")
    private val notifications = MutableLiveData<Void?>()

    lateinit var activityAgnosticDialogs: ActivityAgnosticDialogs
    val sharedPrefsLastDeckIdRepository = SharedPreferencesLastDeckIdRepository()

    /** Used to avoid showing extra progress dialogs when one already shown. */
    var progressDialogShown = false

    /**
     * Executes a setup method: [block], logging execution time.
     * Exceptions are logged and rethrown.
     *
     * @param methodName Method name, used for logging.
     * @param block The method to execute and return
     */
    // 'inline fun' so logs use the correct context
    inline fun <T> setup(
        methodName: String,
        crossinline block: () -> T,
    ): T {
        // TODO: #20168 warn users of non-fatal component errors rather than rethrowing
        try {
            return measureTime(methodName = methodName) { block() }
        } catch (e: Exception) {
            // NOTE: this can be called before Timber is initialized
            Timber.w(e, "failed to execute $methodName")
            throw e
        }
    }

    /**
     * On application creation.
     */
    @KotlinCleanup("analytics can be moved to attachBaseContext()")
    override fun onCreate() {
        initAnkiBackend(debugTraceSqlCalls = false)
        super.onCreate()
        val appLifecycleObserver = AppLifecycleObserver(applicationContext)

        androidx.lifecycle.ProcessLifecycleOwner
            .get()
            .lifecycle
            .addObserver(appLifecycleObserver)
        if (isInitialized) {
            Timber.i("onCreate() called multiple times")
            // 5887 - fix crash.
            if (instance.resources == null) {
                Timber.w("Skipping re-initialisation - no resources. Maybe uninstalling app?")
                return
            }
        }
        instance = this
        ApplicationContextInitializer.setInstance(this)

        // Ensures any change is propagated to widgets
        ChangeManager.subscribe(this)

        initializeAcraCrashReporter()
        initializeNavigator()
        val logType = LogType.value
        when (logType) {
            LogType.DEBUG -> Timber.plant(DebugTree())
            LogType.ROBOLECTRIC -> Timber.plant(RobolectricDebugTree())
            LogType.PRODUCTION -> Timber.plant(ProductionCrashReportingTree())
        }
        if (BuildConfig.ENABLE_LEAK_CANARY) {
            LeakCanaryConfiguration.setInitialConfigFor(this)
        } else {
            LeakCanaryConfiguration.disable()
        }
        Timber.tag(TAG)
        Timber.d("Startup - Application Start")
        Timber.i("Timber config: $logType")

        // analytics after ACRA, they both install UncaughtExceptionHandlers but Analytics chains while ACRA does not
        UsageAnalytics.initialize(this)
        if (BuildConfig.DEBUG) {
            UsageAnalytics.setDryRun(true)
        }

        // Last in the UncaughtExceptionHandlers chain is our filter service
        ThrowableFilterService.initialize()

        applicationScope.launch {
            Timber.i("AnkiDroidApp: listing debug info")
            Timber.i(DebugInfoService.getDebugInfo(this@AnkiDroidApp))
        }

        // Stop after analytics and logging are initialised.
        if (isAcraSenderProcess()) {
            Timber.d("Skipping AnkiDroidApp.onCreate from ACRA sender process")
            return
        }
        if (AdaptionUtil.isUserATestClient) {
            showThemedToast(this.applicationContext, getString(R.string.user_is_a_robot), false)
        }

        setWebContentsDebuggingEnabled(Prefs.isWebDebugEnabled)

        setupContextMenus()

        setup("makeBackendUsable") { makeBackendUsable(this) }
        setupNotifications()

        // Probe WebView availability before any other init touches it (#5794).
        if (!checkWebViewAvailable()) {
            return
        }

        // Forget the last deck that was used in the CardBrowser
        CardBrowser.clearLastDeckId()
        LanguageUtil.setDefaultBackendLanguages()

        initializeAnkiDroidDirectory()

        // listen for day rollover: time + timezone changes
        DayRolloverHandler.listenForRolloverEvents(this)
        DayRolloverAlarm.scheduleNext(this)

        restoreRecurringAlarms(this)

        setupLifecycleLogging()
        activityAgnosticDialogs = ActivityAgnosticDialogs.register(this)
        TtsVoices.launchBuildLocalesJob()
        // enable {{tts-voices:}} field filter
        TtsVoicesFieldFilter.ensureApplied()
    }

    /**
     * @param debugTraceSqlCalls Log all SQL statements executed by the backend.
     * **Warning** The log may be delayed by 100ms, so you should not assume than a given SQL
     * statement has run after a Timber.* line just because the SQL statement appeared later.
     */
    private fun initAnkiBackend(
        @Suppress("SameParameterValue") debugTraceSqlCalls: Boolean = false,
    ) {
        runCatching {
            setup(methodName = "initAnkiBackend") {
                // Note: This method runs before logs are enabled.
                Os.setenv("PLATFORM", syncPlatform(), false)
                // enable debug logging of sync actions
                if (BuildConfig.DEBUG) {
                    Os.setenv("RUST_LOG", "info,anki::sync=debug,anki::media=debug,fsrs=error", false)
                }

                if (debugTraceSqlCalls) {
                    Os.setenv("TRACESQL", "1", false)
                }
            }
        }
    }

    /**
     * Manually initializes the collection directory and `.nomedia` if
     * [hasLegacyStorageAccessPermission] is set
     *
     * On failure, sets [fatalInitializationError] to [storageError][FatalInitializationError.StorageError]
     *
     * In most cases the Anki Backend now creates the collection and [initializeAnkiDroidDirectory]
     *  is called on startup of the activity.
     */
    private fun initializeAnkiDroidDirectory() =
        setup("initializeAnkiDroidDirectory") {
            // #13207: `getCurrentAnkiDroidDirectory` failing is an unconditional be a fatal error
            // TODO: For now, a null getExternalFilesDir, but a valid AnkiDroid Directory in prefs
            //  is not considered to be a fatal error, unless the directory itself is not writable.
            val ankiDroidDir =
                try {
                    CollectionHelper.getCurrentAnkiDroidDirectory(this)
                } catch (e: SystemStorageException) {
                    fatalInitializationError = FatalInitializationError.StorageError(e)
                    return@setup
                }

            // TODO: This line is questionable, as it doesn't work on most post-scoped-storage
            //  builds/Android versions, but we call initializeAnkiDroidDirectory later on startup
            if (!hasLegacyStorageAccessPermission(this)) return@setup

            try {
                CollectionHelper.initializeAnkiDroidDirectory(ankiDroidDir)
                return@setup
            } catch (e: StorageAccessException) {
                Timber.e(e, "Could not initialize AnkiDroid directory")
                try {
                    val defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory(this)
                    if (SdCard.isMounted && CollectionHelper.getCurrentAnkiDroidDirectory(this) == defaultDir) {
                        // Don't send report if the user is using a custom directory as SD cards trip up here a lot
                        sendExceptionReport(e, "AnkiDroidApp.onCreate")
                    }
                } catch (e: SystemStorageException) {
                    // The user can't write to the AnkiDroid directory (=> cant write to the collection)
                    // AND getExternalFilesDir is null - file permissions are likely corrupted (Android 16 bug)
                    // => show the 'fatal storage error' screen
                    fatalInitializationError = FatalInitializationError.StorageError(e)
                }
            }
        }

    /**
     * Sets up display of the context menus which appear when long pressing text on external apps,
     * allowing it to be shared to this app.
     *
     * Example: 'Anki Card'
     *
     * @see Intent.ACTION_PROCESS_TEXT
     */
    private fun setupContextMenus() =
        setup("setupContextMenus") {
            val preferences = this.sharedPrefs()

            // setup 'Card Browser'
            CardBrowserContextMenu.ensureConsistentStateWithPreferenceStatus(
                this,
                preferences.getBoolean(
                    getString(R.string.card_browser_external_context_menu_key),
                    false,
                ),
            )

            // Setup 'Anki Card'
            AnkiCardContextMenu.ensureConsistentStateWithPreferenceStatus(
                this,
                preferences.getBoolean(getString(R.string.anki_card_external_context_menu_key), true),
            )
        }

    private fun setupNotifications() =
        setup("setupNotifications") {
            setupNotificationChannels(applicationContext)

            val context = this.withAppLocale()
            if (Prefs.newReviewRemindersEnabled) {
                Timber.i("Setting review reminder notifications if they have not already been set")
                AlarmManagerService.scheduleAllNotifications(context)
            } else {
                // Register for notifications
                Timber.i("AnkiDroidApp: Starting Services")
                notifications.observeForever { NotificationService.triggerNotificationFor(context) }
            }
        }

    private fun setupLifecycleLogging() =
        setup("setupLifecycleLogging") {
            registerActivityLifecycleCallbacks(
                object : ActivityLifecycleCallbacks {
                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?,
                    ) {
                        Timber.i(
                            "${activity::class.simpleName}::onCreate, savedInstanceState: %s",
                            savedInstanceState?.let { "${it.keySet().size} keys" },
                        )
                        (activity as? FragmentActivity)
                            ?.supportFragmentManager
                            ?.registerFragmentLifecycleCallbacks(
                                FragmentLifecycleLogger(activity),
                                true,
                            )
                    }

                    override fun onActivityStarted(activity: Activity) {
                        Timber.i("${activity::class.simpleName}::onStart")
                    }

                    override fun onActivityResumed(activity: Activity) {
                        Timber.i("${activity::class.simpleName}::onResume")
                    }

                    override fun onActivityPaused(activity: Activity) {
                        Timber.i("${activity::class.simpleName}::onPause")
                    }

                    override fun onActivityStopped(activity: Activity) {
                        Timber.i("${activity::class.simpleName}::onStop")
                    }

                    override fun onActivitySaveInstanceState(
                        activity: Activity,
                        outState: Bundle,
                    ) {
                        Timber.i("${activity::class.simpleName}::onSaveInstanceState")
                    }

                    override fun onActivityDestroyed(activity: Activity) {
                        Timber.i("${activity::class.simpleName}::onDestroy")
                    }
                },
            )
        }

    /**
     * @return the app version, OS version and device model, provided when syncing.
     */
    private fun syncPlatform(): String {
        // AnkiWeb reads this string and uses , and : as delimiters, so we remove them.
        val model = Build.MODEL.replace(',', ' ').replace(':', ' ')
        return String.format(
            Locale.US,
            "android:%s:%s:%s",
            BuildConfig.VERSION_NAME,
            Build.VERSION.RELEASE,
            model,
        )
    }

    @LegacyNotifications("Only used by the widget to trigger notifications, we plan to stop relying on the widget")
    fun scheduleNotification() {
        notifications.postValue(null)
    }

    /**
     * Checks that [android.webkit.WebView] is usable.
     */
    protected fun checkWebViewAvailable(): Boolean =
        try {
            CookieManager.getInstance()
            true
        } catch (e: Throwable) {
            // 5794: Errors occur if the WebView fails to load
            // android.webkit.WebViewFactory.MissingWebViewPackageException.MissingWebViewPackageException
            // Error may be excessive, but I expect a UnsatisfiedLinkError to be possible here.
            fatalInitializationError = FatalInitializationError.WebViewError(e)
            sendExceptionReport(e, "checkWebViewAvailable")
            Timber.e(e, "checkWebViewAvailable")
            false
        }

    /**
     * Callback method invoked when operations that affect the app state are executed.
     * If relevant changes related to the study queues are detected, the Deck Picker Widgets
     * are updated accordingly.
     *
     * @param changes The set of changes that occurred.
     * @param handler An optional handler that can be used for custom processing (unused here).
     */
    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        Timber.d("ChangeSubscriber - opExecuted called with changes: %s", changes)
        if (changes.studyQueues) {
            DeckPickerWidget.updateDeckPickerWidgets(this)
            CardAnalysisWidget.updateCardAnalysisWidgets(this)
        } else {
            Timber.d("No relevant changes to update the widget")
        }
    }

    companion object {
        /**
         * A [SharedPreferencesProvider] which does not require [onCreate] when run from tests
         *
         * @see sharedPreferencesTestingOverride
         */
        val sharedPreferencesProvider get() = SharedPreferencesProvider { sharedPrefs() }

        /** Running under instrumentation. a "/androidTest" directory will be created which contains a test collection  */
        @Suppress("ktlint:standard:property-naming")
        var INSTRUMENTATION_TESTING = false
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        // Tag for logging messages.
        const val TAG = "AnkiDroid"

        /** Singleton instance of this class.
         * Note: this may not be initialized if AnkiDroid is run via BackupManager
         */
        lateinit var instance: AnkiDroidApp
            private set

        /**
         * An override for Shared Preferences to use for unit tests
         *
         * This does not depend on an instance of AnkiDroidApp and therefore has no Android
         * implementations
         */
        @VisibleForTesting
        var sharedPreferencesTestingOverride: SharedPreferences? = null

        /**
         * A test-friendly accessor to Shared Preferences.
         *
         * In tests, this can avoid an instance of `AnkiDroidApp`, which is slow
         * This was added to avoid code churn
         */
        fun sharedPrefs() = sharedPreferencesTestingOverride ?: instance.sharedPrefs()

        /** HACK: Whether an exception report has been thrown - TODO: Rewrite an ACRA Listener to do this  */
        @VisibleForTesting
        var sentExceptionReportHack = false

        @get:JvmName("isInitialized")
        val isInitialized: Boolean
            get() = this::instance.isInitialized

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun simulateRestoreFromBackup() {
            val field = AnkiDroidApp::class.java.getDeclaredField("instance")

            with(field) {
                isAccessible = true
                set(field, null)
            }
            // Mirror reality: when AnkiDroidApp.onCreate doesn't run (the bmgr-restore
            // scenario), appContext is also uninitialized.
            ApplicationContextInitializer.clearForTesting()
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun internalSetInstanceValue(value: AnkiDroidApp) {
            val field = AnkiDroidApp::class.java.getDeclaredField("instance")

            with(field) {
                isAccessible = true
                set(field, value)
            }
            // Production code (AnkiDroidApp.onCreate) sets appContext
            // right after AnkiDroidApp.instance. Mirror that in tests so callers using the
            // common-side accessor see the same mock.
            ApplicationContextInitializer.setInstance(value)
        }

        /** Load the libraries to allow access to Anki-Android-Backend */
        @NeedsTest("Not calling this in the ContentProvider should have failed a test")
        fun makeBackendUsable(context: Context) {
            // Robolectric uses RustBackendLoader.ensureSetup()
            if (Build.FINGERPRINT == "robolectric") return

            // Prevent sqlite throwing error 6410 due to the lack of /tmp on Android
            Os.setenv("TMPDIR", context.cacheDir.path, false)
            // Load backend library
            System.loadLibrary("rsdroid")
        }

        val appResources: Resources
            get() = instance.resources

        fun getMarketIntent(context: Context): Intent {
            val uri =
                context.getString(if (CompatHelper.isKindle) R.string.link_market_kindle else R.string.link_market)
            val parsed = uri.toUri()
            return Intent(Intent.ACTION_VIEW, parsed)
        } // TODO actually this can be done by translating "link_help" string for each language when the App is

        @VisibleForTesting
        fun clearFatalError() {
            this.instance.fatalInitializationError = null
        }

        /**
         * Get the url for the properly translated feedback page
         * @return
         */
        val feedbackUrl: String
            get() = // TODO actually this can be done by translating "link_help" string for each language when the App is
                // properly translated
                when (LanguageUtil.getCurrentLocaleTag()) {
                    "ja" -> appResources.getString(R.string.link_help_ja)
                    "zh" -> appResources.getString(R.string.link_help_zh)
                    "ar" -> appResources.getString(R.string.link_help_ar)
                    else -> appResources.getString(R.string.link_help)
                } // TODO actually this can be done by translating "link_manual" string for each language when the App is

        /**
         * Get the url for the properly translated manual
         * @return
         */
        val manualUrl: String
            get() = // TODO actually this can be done by translating "link_manual" string for each language when the App is
                // properly translated
                when (LanguageUtil.getCurrentLocaleTag()) {
                    "ja" -> appResources.getString(R.string.link_manual_ja)
                    "zh" -> appResources.getString(R.string.link_manual_zh)
                    "ar" -> appResources.getString(R.string.link_manual_ar)
                    else -> appResources.getString(R.string.link_manual)
                }

        /** (optional) set if an unrecoverable error occurs during Application startup */
        val fatalError: FatalInitializationError?
            get() = instance.fatalInitializationError
    }
}

/**
 * Types of unrecoverable errors which we want to inform the user of
 */
sealed class FatalInitializationError {
    data class WebViewError(
        val error: Throwable,
    ) : FatalInitializationError()

    data class StorageError(
        val error: SystemStorageException,
    ) : FatalInitializationError()

    /** Advanced/developer-facing string representing the error */
    val errorDetail: String
        get() =
            when (this) {
                is WebViewError -> ExceptionUtil.getExceptionMessage(error)
                is StorageError -> error.message
            }

    val infoLink: Uri?
        get() =
            when (this) {
                is WebViewError -> null
                is StorageError -> error.infoUri?.toUri()
            }
}
