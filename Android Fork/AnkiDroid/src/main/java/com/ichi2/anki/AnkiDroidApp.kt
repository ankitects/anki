// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.webkit.CookieManager
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import anki.collection.OpChanges
import com.ichi2.anki.analytics.initializeAnalytics
import com.ichi2.anki.analytics.launchPreviousAnrReporting
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.common.android.ApplicationContextInitializer
import com.ichi2.anki.common.android.getCurrentLocaleTag
import com.ichi2.anki.common.android.withAppLocale
import com.ichi2.anki.common.annotations.LegacyNotifications
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.appName
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService.sendExceptionReport
import com.ichi2.anki.common.permissions.hasLegacyStorageAccessPermission
import com.ichi2.anki.common.preferences.SharedPreferencesTestHook
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.logging.FragmentLifecycleLogger
import com.ichi2.anki.logging.LogType
import com.ichi2.anki.logging.ProductionCrashReportingTree
import com.ichi2.anki.logging.RobolectricDebugTree
import com.ichi2.anki.logging.logActivityCreation
import com.ichi2.anki.model.FieldFilters.NoSuggestFilter
import com.ichi2.anki.multimedia.MultimediaArgsStorage
import com.ichi2.anki.multiprofile.ProfileContextWrapper
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.isPhoenixProcess
import com.ichi2.anki.navigation.initializeNavigator
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.recorder.VoiceRecorder
import com.ichi2.anki.reviewreminders.ReminderLogTree
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.servicelayer.ThrowableFilterService
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.startup.FatalInitializationError
import com.ichi2.anki.startup.StartupErrors
import com.ichi2.anki.startup.configureBackendTemporaryDirectory
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.anki.startup.maybeSetDefaultCollectionPath
import com.ichi2.anki.ui.dialogs.ActivityAgnosticDialogs
import com.ichi2.anki.widget.RECURRING_WIDGETS
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.measureTime
import com.ichi2.utils.setWebContentsDebuggingEnabled
import com.ichi2.widget.AnkiDroidWidgetSmall
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.WidgetNotificationScheduler
import com.ichi2.widget.WidgetStatus
import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.deckpicker.DeckPickerWidget
import com.ichi2.widget.restoreRecurringAlarms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.File
import java.util.Locale

/**
 * Application class.
 */
@KotlinCleanup("IDE Lint")
open class AnkiDroidApp :
    Application(),
    ChangeManager.Subscriber {
    private val startupErrors = StartupErrors()

    private var appLifecycleObserver: AppLifecycleObserver? = null

    @LegacyNotifications("The widget triggers notifications by posting null to this, but we plan to stop relying on the widget")
    private val notifications = MutableLiveData<Void?>()

    lateinit var activityAgnosticDialogs: ActivityAgnosticDialogs
    val sharedPrefsLastDeckIdRepository = SharedPreferencesLastDeckIdRepository()

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

    @set:VisibleForTesting
    var profileManager: ProfileManager? = null

    private val profileContext: ProfileContextWrapper?
        get() = profileManager?.activeProfileContext

    /**
     * Runs before [onCreate] and before any ContentProvider, which is what the
     * profile environment needs: WebView data directories must be set before any
     * WebView exists, and every preference read must already be namespaced.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (isAcraSenderProcess() || isPhoenixProcess()) return
        profileManager = ProfileManager.createOrNull(base)
    }

    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences = profileContext?.getSharedPreferences(name, mode) ?: super.getSharedPreferences(name, mode)

    override fun getFilesDir(): File = profileContext?.filesDir ?: super.getFilesDir()

    override fun getCacheDir(): File = profileContext?.cacheDir ?: super.getCacheDir()

    override fun getCodeCacheDir(): File = profileContext?.codeCacheDir ?: super.getCodeCacheDir()

    override fun getNoBackupFilesDir(): File = profileContext?.noBackupFilesDir ?: super.getNoBackupFilesDir()

    override fun getDatabasePath(name: String): File = profileContext?.getDatabasePath(name) ?: super.getDatabasePath(name)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
    ): SQLiteDatabase = profileContext?.openOrCreateDatabase(name, mode, factory) ?: super.openOrCreateDatabase(name, mode, factory)

    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?,
    ): SQLiteDatabase =
        profileContext?.openOrCreateDatabase(name, mode, factory, errorHandler)
            ?: super.openOrCreateDatabase(name, mode, factory, errorHandler)

    override fun deleteDatabase(name: String): Boolean = profileContext?.deleteDatabase(name) ?: super.deleteDatabase(name)

    override fun getDir(
        name: String,
        mode: Int,
    ): File = profileContext?.getDir(name, mode) ?: super.getDir(name, mode)

    /**
     * On application creation, i.e. when the application process starts.
     * This is called before any activities, services, or receivers are created.
     */
    @KotlinCleanup("analytics can be moved to attachBaseContext()")
    override fun onCreate() {
        if (isPhoenixProcess()) {
            super.onCreate()
            return
        }
        initAnkiBackend(debugTraceSqlCalls = false)
        super.onCreate()
        if (!setupAnkiDroidApp()) {
            return
        }

        val crashReporting = CrashReportingContext.apply { setupCrashReporting() }
        setupApplicationDependencies()
        with(crashReporting) {
            setupLogging()
            setupUsageAnalytics()
            launchStartupLogging()
        }

        // Stop after analytics and logging are initialised.
        if (isAcraSenderProcess()) {
            Timber.d("Skipping AnkiDroidApp.onCreate from ACRA sender process")
            return
        }
        setupMainProcessMaintenance()
        setupContextMenus()

        setup("makeBackendUsable") { makeBackendUsable(this) }
        setupNotifications()
        setupAppLifecycleObserver()
        setupBackendChangeManager()

        // Probe WebView availability before any other init touches it (#5794).
        if (!setupWebView()) {
            return
        }

        setupCardBrowser()
        val anki = AnkiContext.apply { setupAnkiBackend() }
        with(anki) { initializeAnkiDroidDirectory() }
        with(anki) { setupDayRollover() }
        setupRecurringAlarms()

        setupLifecycleLogging()
        setupActivityAgnosticDialogs()
        setupTextToSpeech()
        setupCustomFieldFilters()
    }

    override fun onTerminate() {
        // WARN: onTerminate is not called on production Android devices, only emulated environments

        // Robolectric creates an application per test, but ProcessLifecycleOwner survives between tests.
        // It never emits ON_DESTROY, so remove the observer here to release this application.
        appLifecycleObserver?.let { observer ->
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        }
        appLifecycleObserver = null
        super.onTerminate()
    }

    /** Schedules temporary-file maintenance and logs its duration without delaying startup. */
    @VisibleForTesting
    internal fun launchCacheCleanup() =
        applicationScope.launch(Dispatchers.IO) {
            measureTime("cacheCleanup") {
                runCacheCleanup("multimediaArgsCleanup") {
                    MultimediaArgsStorage.create(this@AnkiDroidApp).removeExpiredFiles()
                }
                runCacheCleanup("voiceRecordingsCleanup") {
                    VoiceRecorder.removeExpiredRecordings(VoiceRecorder.recordingsDirectory(this@AnkiDroidApp))
                }
                runCacheCleanup("idsFileCleanup") {
                    IdsFile.deleteStaleFiles(this@AnkiDroidApp)
                }
            }
        }

    private inline fun runCacheCleanup(
        name: String,
        block: () -> Unit,
    ) {
        runCatching { measureTime(name, block) }
            .onFailure { Timber.w(it, "Unable to run %s", name) }
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
     * Sets [isInitialized] to `true` ([instance] != null)
     *
     * [onCreate] can be called multiple times due to ACRA using a separate sender process
     *
     * @return false if `instance.resources` is unusable
     */
    private fun setupAnkiDroidApp(): Boolean {
        return setup("setupAnkiDroidApp") {
            if (isInitialized) {
                Timber.i("onCreate() called multiple times")
                // 5887 - fix crash.
                if (instance.resources == null) {
                    Timber.w("Skipping re-initialisation - no resources. Maybe uninstalling app?")
                    return@setup false
                }
            }
            instance = this
            ApplicationContextInitializer.setInstance(this)
            appName = getString(R.string.app_name)
            true
        }
    }

    private fun setupCrashReporting() {
        setup("setupCrashReporting") {
            initializeAcraCrashReporter()
        }
    }

    /** Registers the application-specific implementations used by shared modules. */
    private fun setupApplicationDependencies() {
        setup("setupApplicationDependencies") {
            initializeNavigator()
            initializeWidgetRepository()
            initializeDatabaseErrorDialogs()
            initializeActivitySetup()
            WidgetNotificationScheduler.register { scheduleNotification() }
            WidgetStatus.register(Prefs) { context -> AnkiDroidWidgetSmall.UpdateService().doUpdate(context) }
            Animations.setPreferencesProvider { context -> PrefsRepository(context) }
            CollectionLockedException.messageProvider = {
                getString(CommonString.database_locked_summary_new, getString(CommonString.col_path))
            }
            ChangeManager.throwOnDuplicateSubscription = BuildConfig.DEBUG
        }
    }

    // Keep crash reporting ahead of logging setup so failures while planting trees or configuring
    // LeakCanary can be reported.
    context(_: CrashReportingContext)
    private fun setupLogging() {
        setup("setupLogging") {
            val logType = LogType.value
            when (logType) {
                LogType.DEBUG -> Timber.plant(DebugTree())
                LogType.ROBOLECTRIC -> Timber.plant(RobolectricDebugTree())
                LogType.PRODUCTION -> Timber.plant(ProductionCrashReportingTree())
            }
            Timber.plant(ReminderLogTree(this))
            if (BuildConfig.ENABLE_LEAK_CANARY && !isRobolectric) {
                LeakCanaryConfiguration.setInitialConfigFor(this)
            } else {
                LeakCanaryConfiguration.disable()
            }
            Timber.tag(TAG)
            Timber.d("Startup - Application Start")
            Timber.i("Timber config: $logType")
        }
    }

    context(_: CrashReportingContext)
    private fun setupUsageAnalytics() {
        setup("setupUsageAnalytics") {
            // Analytics chains UncaughtExceptionHandlers, while ACRA does not, so initialize ACRA first.
            initializeAnalytics()
            // Install the filter last so it sanitizes exceptions before either reporting handler sees them.
            ThrowableFilterService.initialize()
        }
    }

    /**
     * Launches startup diagnostics after Timber is configured.
     *
     * [DebugInfoService.getDebugInfo] reads `CrashReportService.isEnabled()` to include the
     * crash-reporting status. Before the reporter is initialized, that read warns and returns
     * the default `false`, so crash reporting must be set up before collecting the diagnostics.
     */
    context(_: CrashReportingContext)
    private fun launchStartupLogging() {
        setup("launchStartupLogging") {
            applicationScope.launch {
                Timber.i("AnkiDroidApp: listing debug info")
                Timber.i(DebugInfoService.getDebugInfo(this@AnkiDroidApp))
            }
        }
    }

    /** Starts maintenance and diagnostics which must not run in the ACRA sender process. */
    private fun setupMainProcessMaintenance() {
        setup("setupMainProcessMaintenance") {
            launchPreviousAnrReporting()
            ProfileManager.attachError?.let {
                Timber.w(it, "Failed to load the profile environment, running on the base context")
            }
            launchCacheCleanup()
            if (AdaptionUtil.isUserATestClient) {
                showThemedToast(applicationContext, getString(CommonString.user_is_a_robot), false)
            }
        }
    }

    /**
     * Manually initializes the collection directory and `.nomedia` if
     * [hasLegacyStorageAccessPermission] is set
     *
     * On failure, records a [storage error][FatalInitializationError.StorageError] in [startupErrors].
     *
     * In most cases the Anki Backend now creates the collection and [initializeAnkiDroidDirectory]
     *  is called on startup of the activity.
     */
    context(_: AnkiContext)
    private fun initializeAnkiDroidDirectory() =
        setup("initializeAnkiDroidDirectory") {
            // #13207: `getCurrentAnkiDroidDirectory` failing is an unconditional be a fatal error
            // TODO: For now, a null getExternalFilesDir, but a valid AnkiDroid Directory in prefs
            //  is not considered to be a fatal error, unless the directory itself is not writable.
            val ankiDroidDir =
                try {
                    maybeSetDefaultCollectionPath(this)
                    if (CollectionHelper.storageDecision(this.sharedPrefs()) != StorageDecision.Decided) {
                        // a fresh install without access to public storage: the user
                        // decides via the permission screen (see maybeSetDefaultCollectionPath)
                        Timber.i("skipping AnkiDroid directory init: storage is undecided")
                        return@setup
                    }
                    CollectionHelper.getCurrentAnkiDroidDirectory(this)
                } catch (e: SystemStorageException) {
                    startupErrors.recordFatalError(FatalInitializationError.StorageError(e))
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
                    val defaultDir = getDefaultAnkiDroidDirectory(this)
                    if (SdCard.isMounted && CollectionHelper.getCurrentAnkiDroidDirectory(this) == defaultDir) {
                        // Don't send report if the user is using a custom directory as SD cards trip up here a lot
                        sendExceptionReport(e, "AnkiDroidApp.onCreate")
                    }
                } catch (e: SystemStorageException) {
                    // The user can't write to the AnkiDroid directory (=> cant write to the collection)
                    // AND getExternalFilesDir is null - file permissions are likely corrupted (Android 16 bug)
                    // => show the 'fatal storage error' screen
                    startupErrors.recordFatalError(FatalInitializationError.StorageError(e))
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
            // In the future, if more notifications are added to AnkiDroid, scheduleAllNotifications
            // should be extended to handle them, but since for now the only modernized notifications are review reminder
            // notifications, we block this behind the review-reminder-specific feature flag.
            if (Prefs.newReviewRemindersEnabled) {
                Timber.i("Setting notifications if they have not already been set")
                applicationScope.launch {
                    scheduleAllNotifications(context)
                }
            } else {
                // Register for notifications
                Timber.i("AnkiDroidApp: Starting Services")
                notifications.observeForever { NotificationService.triggerNotificationFor(context) }
            }
        }

    private fun setupAppLifecycleObserver() =
        setup("setupAppLifecycleObserver") {
            appLifecycleObserver =
                AppLifecycleObserver(applicationContext).also { observer ->
                    ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
                }
        }

    /**
     * Ensures any changes in the backend are propagated to:
     *
     * - widgets
     *
     * @see opExecuted
     * @see ChangeManager
     */
    private fun setupBackendChangeManager() =
        setup("setupBackendChangeManager") {
            ChangeManager.subscribe(this)
        }

    /** Configures WebView and records a fatal initialization error if it is unavailable. */
    private fun setupWebView(): Boolean =
        setup("setupWebView") {
            setWebContentsDebuggingEnabled(Prefs.isWebDebugEnabled)
            checkWebViewAvailable()
        }

    private fun setupCardBrowser() {
        setup("setupCardBrowser") {
            // Forget the last deck that was used in the CardBrowser.
            CardBrowser.clearLastDeckId()
        }
    }

    private fun setupAnkiBackend() =
        setup("setupAnkiBackend") {
            LanguageUtil.setDefaultBackendLanguages()
        }

    /** Listen for day rollover: time + timezone changes and refresh on the day cutoff. */
    context(_: AnkiContext)
    private fun setupDayRollover() =
        setup("setupDayRollover") {
            DayRolloverHandler.listenForRolloverEvents(this)
            DayRolloverAlarm.scheduleNext(this)
        }

    private fun setupRecurringAlarms() {
        setup("setupRecurringAlarms") {
            restoreRecurringAlarms(this, RECURRING_WIDGETS)
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
                        activity.logActivityCreation(savedInstanceState)
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

    private fun setupActivityAgnosticDialogs() {
        setup("setupActivityAgnosticDialogs") {
            activityAgnosticDialogs = ActivityAgnosticDialogs.register(this)
        }
    }

    private fun setupTextToSpeech() {
        setup("setupTextToSpeech") {
            TtsVoices.launchBuildLocalesJob()
        }
    }

    /**
     * Applies AnkiDroid-specific implementations of field filters
     *
     * @see com.ichi2.anki.model.FieldFilter
     * @see com.ichi2.anki.model.FieldFilters
     */
    private fun setupCustomFieldFilters() {
        setup("setupCustomFieldFilters") {
            // enable {{tts-voices:}} field filter
            TtsVoicesFieldFilter.ensureApplied()
            // enable {{nosuggest:type:}} field filter (issue #10352)
            NoSuggestFilter.ensureApplied()
        }
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
            startupErrors.recordFatalError(FatalInitializationError.WebViewError(e))
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

    /**
     * Initialization for the Anki Backend has completed:
     * - [initAnkiBackend] - platform environment variables/logging
     * - [makeBackendUsable] - load rsdroid.so
     * - [setupBackendChangeManager] - change manager is subscribed
     * - [setupAnkiBackend] - i18n is set up
     */

    object AnkiContext

    /** Crash reporting is initialized for dependent diagnostics and to capture later startup failures. */
    private object CrashReportingContext

    companion object {
        /**
         * A [SharedPreferencesProvider] which does not require [onCreate] when run from tests
         *
         * @see SharedPreferencesTestHook
         */
        val sharedPreferencesProvider get() = SharedPreferencesProvider { sharedPrefs() }

        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

        // Tag for logging messages.
        const val TAG = "AnkiDroid"

        /** Singleton instance of this class.
         * Note: this may not be initialized if AnkiDroid is run via BackupManager
         */
        lateinit var instance: AnkiDroidApp
            private set

        /** Resource counterpart to [SharedPreferencesTestHook], scoped by JVM test rules. */
        @VisibleForTesting
        var resourcesTestingOverride: Resources? = null

        /**
         * A test-friendly accessor to Shared Preferences.
         *
         * In tests, this can avoid an instance of `AnkiDroidApp`, which is slow
         * This was added to avoid code churn
         */
        fun sharedPrefs() = SharedPreferencesTestHook.override ?: instance.sharedPrefs()

        /**
         * [sharedPrefs], or `null` if unavailable: [instance] is not initialized when running
         * under a test-only [Application] (e.g. `EmptyApplication`) or in pure JVM tests
         */
        fun sharedPrefsOrNull(): SharedPreferences? =
            SharedPreferencesTestHook.override ?: if (isInitialized) instance.sharedPrefs() else null

        fun wrapForActiveProfile(base: Context): Context = (base.applicationContext as? AnkiDroidApp)?.profileManager?.wrap(base) ?: base

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
            configureBackendTemporaryDirectory(context)
            // Load backend library
            System.loadLibrary("rsdroid")
        }

        val appResources: Resources
            get() = resourcesTestingOverride ?: instance.resources

        fun getMarketIntent(context: Context): Intent {
            val uri =
                context.getString(if (CompatHelper.isKindle) R.string.link_market_kindle else R.string.link_market)
            val parsed = uri.toUri()
            return Intent(Intent.ACTION_VIEW, parsed)
        } // TODO actually this can be done by translating "link_help" string for each language when the App is

        /**
         * Get the url for the properly translated feedback page
         * @return
         */
        val feedbackUrl: String
            get() = // TODO actually this can be done by translating "link_help" string for each language when the App is
                // properly translated
                when (getCurrentLocaleTag()) {
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
                when (getCurrentLocaleTag()) {
                    "ja" -> appResources.getString(R.string.link_manual_ja)
                    "zh" -> appResources.getString(R.string.link_manual_zh)
                    "ar" -> appResources.getString(R.string.link_manual_ar)
                    else -> appResources.getString(R.string.link_manual)
                }

        /** Errors recorded during application startup. */
        internal val startupErrors: StartupErrors
            get() = instance.startupErrors
    }
}
