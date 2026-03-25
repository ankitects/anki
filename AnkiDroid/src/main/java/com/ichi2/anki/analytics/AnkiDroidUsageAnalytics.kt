// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.criticalay.GoogleAnalytics
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.ext.getRootCause
import com.ichi2.anki.preferences.sharedPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

@NeedsTest("Add coverage for opt-in handling, client id persistence and event/exception sending")
object AnkiDroidUsageAnalytics {
    private const val ANALYTICS_OPTIN_KEY = "analytics_opt_in"
    private const val ANALYTICS_CLIENT_ID = "googleAnalyticsClientId"

    /**
     * Hard cap on the length of an exception description sent to GA.
     * GA's `exception_description` parameter is bounded truncate
     * defensively so an overly long stack/message isn't rejected at the wire.
     */
    private const val MAX_EXCEPTION_DESCRIPTION_LENGTH = 150

    /**
     * Dedicated prefs file (separate from the user-facing app preferences) for the
     * analytics client id. Keeping it isolated avoids exposing the id through
     * preference screens, backups, or bulk prefs operations on the main file.
     *
     * The id is install-scoped (one per device install) rather than per-profile
     * profiles share the same analytics client id, which matches GA's expectations
     * and avoids fragmenting analytics across profile switches.
     */
    private const val ANALYTICS_PREFS = "analyticsPrefs"

    @Volatile private var analytics: GoogleAnalytics? = null

    @Volatile private var optIn = false

    /**
     * Application context captured during [initialize]. Held here so we don't
     * rely on [AnkiDroidApp.instance] from background paths the singleton can
     * be uninitialized in rare Android scenarios (e.g. BackupManager) and
     * analytics is a startup concern that must not crash.
     */
    private lateinit var appContext: Context

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clientId: String by lazy { getOrCreateClientId(appContext) }

    private val sharedPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == ANALYTICS_OPTIN_KEY) {
                optIn = prefs.getBoolean(key, false)
                Timber.i("Setting analytics opt-in to: %b", optIn)
            }
        }

    /**
     * Cached percentage of analytics events that are actually sent; the rest
     * are dropped client-side to limit volume.
     *
     * Starts as [AnalyticsSamplePercentage.Uninitialized] and is loaded lazily
     * from `R.integer.ga_sampleFrequency` on first read. [setDevMode] overrides
     * it to [AnalyticsSamplePercentage.Full] so every event is sent during
     * development.
     */
    private var samplePercentage: AnalyticsSamplePercentage = AnalyticsSamplePercentage.Uninitialized

    /**
     * Resolved preference keys (the localized string-resource values, not
     * resource ids) whose changes both the key and the new value are
     * reported to analytics. Populated during [initialize] from
     * [AnalyticsConstants.reportablePrefKeys].
     */
    lateinit var reportablePreferences: Set<String>
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext

        Timber.i("AnkiDroidUsageAnalytics:: initialize()")

        // Read opt-in before building the client so `enabled` reflects the
        // user's choice rather than the default.
        handlePreferences(appContext)

        if (analytics == null) {
            analytics =
                GoogleAnalytics.builder {
                    measurementId = appContext.getString(R.string.ga_trackingId)
                    apiSecret = BuildConfig.ANALYTICS_API_KEY
                    appName = appContext.getString(R.string.app_name)
                    appVersion = BuildConfig.VERSION_NAME
                    enabled = optIn
                    samplePercentage = getAnalyticsSamplePercentage(appContext)
                    debug = false
                }
        }

        initializePrefKeys(appContext)

        AnalyticsExceptionHandler.install(this::sendAnalyticsException)
    }

    private fun handlePreferences(context: Context) {
        val userPrefs = context.sharedPrefs()
        optIn = userPrefs.getBoolean(ANALYTICS_OPTIN_KEY, false)
        userPrefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    fun reinitialize(context: Context) {
        Timber.i("reInitialize()")
        AnalyticsExceptionHandler.uninstall()

        serviceScope.launch {
            runCatching { analytics?.flush() }.onFailure { e ->
                Timber.w(e, "Failed to flush analytics")
            }
            analytics = null
            initialize(context)
        }
    }

    /**
     * Records a screen view named after the runtime class of [screen].
     *
     * Uses [Class.getSimpleName] so the screen name is the unqualified class
     * name (e.g. `DeckPicker`, not `com.ichi2.anki.DeckPicker`). Pass an
     * activity/fragment/`this` from the screen you want to record.
     */
    fun sendAnalyticsScreenView(screen: Any) = sendAnalyticsScreenView(screen.javaClass.simpleName)

    fun sendAnalyticsScreenView(screenName: String) {
        Timber.d("AnkiDroidUsageAnalytics: screenView($screenName)")
        if (!optIn) return
        analytics?.screenView(clientId)?.screenName(screenName)?.sendAsync()
    }

    fun sendAnalyticsEvent(
        category: String,
        action: String,
        value: Int? = null,
        label: String? = null,
    ) {
        Timber.d("AnkiDroidUsageAnalytics: event(category=$category action=$action)")
        if (!optIn) return
        val analytics = analytics ?: return
        val event =
            analytics
                .event(clientId)
                .category(category)
                .action(action)
        label?.let { event.label(it) }
        value?.let { event.value(it) }
        event.sendAsync()
    }

    fun sendAnalyticsException(
        t: Throwable,
        fatal: Boolean,
    ) {
        val cause = t.getRootCause()
        sendAnalyticsException("${cause::class.simpleName}: ${cause.message}", fatal)
    }

    fun sendAnalyticsException(
        description: String,
        fatal: Boolean,
    ) {
        if (!optIn) return

        Timber.d("AnkiDroidUsageAnalytics: exception(fatal=$fatal)")
        val analytics = analytics ?: return
        analytics
            .exception(clientId)
            .description(description.take(MAX_EXCEPTION_DESCRIPTION_LENGTH))
            .fatal(fatal)
            .sendAsync()
    }

    private fun getOrCreateClientId(context: Context): String {
        Timber.d("AnkiDroidUsageAnalytics:: getting client Id")
        val prefs = context.getSharedPreferences(ANALYTICS_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(ANALYTICS_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(ANALYTICS_CLIENT_ID, it) }
        }
    }

    private fun getAnalyticsSamplePercentage(context: Context): Int {
        Timber.d("AnkiDroidUsageAnalytics:: getting sample percentage")
        if (!samplePercentage.isInitialized) {
            samplePercentage =
                AnalyticsSamplePercentage(context.resources.getInteger(R.integer.ga_sampleFrequency))
        }
        return samplePercentage.value
    }

    /**
     * Resolves [AnalyticsConstants.reportablePrefKeys] (string-resource ids)
     * to their localized key strings via [context] and caches the result in
     * [reportablePreferences] for fast membership checks at the call site.
     */
    private fun initializePrefKeys(context: Context) {
        Timber.d("AnkiDroidUsageAnalytics:: initializing pref keys")
        reportablePreferences =
            AnalyticsConstants.reportablePrefKeys.mapTo(
                HashSet(AnalyticsConstants.reportablePrefKeys.size),
            ) { context.getString(it) }
    }

    var isEnabled: Boolean
        get() = optIn
        set(value) {
            optIn = value
            AnkiDroidApp.instance.sharedPrefs().edit {
                putBoolean(ANALYTICS_OPTIN_KEY, value)
            }
            // Rebuild the underlying client so its own `enabled` flag picks
            // up the new opt-in state without waiting for the next launch.
            if (::appContext.isInitialized) {
                reinitialize(appContext)
            }
        }

    /**
     * Switches analytics into "development" mode: forces the [samplePercentage]
     * to 100 so every event is sent (production samples a subset to limit
     * volume) and reinitialized the underlying analytics client to apply it.
     *
     * Intended for debug builds and testing not for production use.
     */
    fun setDevMode(context: Context) {
        Timber.d("setDevMode() re-configuring for development analytics tagging")
        samplePercentage = AnalyticsSamplePercentage.Full
        reinitialize(context)
    }
}
