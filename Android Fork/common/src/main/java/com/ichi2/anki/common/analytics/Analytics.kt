// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.analytics

/**
 * Opt-in usage analytics, implemented in the app module by `AnkiDroidUsageAnalytics`.
 *
 * Takes no Android types, so it can live in `:common`. Setup that needs a `Context`
 * stays on the implementation.
 *
 * Send events through [Analytics]; this is the contract it delegates to.
 *
 * @see Analytics
 */
interface UsageAnalytics {
    /** Reports [event]. */
    fun send(event: AnalyticsEvent)

    /** Records a screen view named after [screen]'s class. */
    fun sendAnalyticsScreenView(screen: Any) {
        sendAnalyticsScreenView(screen.javaClass.simpleName)
    }

    /** Records a screen view under [screenName], for classes serving several screens. */
    fun sendAnalyticsScreenView(screenName: String)

    /** Reports the root cause of [t]. */
    fun sendAnalyticsException(
        t: Throwable,
        fatal: Boolean,
    )

    companion object {
        /** Bumped to `_v2` for GA4 so consent given for the old backend isn't reused. */
        const val PREF_ANALYTICS_OPTIN = "analytics_opt_in_v2"
    }
}

/**
 * Sends analytics through whichever [UsageAnalytics] was registered at startup.
 *
 * ```
 * Analytics.send(AnalyticsEvent.WidgetEnabled("AddNoteWidget"))
 * ```
 */
object Analytics {
    /**
     * Used until [setAnalytics] runs. Crashes are reported through here, and that can
     * happen before startup registers an implementation, so dropping the hit has to be
     * safe: throwing would hide the crash we were reporting.
     */
    private object Unregistered : UsageAnalytics {
        override fun send(event: AnalyticsEvent) = Unit

        override fun sendAnalyticsScreenView(screenName: String) = Unit

        override fun sendAnalyticsException(
            t: Throwable,
            fatal: Boolean,
        ) = Unit
    }

    private var instance: UsageAnalytics = Unregistered

    fun setAnalytics(analytics: UsageAnalytics) {
        instance = analytics
    }

    fun send(event: AnalyticsEvent) = instance.send(event)

    fun sendAnalyticsScreenView(screen: Any) = instance.sendAnalyticsScreenView(screen)

    fun sendAnalyticsScreenView(screenName: String) = instance.sendAnalyticsScreenView(screenName)

    fun sendAnalyticsException(
        t: Throwable,
        fatal: Boolean,
    ) = instance.sendAnalyticsException(t, fatal)
}
