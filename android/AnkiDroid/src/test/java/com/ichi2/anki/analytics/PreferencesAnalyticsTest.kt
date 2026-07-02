/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.PreferenceTestUtils
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class PreferencesAnalyticsTest : RobolectricTest() {
    private val developerOptionsKeys = PreferenceTestUtils.getDeveloperOptionsKeys(targetContext)

    /** All preference keys besides dev options */
    private val allKeys =
        PreferenceTestUtils
            .getAllPreferenceKeys(targetContext)
            .subtract(developerOptionsKeys)

    private val reportableKeys = AnalyticsConstants.reportablePrefKeys.toStringResourceSet()

    /** Keys of preferences that shouldn't be reported */
    private val excludedPrefs: Set<String> =
        setOf(
            // Share feature usage: analytics are only reported if this is enabled :)
            R.string.analytics_opt_in_key, // analytics_opt_in
            // Screens: don't have a value
            R.string.pref_general_screen_key, // generalScreen
            R.string.pref_reviewing_screen_key, // reviewingScreen
            R.string.pref_sync_screen_key, // syncScreen
            R.string.pref_notifications_screen_key, // notificationsScreen
            R.string.pref_controls_screen_key, // controlsScreen
            R.string.pref_accessibility_screen_key, // accessibilityScreen
            R.string.pref_custom_sync_server_screen_key, // customSyncServerScreen
            R.string.pref_app_bar_buttons_screen_key, // appBarButtonsScreen
            R.string.pref_advanced_screen_key, // pref_screen_advanced
            R.string.pref_backups_screen_key, // backupsScreen
            R.string.pref_backups_help_key, // backups_help
            R.string.pref_review_reminders_screen_key, // reviewRemindersScreen
            R.string.pref_backup_limits_screen_key, // backupLimitsScreen
            R.string.about_screen_key, // aboutScreen
            R.string.pref_switch_profile_screen_key, // switchProfileScreen
            // Categories: don't have a value
            R.string.study_screen_category_key, // studyScreenAppearance
            R.string.pref_appearance_screen_key, // appearance_preference_group
            R.string.pref_cat_plugins_key, // category_plugins
            R.string.pref_cat_workarounds_key, // category_workarounds
            R.string.pref_controls_tab_layout_key, // controlsTabLayout
            R.string.pref_review_category_key, // reviewsCategory
            // Preferences that only click: don't have a value
            R.string.tts_key, // tts
            R.string.pref_reset_languages_key, // resetLanguages
            R.string.pref_keyboard_shortcuts_key, // showKeyboardShortcuts
            R.string.search_preference_key, // searchPreference
            // Opens App Bar buttons fragment
            R.string.custom_buttons_link_preference, // custom_buttons_link
            // Opens Custom sync server fragment
            R.string.custom_sync_server_key, // custom_sync_server_link
            R.string.thirdparty_apps_key, // thirdpartyapps_link
            // will be reworked in the future
            // Notify when
            R.string.pref_notifications_minimum_cards_due_key, // minimumCardsDueForNotification
            // Vibrate
            R.string.pref_notifications_vibrate_key, // widgetVibrate
            // Blink light
            R.string.pref_notifications_blink_key, // widgetBlink
            // potential personal data
            R.string.sync_account_key, // syncAccount
            R.string.custom_sync_server_collection_url_key, // syncBaseUrl
            R.string.pref_language_key, // language
            R.string.custom_sync_certificate_key, // customSyncCertificate
            // Experimental settings
            R.string.reviewer_menu_settings_key, // reviewerMenuSettings
            R.string.show_answer_buttons_key, // showAnswerButtons
            R.string.hide_hard_and_easy_key, // hideHardAndEasy
            R.string.reviewer_frame_style_key, // reviewerFrameStyle
            R.string.hide_system_bars_key, // hideSystemBars
            R.string.ignore_display_cutout_key, // ignoreDisplayCutout
            R.string.reviewer_toolbar_position_key, // reviewerToolbarPosition
            R.string.answer_button_size_pref_key, // answerBtnSize
        ).toStringResourceSet()

    @Test
    fun `The include and excluded prefs lists don't share elements`() {
        val intersection = reportableKeys.intersect(excludedPrefs)
        assertThat(
            "The include and exclude prefs list shouldn't share elements: $intersection",
            intersection.isEmpty(),
        )
    }

    @Test
    fun `All preferences are either included or excluded in the report list`() {
        val keysNotInAList =
            allKeys
                .subtract(excludedPrefs)
                .subtract(reportableKeys)

        assertThat(
            "All preference keys must be included in either the" +
                " `reportableKeys` or the `excludedPrefs` list" +
                ": $keysNotInAList",
            keysNotInAList.isEmpty(),
        )
    }

    @Test
    fun `reportableKeys list does not have extra keys`() {
        val extraKeys = reportableKeys.subtract(allKeys)
        assertThat(
            "reportableKeys should not have" +
                " elements that aren't in the preference keys" +
                ": $extraKeys",
            extraKeys.isEmpty(),
        )
    }

    @Test
    fun `Excluded prefs list does not have extra keys`() {
        val extraKeys = excludedPrefs.subtract(allKeys)
        assertThat(
            "excludedPrefs should not have elements that aren't in the preference keys" +
                ": $extraKeys",
            extraKeys.isEmpty(),
        )
    }

    @Test
    fun `Developer options changes must not be reported`() {
        val developerOptionsKeys = PreferenceTestUtils.getKeysFromXml(targetContext, R.xml.preferences_developer_options)
        val developerOptionsAtReportList = reportableKeys.intersect(developerOptionsKeys.toSet())

        assertThat(
            "dev options keys must not be in the `reportableKeys` list" +
                ": $developerOptionsAtReportList",
            developerOptionsAtReportList.isEmpty(),
        )
    }

    @Test
    fun `getPreferenceReportableValue - String`() {
        assertThat(
            SettingsFragment.getPreferenceReportableValue("3"),
            Matchers.equalTo(3),
        )
        assertNull(SettingsFragment.getPreferenceReportableValue("foo"))
    }

    private fun Set<Int>.toStringResourceSet(): Set<String> =
        this.mapTo(mutableSetOf()) { resId ->
            targetContext.getString(resId)
        }
}
