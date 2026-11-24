// SPDX-FileCopyrightText: 2021 Mrudul Tora <mrudultora@gmail.com>
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.analytics

import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Pins the category and action of every [AnalyticsEvent]: these strings are
 * compared between AnkiDroid versions and must never change.
 *
 * The expectations are string literals on purpose. Referencing the production
 * values here would make the test agree with any accidental rename.
 */
class AnalyticsEventTest {
    /** One sample instance per subclass, with dummy runtime data in every open field. */
    private val pinnedEvents: Map<KClass<out AnalyticsEvent>, Pinned> =
        mapOf(
            AnalyticsEvent.LinkClicked::class to
                Pinned(AnalyticsEvent.LinkClicked(LinkAction.OPENED_DONATE), "LinkClicked", "Opened Donate"),
            AnalyticsEvent.SettingTapped::class to
                Pinned(AnalyticsEvent.SettingTapped("prefKey"), "Setting", "Tapped setting", label = "prefKey"),
            AnalyticsEvent.SettingChanged::class to
                Pinned(AnalyticsEvent.SettingChanged("prefKey", 3), "Setting", "Changed setting", label = "prefKey", value = 3),
            AnalyticsEvent.CrashReported::class to
                Pinned(AnalyticsEvent.CrashReported("installId"), "ACRA Crash Handler", "Crash reported", label = "installId"),
            AnalyticsEvent.PotentiallyDeadCode::class to
                Pinned(
                    AnalyticsEvent.PotentiallyDeadCode("SomeClass.fallback"),
                    "PotentiallyDeadCode",
                    "Executed",
                    label = "SomeClass.fallback",
                ),
            AnalyticsEvent.WidgetEnabled::class to
                Pinned(AnalyticsEvent.WidgetEnabled("SomeWidget"), "Widget", "enabled", label = "SomeWidget"),
            AnalyticsEvent.WidgetDisabled::class to
                Pinned(AnalyticsEvent.WidgetDisabled("SomeWidget"), "Widget", "disabled", label = "SomeWidget"),
        )

    private val pinnedLinkActions =
        mapOf(
            LinkAction.OPENED_HELP_DIALOG to "Opened HelpDialogBox",
            LinkAction.OPENED_USING_ANKIDROID to "Opened Using AnkiDroid",
            LinkAction.OPENED_GET_HELP to "Opened Get Help",
            LinkAction.OPENED_SUPPORT_ANKIDROID to "Opened Support AnkiDroid",
            LinkAction.OPENED_COMMUNITY to "Opened Community",
            LinkAction.OPENED_PRIVACY to "Opened Privacy",
            LinkAction.OPENED_ANKIWEB_TERMS_AND_CONDITIONS to "Opened AnkiWeb Terms and Conditions",
            LinkAction.OPENED_ANKIDROID_PRIVACY_POLICY to "Opened AnkiDroid Privacy Policy",
            LinkAction.OPENED_ANKIWEB_PRIVACY_POLICY to "Opened AnkiWeb Privacy Policy",
            LinkAction.OPENED_ANKIDROID_MANUAL to "Opened AnkiDroid Manual",
            LinkAction.OPENED_ANKI_MANUAL to "Opened Anki Manual",
            LinkAction.OPENED_ANKIDROID_FAQ to "Opened AnkiDroid FAQ",
            LinkAction.OPENED_MAILING_LIST to "Opened Mailing List",
            LinkAction.OPENED_REPORT_BUG to "Opened Report a Bug",
            LinkAction.OPENED_DONATE to "Opened Donate",
            LinkAction.OPENED_TRANSLATE to "Opened Translate",
            LinkAction.OPENED_DEVELOP to "Opened Develop",
            LinkAction.OPENED_RATE to "Opened Rate",
            LinkAction.OPENED_OTHER to "Opened Other",
            LinkAction.OPENED_SEND_FEEDBACK to "Opened Send Feedback",
            LinkAction.OPENED_ANKI_FORUMS to "Opened Anki Forums",
            LinkAction.OPENED_REDDIT to "Opened Reddit",
            LinkAction.OPENED_DISCORD to "Opened Discord",
            LinkAction.OPENED_FACEBOOK to "Opened Facebook",
            LinkAction.OPENED_TWITTER to "Opened Twitter",
            LinkAction.EXCEPTION_REPORT to "Exception Report",
            LinkAction.IMPORT_APKG_FILE to "Import APKG",
            LinkAction.IMPORT_COLPKG_FILE to "Import COLPKG",
            LinkAction.IMPORT_CSV_FILE to "Import CSV",
        )

    @Test
    fun `every event subclass is pinned`() {
        assertEquals(
            pinnedEvents.keys,
            AnalyticsEvent::class.sealedSubclasses.toSet(),
            "A new AnalyticsEvent must be pinned here so its strings are never changed",
        )
    }

    @Test
    fun `pinned events emit exactly the pinned strings`() {
        for (pinned in pinnedEvents.values) {
            assertEquals(pinned.category, pinned.event.category)
            assertEquals(pinned.action, pinned.event.action)
            assertEquals(pinned.label, pinned.event.label)
            assertEquals(pinned.value, pinned.event.value)
        }
    }

    @Test
    fun `every link action is pinned`() {
        assertEquals(
            pinnedLinkActions,
            LinkAction.entries.associateWith { it.action },
            "A new LinkAction must be pinned here so its string is never changed",
        )
    }

    /** Guards the core invariant: constructor arguments must never leak into category or action. */
    @Test
    fun `runtime data never reaches category or action`() {
        val variants =
            listOf(
                AnalyticsEvent.SettingTapped("a") to AnalyticsEvent.SettingTapped("b"),
                AnalyticsEvent.SettingChanged("a", 1) to AnalyticsEvent.SettingChanged("b", 2),
                AnalyticsEvent.CrashReported("a") to AnalyticsEvent.CrashReported("b"),
                AnalyticsEvent.PotentiallyDeadCode("a") to AnalyticsEvent.PotentiallyDeadCode("b"),
                AnalyticsEvent.WidgetEnabled("a") to AnalyticsEvent.WidgetEnabled("b"),
                AnalyticsEvent.WidgetDisabled("a") to AnalyticsEvent.WidgetDisabled("b"),
            )
        for ((first, second) in variants) {
            assertEquals(first.category, second.category)
            assertEquals(first.action, second.action)
        }
    }

    private class Pinned(
        val event: AnalyticsEvent,
        val category: String,
        val action: String,
        val label: String? = null,
        val value: Int? = null,
    )
}
