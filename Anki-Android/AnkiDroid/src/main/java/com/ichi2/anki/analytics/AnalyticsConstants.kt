/*
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.analytics

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.R

/**
 * A centralized repository for all constant values used in analytics tracking.
 */
object AnalyticsConstants {
    object Category {
        const val SYNC = "Sync"
        const val LINK_CLICKED = "LinkClicked"
        const val SETTING = "Setting"
    }

    /**
     * These Strings must not be changed as they are used for analytic comparisons between AnkiDroid versions.
     * If a new string is added here then the respective changes must also be made in AnalyticsConstantsTest.kt
     * All the constant strings added here must be annotated with @AnalyticsConstant.
     */
    object Actions {
        // Analytics actions used in Help Dialog
        @AnalyticsConstant
        val OPENED_HELP_DIALOG = "Opened HelpDialogBox"

        @AnalyticsConstant
        val OPENED_USING_ANKIDROID = "Opened Using AnkiDroid"

        @AnalyticsConstant
        val OPENED_GET_HELP = "Opened Get Help"

        @AnalyticsConstant
        val OPENED_SUPPORT_ANKIDROID = "Opened Support AnkiDroid"

        @AnalyticsConstant
        val OPENED_COMMUNITY = "Opened Community"

        @AnalyticsConstant
        val OPENED_PRIVACY = "Opened Privacy"

        @AnalyticsConstant
        val OPENED_ANKIWEB_TERMS_AND_CONDITIONS = "Opened AnkiWeb Terms and Conditions"

        @AnalyticsConstant
        val OPENED_ANKIDROID_PRIVACY_POLICY = "Opened AnkiDroid Privacy Policy"

        @AnalyticsConstant
        val OPENED_ANKIWEB_PRIVACY_POLICY = "Opened AnkiWeb Privacy Policy"

        @AnalyticsConstant
        val OPENED_ANKIDROID_MANUAL = "Opened AnkiDroid Manual"

        @AnalyticsConstant
        val OPENED_ANKI_MANUAL = "Opened Anki Manual"

        @AnalyticsConstant
        val OPENED_ANKIDROID_FAQ = "Opened AnkiDroid FAQ"

        @AnalyticsConstant
        val OPENED_MAILING_LIST = "Opened Mailing List"

        @AnalyticsConstant
        val OPENED_REPORT_BUG = "Opened Report a Bug"

        @AnalyticsConstant
        val OPENED_DONATE = "Opened Donate"

        @AnalyticsConstant
        val OPENED_TRANSLATE = "Opened Translate"

        @AnalyticsConstant
        val OPENED_DEVELOP = "Opened Develop"

        @AnalyticsConstant
        val OPENED_RATE = "Opened Rate"

        @AnalyticsConstant
        val OPENED_OTHER = "Opened Other"

        @AnalyticsConstant
        val OPENED_SEND_FEEDBACK = "Opened Send Feedback"

        @AnalyticsConstant
        val OPENED_ANKI_FORUMS = "Opened Anki Forums"

        @AnalyticsConstant
        val OPENED_REDDIT = "Opened Reddit"

        @AnalyticsConstant
        val OPENED_DISCORD = "Opened Discord"

        @AnalyticsConstant
        val OPENED_FACEBOOK = "Opened Facebook"

        @AnalyticsConstant
        val OPENED_TWITTER = "Opened Twitter"

        @AnalyticsConstant
        val EXCEPTION_REPORT = "Exception Report"

        @AnalyticsConstant
        val IMPORT_APKG_FILE = "Import APKG"

        @AnalyticsConstant
        val IMPORT_COLPKG_FILE = "Import COLPKG"

        @AnalyticsConstant
        val IMPORT_CSV_FILE = "Import CSV"

        @AnalyticsConstant
        val TAPPED_SETTING = "Tapped setting"

        @AnalyticsConstant
        val CHANGED_SETTING = "Changed setting"
    }

    @VisibleForTesting
    val reportablePrefKeys =
        setOf(
            // ******************************** General ************************************************
            R.string.error_reporting_mode_key, // Error reporting mode
            R.string.paste_png_key, // Paste clipboard images as PNG
            R.string.deck_for_new_cards_key, // Deck for new cards
            R.string.exit_via_double_tap_back_key, // Press back twice to go back/exit
            R.string.anki_card_external_context_menu_key, // ‘Anki Card’ Menu
            R.string.card_browser_external_context_menu_key, // ‘Card Browser’ Menu
            // ******************************** Reviewing **********************************************
            R.string.day_offset_preference, // Start of next day
            R.string.learn_cutoff_preference, // Learn ahead limit
            R.string.time_limit_preference, // Timebox time limit
            R.string.keep_screen_on_preference, // Disable screen timeout
            R.string.double_tap_timeout_pref_key, // Double tap time interval (milliseconds)
            // ******************************** Sync ***************************************************
            R.string.sync_fetch_media_key, // Fetch media on sync
            R.string.automatic_sync_choice_key, // Automatic synchronization
            R.string.sync_status_badge_key, // Display synchronization status
            R.string.metered_sync_key, // Allow sync on metered connections
            R.string.sync_io_timeout_secs_key, // Network timeout
            R.string.one_way_sync_key, // One-way sync
            // ******************************** Backup *************************************************
            R.string.pref_minutes_between_automatic_backups_key,
            R.string.pref_daily_backups_to_keep_key,
            R.string.pref_weekly_backups_to_keep_key,
            R.string.pref_monthly_backups_to_keep_key,
            // ******************************** Appearance *********************************************
            R.string.app_theme_key, // Theme
            R.string.day_theme_key, // Day theme
            R.string.night_theme_key, // Night theme
            R.string.pref_deck_picker_background_key, // Background image
            R.string.pref_remove_wallpaper_key, // Remove wallpaper
            R.string.fullscreen_mode_preference, // Fullscreen mode
            R.string.center_vertically_preference, // Center align
            R.string.show_estimates_preference, // Show button time
            R.string.answer_buttons_position_preference, // Answer buttons position
            R.string.show_topbar_preference, // Show top bar
            R.string.show_progress_preference, // Show remaining
            R.string.show_eta_preference, // Show ETA
            R.string.show_audio_play_buttons_key, // Show play buttons on cards with audio (reversed in collection: HIDE_AUDIO_PLAY_BUTTONS)
            R.string.pref_display_filenames_in_browser_key, // Display filenames in card browser
            R.string.show_deck_title_key, // Show deck title
            // ******************************** Controls *********************************************
            R.string.gestures_preference, // Enable gestures
            R.string.gestures_corner_touch_preference, // 9-point touch
            R.string.nav_drawer_gesture_key, // Full screen navigation drawer
            R.string.pref_swipe_sensitivity_key, // Swipe sensitivity
            R.string.show_answer_command_key,
            R.string.answer_again_command_key,
            R.string.answer_hard_command_key,
            R.string.answer_good_command_key,
            R.string.answer_easy_command_key,
            R.string.undo_command_key,
            R.string.redo_command_key,
            R.string.edit_command_key,
            R.string.mark_command_key,
            R.string.bury_card_command_key,
            R.string.suspend_card_command_key,
            R.string.delete_command_key,
            R.string.play_media_command_key,
            R.string.abort_command_key,
            R.string.bury_note_command_key,
            R.string.suspend_note_command_key,
            R.string.flag_red_command_key,
            R.string.flag_orange_command_key,
            R.string.flag_green_command_key,
            R.string.flag_blue_command_key,
            R.string.flag_pink_command_key,
            R.string.flag_turquoise_command_key,
            R.string.flag_purple_command_key,
            R.string.remove_flag_command_key,
            R.string.page_up_command_key,
            R.string.page_down_command_key,
            R.string.tag_command_key,
            R.string.card_info_command_key,
            R.string.previous_card_info_command_key,
            R.string.record_voice_command_key,
            R.string.replay_voice_command_key,
            R.string.save_voice_command_key,
            R.string.toggle_whiteboard_command_key,
            R.string.toggle_eraser_command_key,
            R.string.clear_whiteboard_command_key,
            R.string.change_whiteboard_pen_color_command_key,
            R.string.toggle_auto_advance_command_key,
            R.string.show_hint_command_key,
            R.string.show_all_hints_command_key,
            R.string.add_note_command_key,
            R.string.reschedule_command_key,
            R.string.user_action_1_key,
            R.string.user_action_2_key,
            R.string.user_action_3_key,
            R.string.user_action_4_key,
            R.string.user_action_5_key,
            R.string.user_action_6_key,
            R.string.user_action_7_key,
            R.string.user_action_8_key,
            R.string.user_action_9_key,
            // ******************************** Accessibility ******************************************
            R.string.card_zoom_preference,
            R.string.image_zoom_preference,
            R.string.answer_button_size_preference,
            R.string.show_large_answer_buttons_preference,
            R.string.pref_card_browser_font_scale_key,
            R.string.pref_card_minimal_click_time,
            // ******************************** Advanced ***********************************************
            R.string.pref_ankidroid_directory_key, // AnkiDroid directory
            R.string.double_scrolling_gap_key, // Double scrolling
            R.string.disable_hardware_render_key, // Disable card hardware render
            R.string.safe_display_key, // Safe display mode
            R.string.use_input_tag_key, // Type answer into the card
            R.string.disable_single_field_edit_key, // Disable Single-Field Edit Mode
            R.string.note_editor_newline_replace_key, // Replace newlines with HTML
            R.string.type_in_answer_focus_key, // Focus ‘type in answer’
            R.string.media_import_allow_all_files_key, // Allow all files in media imports
            R.string.enable_api_key, // Enable AnkiDroid API
            R.string.use_fixed_port_pref_key, // localStorage in Study Screen
            // ******************************** App bar buttons ****************************************
            R.string.reset_custom_buttons_key,
            R.string.custom_button_undo_key,
            R.string.custom_button_redo_key,
            R.string.custom_button_schedule_card_key,
            R.string.custom_button_flag_key,
            R.string.custom_button_edit_card_key,
            R.string.custom_button_tags_key,
            R.string.custom_button_add_card_key,
            R.string.custom_button_replay_key,
            R.string.custom_button_card_info_key,
            R.string.custom_button_previous_card_info_key,
            R.string.custom_button_select_tts_key,
            R.string.custom_button_deck_options_key,
            R.string.custom_button_mark_card_key,
            R.string.custom_button_toggle_mic_toolbar_key,
            R.string.custom_button_bury_key,
            R.string.custom_button_suspend_key,
            R.string.custom_button_delete_key,
            R.string.custom_button_enable_whiteboard_key,
            R.string.custom_button_toggle_eraser_key,
            R.string.custom_button_toggle_stylus_key,
            R.string.custom_button_save_whiteboard_key,
            R.string.custom_button_whiteboard_pen_color_key,
            R.string.custom_button_show_hide_whiteboard_key,
            R.string.custom_button_clear_whiteboard_key,
            R.string.custom_button_toggle_auto_advance,
            R.string.custom_button_user_action_1_key,
            R.string.custom_button_user_action_2_key,
            R.string.custom_button_user_action_3_key,
            R.string.custom_button_user_action_4_key,
            R.string.custom_button_user_action_5_key,
            R.string.custom_button_user_action_6_key,
            R.string.custom_button_user_action_7_key,
            R.string.custom_button_user_action_8_key,
            R.string.custom_button_user_action_9_key,
            // *********************************** Study Screen ************************************
            R.string.new_reviewer_options_key,
            R.string.show_answer_feedback_key,
        )
}
