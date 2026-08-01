// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.preferences

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreference
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.preferences.profiles.SwitchProfilesFragment
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.preferences.HeaderPreference

class HeaderFragment : SettingsFragment() {
    override val analyticsScreenNameConstant: String
        get() = "prefs.initialPage"
    override val preferenceResource: Int
        get() = R.xml.preference_headers

    private var highlightedPreferenceKey: String = ""

    override fun initSubscreen() {
        requirePreference<HeaderPreference>(R.string.pref_backup_limits_screen_key)
            .title = TR.preferencesBackups()

        requirePreference<HeaderPreference>(R.string.pref_appearance_screen_key)
            .title = TR.preferencesAppearance()

        requirePreference<HeaderPreference>(R.string.pref_sync_screen_key).summary =
            HeaderPreference.buildHeaderSummary(
                TR.sentenceCase.ankiWebAccount,
                getString(R.string.automatic_sync_choice),
            )

        requirePreference<Preference>(R.string.pref_advanced_screen_key).apply {
            if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                isVisible = false
            }
        }

        requirePreference<Preference>(R.string.pref_developer_options_screen_key)
            .isVisible = Prefs.isDeveloperOptionsEnabled

        requirePreference<HeaderPreference>(R.string.pref_review_reminders_screen_key).isVisible = Prefs.newReviewRemindersEnabled
        requirePreference<HeaderPreference>(R.string.pref_notifications_screen_key).isVisible = !Prefs.newReviewRemindersEnabled
        requirePreference<HeaderPreference>(R.string.pref_switch_profile_screen_key).isVisible = Prefs.switchProfileEnabled

        configureSearchBar(
            requireActivity() as AppCompatActivity,
            requirePreference<SearchPreference>(R.string.search_preference_key).searchConfiguration,
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }
    }

    fun highlightPreference(
        @StringRes keyRes: Int,
    ) {
        val key = getString(keyRes)
        findPreference<HeaderPreference>(highlightedPreferenceKey)?.setHighlighted(false)
        findPreference<HeaderPreference>(key)?.setHighlighted(true)
        highlightedPreferenceKey = key
    }

    companion object {
        fun configureSearchBar(
            activity: AppCompatActivity,
            searchConfiguration: SearchConfiguration,
        ) {
            val setDuePreferenceTitle = with(activity) { TR.sentenceCase.setDueDate }
            with(searchConfiguration) {
                setActivity(activity)
                setBreadcrumbsEnabled(true)
                setFuzzySearchEnabled(false)
                setHistoryEnabled(true)
                setFragmentContainerViewId(android.R.id.list_container)

                index(R.xml.preferences_general)
                index(R.xml.preferences_reviewing)
                index(R.xml.preferences_sync)
                index(R.xml.preferences_custom_sync_server)
                    .addBreadcrumb(R.string.pref_cat_sync)

                if (Prefs.newReviewRemindersEnabled) {
                    indexItem()
                        .withKey(activity.getString(R.string.pref_review_reminders_screen_key))
                        .withTitle("Review reminders")
                        .withResId(R.xml.preferences_review_reminders)
                        .withSummary("Notifications")
                } else {
                    index(R.xml.preferences_notifications)
                }

                index(R.xml.preferences_appearance)
                if (!Prefs.isNewStudyScreenEnabled) {
                    index(R.xml.preferences_custom_buttons)
                        .addBreadcrumb(TR.preferencesAppearance())
                }
                index(R.xml.preferences_controls)
                index(R.xml.preferences_reviewer_controls)
                    .addBreadcrumb(activity.getString(R.string.pref_cat_controls))
                    .addBreadcrumb(activity.getString(R.string.pref_controls_reviews_tab))
                index(R.xml.preferences_previewer_controls)
                    .addBreadcrumb(activity.getString(R.string.pref_cat_controls))
                    .addBreadcrumb(activity.getString(R.string.pref_controls_previews_tab))
                index(R.xml.preferences_accessibility)
                index(R.xml.preferences_backup_limits)
                ignorePreference(activity.getString(R.string.pref_backups_help_key))
                indexItem()
                    .withKey(activity.getString(R.string.reschedule_command_key))
                    .withTitle(setDuePreferenceTitle)
                    .withResId(R.xml.preferences_controls)
                    .addBreadcrumb(activity.getString(R.string.pref_cat_controls))
                    .addBreadcrumb(setDuePreferenceTitle)
                // Some strings can't be indexed from the XML document as they are loaded from the back-end. We add them manually.
                indexItem()
                    .withKey(activity.getString(R.string.anki_card_external_context_menu_key))
                    .withTitle(
                        activity.getString(
                            R.string.card_browser_enable_external_context_menu,
                            activity.getString(R.string.context_menu_anki_card_label),
                        ),
                    ).withSummary(
                        activity.getString(
                            R.string.card_browser_enable_external_context_menu_summary,
                            activity.getString(R.string.context_menu_anki_card_label),
                        ),
                    ).withResId(R.xml.preferences_general)
                    .addBreadcrumb(activity.getString(R.string.pref_cat_general))
                    .addBreadcrumb(activity.getString(R.string.pref_cat_system_wide))

                indexItem()
                    .withKey(activity.getString(R.string.card_browser_external_context_menu_key))
                    .withTitle(
                        activity.getString(
                            R.string.card_browser_enable_external_context_menu,
                            activity.getString(R.string.card_browser_context_menu),
                        ),
                    ).withSummary(
                        activity.getString(
                            R.string.card_browser_enable_external_context_menu_summary,
                            activity.getString(R.string.card_browser_context_menu),
                        ),
                    ).withResId(R.xml.preferences_general)
                    .addBreadcrumb(activity.getString(R.string.pref_cat_general))
                    .addBreadcrumb(activity.getString(R.string.pref_cat_system_wide))

                if (!Prefs.isNewStudyScreenEnabled) {
                    indexItem()
                        .withKey(activity.getString(R.string.show_audio_play_buttons_key))
                        .withTitle(
                            TR.preferencesShowPlayButtonsOnCardsWith(),
                        ).withResId(R.xml.preferences_appearance)
                        .addBreadcrumb(TR.preferencesAppearance())
                        .addBreadcrumb(activity.getString(R.string.pref_cat_reviewer))
                }

                indexItem()
                    .withKey(activity.getString(R.string.one_way_sync_key))
                    .withTitle(
                        activity.getString(R.string.one_way_sync_title),
                    ).withSummary(TR.preferencesOnNextSyncForceChangesIn())
                    .withResId(R.xml.preferences_sync)
                    .addBreadcrumb(activity.getString(R.string.pref_cat_sync))
                    .addBreadcrumb(activity.getString(R.string.pref_cat_advanced))
            }

            // Some preferences and categories are only shown conditionally,
            // so they should be searchable based on the same conditions

            // From [HeaderFragment.onCreatePreferences]
            if (Prefs.isDeveloperOptionsEnabled) {
                searchConfiguration.index(R.xml.preferences_developer_options)
                // From [DeveloperOptionsFragment.initSubscreen]
                if (BuildConfig.DEBUG) {
                    searchConfiguration.ignorePreference(activity.getString(R.string.developer_options_enabled_by_user_key))
                }
            }

            // From [HeaderFragment.onCreatePreferences]
            if (!AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                searchConfiguration.index(R.xml.preferences_advanced)
            }

            // From [NotificationsSettingsFragment.initSubscreen]
            if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
                searchConfiguration.ignorePreference(activity.getString(R.string.pref_notifications_vibrate_key))
                searchConfiguration.ignorePreference(activity.getString(R.string.pref_notifications_blink_key))
            }

            // From [AdvancedSettingsFragment.removeUnnecessaryAdvancedPrefs]
            if (!CompatHelper.hasScrollKeys()) {
                searchConfiguration.ignorePreference(activity.getString(R.string.double_scrolling_gap_key))
            }

            searchConfiguration.ignorePreference(activity.getString(R.string.user_actions_controls_category_key))

            if (Prefs.isNewStudyScreenEnabled) {
                searchConfiguration.index(R.xml.preferences_reviewer)
                val legacySettings =
                    AdvancedSettingsFragment.legacyStudyScreenSettings + AccessibilitySettingsFragment.legacyStudyScreenSettings +
                        AppearanceSettingsFragment.legacyStudyScreenSettings + ControlsSettingsFragment.legacyStudyScreenSettings
                for (key in legacySettings) {
                    val keyString = activity.getString(key)
                    searchConfiguration.ignorePreference(keyString)
                }
            }
        }

        /**
         * @return the key for the [HeaderPreference] that corresponds to the given [fragment]
         * in the Preference tree.
         *
         * e.g. Sync > Custom sync server settings -> returns the key for the Sync header
         */
        @StringRes
        fun getHeaderKeyForFragment(fragment: Fragment): Int? =
            when (fragment) {
                is GeneralSettingsFragment -> R.string.pref_general_screen_key
                is ReviewingSettingsFragment -> R.string.pref_reviewing_screen_key
                is SyncSettingsFragment, is CustomSyncServerSettingsFragment -> R.string.pref_sync_screen_key
                is NotificationsSettingsFragment -> R.string.pref_notifications_screen_key
                is ScheduleRemindersFragment -> R.string.pref_review_reminders_screen_key
                is AppearanceSettingsFragment, is CustomButtonsSettingsFragment -> R.string.pref_appearance_screen_key
                is ControlsSettingsFragment -> R.string.pref_controls_screen_key
                is AccessibilitySettingsFragment -> R.string.pref_accessibility_screen_key
                is BackupLimitsSettingsFragment -> R.string.pref_backup_limits_screen_key
                is AdvancedSettingsFragment -> R.string.pref_advanced_screen_key
                is ReviewerOptionsFragment, is ReviewerMenuSettingsFragment -> R.string.new_reviewer_options_key
                is DeveloperOptionsFragment -> R.string.pref_developer_options_screen_key
                is AboutFragment -> R.string.about_screen_key
                is SwitchProfilesFragment -> R.string.pref_switch_profile_screen_key
                else -> null
            }
    }
}
