// SPDX-FileCopyrightText: 2018 Mike Hardy <mike@mikehardy.net>
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.analytics

/**
 * The closed set of events AnkiDroid reports to analytics.
 *
 * New events can only be declared in this module, and `AnalyticsEventTest` fails until each one is pinned.
 */
sealed class AnalyticsEvent(
    val category: String,
    val action: String,
    val label: String? = null,
    val value: Int? = null,
) {
    /** The user followed a link or menu entry, e.g. in the help dialog. */
    data class LinkClicked(
        val link: LinkAction,
    ) : AnalyticsEvent("LinkClicked", link.action)

    /** A settings row was tapped. [key] is null for preferences without one. */
    data class SettingTapped(
        val key: String?,
    ) : AnalyticsEvent("Setting", "Tapped setting", label = key)

    /** A reportable preference changed. [newValue] is null when not numeric. */
    data class SettingChanged(
        val key: String,
        val newValue: Int?,
    ) : AnalyticsEvent("Setting", "Changed setting", label = key, value = newValue)

    /** ACRA handled a crash. [installId] matches this hit to the ACRA report. */
    data class CrashReported(
        val installId: String,
    ) : AnalyticsEvent("ACRA Crash Handler", "Crash reported", label = installId)

    /**
     * A path believed to be unreachable was executed. Keep its fallback until evidence supports removal.
     * [location] must be a stable, developer-defined identifier, never user data.
     */
    data class PotentiallyDeadCode(
        val location: String,
    ) : AnalyticsEvent("PotentiallyDeadCode", "Executed", label = location)

    /** A home screen widget was added. [widgetName] is the provider class name. */
    data class WidgetEnabled(
        val widgetName: String,
    ) : AnalyticsEvent("Widget", "enabled", label = widgetName)

    /** The last instance of a home screen widget was removed. */
    data class WidgetDisabled(
        val widgetName: String,
    ) : AnalyticsEvent("Widget", "disabled", label = widgetName)
}

/**
 * Everything reported under the `LinkClicked` category.
 *
 * The [action] strings are compared between AnkiDroid versions and must not
 * change; `AnalyticsEventTest` pins them. The import entries look out of place
 * but have always been reported under `LinkClicked`, and moving them would
 * break that comparison.
 */
enum class LinkAction(
    val action: String,
) {
    OPENED_HELP_DIALOG("Opened HelpDialogBox"),
    OPENED_USING_ANKIDROID("Opened Using AnkiDroid"),
    OPENED_GET_HELP("Opened Get Help"),
    OPENED_SUPPORT_ANKIDROID("Opened Support AnkiDroid"),
    OPENED_COMMUNITY("Opened Community"),
    OPENED_PRIVACY("Opened Privacy"),
    OPENED_ANKIWEB_TERMS_AND_CONDITIONS("Opened AnkiWeb Terms and Conditions"),
    OPENED_ANKIDROID_PRIVACY_POLICY("Opened AnkiDroid Privacy Policy"),
    OPENED_ANKIWEB_PRIVACY_POLICY("Opened AnkiWeb Privacy Policy"),
    OPENED_ANKIDROID_MANUAL("Opened AnkiDroid Manual"),
    OPENED_ANKI_MANUAL("Opened Anki Manual"),
    OPENED_ANKIDROID_FAQ("Opened AnkiDroid FAQ"),
    OPENED_MAILING_LIST("Opened Mailing List"),
    OPENED_REPORT_BUG("Opened Report a Bug"),
    OPENED_DONATE("Opened Donate"),
    OPENED_TRANSLATE("Opened Translate"),
    OPENED_DEVELOP("Opened Develop"),
    OPENED_RATE("Opened Rate"),
    OPENED_OTHER("Opened Other"),
    OPENED_SEND_FEEDBACK("Opened Send Feedback"),
    OPENED_ANKI_FORUMS("Opened Anki Forums"),
    OPENED_REDDIT("Opened Reddit"),
    OPENED_DISCORD("Opened Discord"),
    OPENED_FACEBOOK("Opened Facebook"),
    OPENED_TWITTER("Opened Twitter"),
    EXCEPTION_REPORT("Exception Report"),
    IMPORT_APKG_FILE("Import APKG"),
    IMPORT_COLPKG_FILE("Import COLPKG"),
    IMPORT_CSV_FILE("Import CSV"),
}
