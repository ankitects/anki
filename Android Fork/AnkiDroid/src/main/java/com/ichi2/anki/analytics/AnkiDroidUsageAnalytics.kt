// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.criticalay.GoogleAnalytics
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import com.ichi2.anki.common.analytics.UsageAnalytics
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.ext.getRootCause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Facade over the GA4 analytics library for AnkiDroid's opt-in usage analytics.
 * Screen views, events and exceptions are routed through here, and every send
 * function is a no-op unless the user has opted in.
 *
 * See `docs/analytics/README.md` for what is sent over the wire, the consent
 * model, and answers to common reviewer questions.
 */
@NeedsTest("Add coverage for opt-in handling, client id persistence and event/exception sending")
internal object AnkiDroidUsageAnalytics : UsageAnalytics {
    const val PREF_ANALYTICS_OPTIN = UsageAnalytics.PREF_ANALYTICS_OPTIN
    private const val PREF_ANALYTICS_CLIENT_ID = "googleAnalyticsClientId"

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
     * rely on [appContext] from background paths the singleton can
     * be uninitialized in rare Android scenarios (e.g. BackupManager) and
     * analytics is a startup concern that must not crash.
     */
    private lateinit var analyticsContext: Application

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clientId: String by lazy { getOrCreateClientId(analyticsContext) }

    private val sharedPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == PREF_ANALYTICS_OPTIN) {
                val newOptIn = prefs.getBoolean(key, false)
                // [isEnabled] updates `optIn` before it writes, so an unchanged value
                // means it's already handling this toggle: don't rebuild twice
                if (newOptIn != optIn) {
                    optIn = newOptIn
                    Timber.i("Setting analytics opt-in to: %b", optIn)
                    // the client's `enabled` flag is fixed when it's built, so writes
                    // from elsewhere (the Settings switch) need a rebuild to take effect
                    if (::analyticsContext.isInitialized) {
                        reinitialize(analyticsContext)
                    }
                }
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
        Timber.i("AnkiDroidUsageAnalytics:: initialize()")
        configure(context)
        AnalyticsExceptionHandler.install(this::sendAnalyticsException)
    }

    /**
     * Reads the opt-in and builds the client. Split out of [initialize] so [reinitialize]
     * can rebuild without touching the exception handler: re-installing would chain it on
     * top of `ThrowableFilterService`, putting analytics ahead of the PII filter.
     */
    private fun configure(context: Context) {
        analyticsContext = context.applicationContext as Application

        // Read opt-in before building the client so `enabled` reflects the
        // user's choice rather than the default.
        handlePreferences(analyticsContext)

        if (analytics == null) {
            analytics =
                GoogleAnalytics.builder {
                    measurementId = analyticsContext.getString(R.string.ga_trackingId)
                    apiSecret = analyticsContext.getString(R.string.ga_api_secret)
                    appName = analyticsContext.getString(R.string.app_name)
                    appVersion = BuildConfig.VERSION_NAME
                    enabled = optIn
                    samplePercentage = getAnalyticsSamplePercentage(analyticsContext)
                    // debug builds hit GA's validation endpoint, which checks the payload
                    // but records nothing, keeping development traffic out of the property
                    debug = BuildConfig.DEBUG
                }
        }

        initializePrefKeys(analyticsContext)
    }

    private fun handlePreferences(context: Context) {
        val userPrefs = context.sharedPrefs()
        optIn = userPrefs.getBoolean(PREF_ANALYTICS_OPTIN, false)
        userPrefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    fun reinitialize(context: Context) {
        Timber.i("reInitialize()")
        serviceScope.launch { rebuild(context) }
    }

    /**
     * Flushes and rebuilds the client. Deliberately does not go through [initialize]:
     * the exception handler reports through this singleton rather than the client, so
     * it stays valid across a rebuild, and re-installing it would chain it above
     * `ThrowableFilterService` and put analytics ahead of the PII filter.
     */
    @VisibleForTesting
    internal suspend fun rebuild(context: Context) {
        runCatching { analytics?.flush() }.onFailure { e ->
            Timber.w(e, "Failed to flush analytics")
        }
        analytics = null
        configure(context)
    }

    /**
     * Records a screen view named after the runtime class of [screen].
     *
     * Uses [Class.getSimpleName] so the screen name is the unqualified class
     * name (e.g. `DeckPicker`, not `com.ichi2.anki.DeckPicker`). Pass an
     * activity/fragment/`this` from the screen you want to record.
     */
    override fun sendAnalyticsScreenView(screen: Any) = sendAnalyticsScreenView(screen.javaClass.simpleName)

    override fun sendAnalyticsScreenView(screenName: String) {
        Timber.d("AnkiDroidUsageAnalytics: screenView($screenName)")
        if (!optIn) return
        analytics?.screenView(clientId)?.screenName(screenName)?.sendAsync()
    }

    override fun send(event: AnalyticsEvent) {
        Timber.d("AnkiDroidUsageAnalytics: event(category=${event.category} action=${event.action})")
        if (!optIn) return
        val analytics = analytics ?: return
        val builder =
            analytics
                .event(clientId)
                .category(event.category)
                .action(event.action)
        event.label?.let { builder.label(it) }
        event.value?.let { builder.value(it) }
        builder.sendAsync()
    }

    override fun sendAnalyticsException(
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
        return prefs.getString(PREF_ANALYTICS_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(PREF_ANALYTICS_CLIENT_ID, it) }
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
            appContext.sharedPrefs().edit {
                putBoolean(PREF_ANALYTICS_OPTIN, value)
            }
            // Rebuild the underlying client so its own `enabled` flag picks
            // up the new opt-in state without waiting for the next launch.
            if (::analyticsContext.isInitialized) {
                reinitialize(analyticsContext)
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

/**
 * Initializes GA4 analytics and wires it up as the global [Analytics] implementation.
 */
context(application: Application)
fun initializeAnalytics() {
    AnkiDroidUsageAnalytics.initialize(application)
    Analytics.setAnalytics(AnkiDroidUsageAnalytics)
}
