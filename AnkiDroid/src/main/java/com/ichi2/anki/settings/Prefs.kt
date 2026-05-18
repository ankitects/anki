/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.TapGestureMode
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.HideSystemBars
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.PrefEnum
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.settings.enums.ToolbarPosition
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// TODO move this to `com.ichi2.anki.preferences`
//  after the UI classes of that package are moved to `com.ichi2.anki.ui.preferences`
object Prefs : PrefsRepository(AnkiDroidApp.sharedPrefs(), AnkiDroidApp.appResources)

open class PrefsRepository(
    val sharedPrefs: SharedPreferences,
    private val resources: Resources,
) {
    constructor(context: Context) : this(context.sharedPrefs(), context.resources)

    @VisibleForTesting
    fun key(
        @StringRes resId: Int,
    ): String = resources.getString(resId)

    @VisibleForTesting
    fun getBoolean(
        @StringRes keyResId: Int,
        defValue: Boolean,
    ): Boolean = sharedPrefs.getBoolean(key(keyResId), defValue)

    @VisibleForTesting
    fun putBoolean(
        @StringRes keyResId: Int,
        value: Boolean,
    ) {
        sharedPrefs.edit { putBoolean(key(keyResId), value) }
    }

    @VisibleForTesting
    fun getString(
        @StringRes keyResId: Int,
        defValue: String?,
    ): String? = sharedPrefs.getString(key(keyResId), defValue)

    @VisibleForTesting
    fun putString(
        @StringRes keyResId: Int,
        value: String?,
    ) {
        sharedPrefs.edit { putString(key(keyResId), value) }
    }

    @VisibleForTesting
    fun getInt(
        @StringRes keyResId: Int,
        defValue: Int,
    ): Int = sharedPrefs.getInt(key(keyResId), defValue)

    @VisibleForTesting
    fun putInt(
        @StringRes keyResId: Int,
        value: Int,
    ) = sharedPrefs.edit { putInt(key(keyResId), value) }

    private fun getLong(
        @StringRes keyResId: Int,
        defValue: Long,
    ): Long = sharedPrefs.getLong(key(keyResId), defValue)

    private fun putLong(
        @StringRes keyResId: Int,
        value: Long,
    ) = sharedPrefs.edit { putLong(key(keyResId), value) }

    @VisibleForTesting
    fun <E> getEnum(
        @StringRes keyResId: Int,
        defaultValue: E,
    ): E where E : Enum<E>, E : PrefEnum {
        val enumClass = defaultValue.javaClass
        val fallback = resources.getString(defaultValue.entryResId)
        val stringValue = getString(keyResId, fallback)
        return enumClass.enumConstants?.firstOrNull {
            resources.getString(it.entryResId) == stringValue
        } ?: defaultValue
    }

    fun <E> putEnum(
        @StringRes keyResId: Int,
        value: E,
    ) where E : Enum<E>, E : PrefEnum {
        val stringValue = resources.getString(value.entryResId)
        putString(keyResId, stringValue)
    }

    // **************************************** Delegates *************************************** //

    @VisibleForTesting
    fun booleanPref(
        key: String,
        defaultValue: Boolean,
    ): ReadWriteProperty<Any?, Boolean> =
        object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): Boolean = sharedPrefs.getBoolean(key, defaultValue)

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: Boolean,
            ) {
                sharedPrefs.edit { putBoolean(key, value) }
            }
        }

    @VisibleForTesting
    fun booleanPref(
        @StringRes keyResId: Int,
        defaultValue: Boolean,
    ): ReadWriteProperty<Any?, Boolean> = booleanPref(key(keyResId), defaultValue)

    @VisibleForTesting
    fun stringPref(
        @StringRes keyResId: Int,
        defaultValue: String? = null,
    ): ReadWriteProperty<Any?, String?> =
        object : ReadWriteProperty<Any?, String?> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): String? = getString(keyResId, defaultValue) ?: defaultValue

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: String?,
            ) {
                putString(keyResId, value)
            }
        }

    @VisibleForTesting
    fun intPref(
        @StringRes keyResId: Int,
        defaultValue: Int,
    ): ReadWriteProperty<Any?, Int> =
        object : ReadWriteProperty<Any?, Int> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): Int = getInt(keyResId, defaultValue)

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: Int,
            ) {
                putInt(keyResId, value)
            }
        }

    @VisibleForTesting
    fun longPref(
        @StringRes keyResId: Int,
        defaultValue: Long,
    ): ReadWriteProperty<Any?, Long> =
        object : ReadWriteProperty<Any?, Long> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): Long = getLong(keyResId, defaultValue)

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: Long,
            ) {
                putLong(keyResId, value)
            }
        }

    @VisibleForTesting
    fun <E> enumPref(
        @StringRes keyResId: Int,
        defaultValue: E,
    ): ReadWriteProperty<Any?, E> where E : Enum<E>, E : PrefEnum =
        object : ReadWriteProperty<Any?, E> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): E = getEnum(keyResId, defaultValue)

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: E,
            ) {
                putEnum(keyResId, value)
            }
        }

    // ****************************************************************************************** //
    // **************************************** Settings **************************************** //
    // ****************************************************************************************** //

    // ****************************************** General ****************************************** //

    val exitViaDoubleTapBack by booleanPref(R.string.exit_via_double_tap_back_key, false)

    // ****************************************** Sync ****************************************** //

    val isAutoSyncEnabled by booleanPref(R.string.automatic_sync_choice_key, false)
    val displaySyncStatus by booleanPref(R.string.sync_status_badge_key, defaultValue = true)
    var allowSyncOnMeteredConnections by booleanPref(R.string.metered_sync_key, defaultValue = false)

    var username by stringPref(R.string.username_key)
    var hkey by stringPref(R.string.hkey_key)
    var currentSyncUri by stringPref(R.string.current_sync_uri_key)

    var lastSyncTime by longPref(R.string.last_sync_time_key, defaultValue = 0L)

    val shouldFetchMedia: ShouldFetchMedia
        get() = getEnum(R.string.sync_fetch_media_key, ShouldFetchMedia.ALWAYS)

    var networkTimeoutSecs by intPref(R.string.sync_io_timeout_secs_key, defaultValue = 60)

    //region Custom sync server

    val customSyncCertificate by stringPref(R.string.custom_sync_certificate_key)
    val customSyncUri by stringPref(R.string.custom_sync_server_collection_url_key)
    val isCustomSyncEnabled by booleanPref(R.string.custom_sync_server_enabled_key, defaultValue = false)
    var isBackgroundEnabled by booleanPref(R.string.pref_deck_picker_background_key, defaultValue = false)

    //endregion

    /**
     * Whether the sync process has requested notification permissions before.
     * We only want to request notification permissions for the sync feature if the dialog has never been shown
     * for this reason before.
     *
     * @see reminderNotifsRequestShown
     */
    var syncNotifsRequestShown by booleanPref(R.string.sync_notifs_request_shown_key, defaultValue = false)

    // ************************************** Review Reminders ********************************** //

    /**
     * Whether to enable the new review reminders notification system.
     */
    var newReviewRemindersEnabled by booleanPref(R.string.pref_new_review_reminders, false)

    /**
     * Review reminder IDs are unique, starting at 0 and climbing upwards by one each time a new one is created.
     */
    var reviewReminderNextFreeId by intPref(R.string.review_reminders_next_free_id, defaultValue = 0)

    /**
     * Whether the review reminder feature has requested notification permissions before.
     * We only want to request notification permissions for the review reminder feature if the dialog has never been
     * shown for this reason before.
     *
     * @see syncNotifsRequestShown
     */
    var reminderNotifsRequestShown by booleanPref(R.string.reminder_notifs_request_shown_key, defaultValue = false)

    /**
     * A list of all recent deserialization errors that have occurred when trying to load review reminders from storage.
     * For example, review reminders are deserialized and have their alarms scheduled when the device starts, but
     * if the deserialization process fails and no valid migrations are available, the error can be put into this string
     * so that the next time the user opens the app, an error dialog can be shown to inform them of the issue.
     */
    var reviewReminderDeserializationErrors by stringPref(R.string.review_reminder_deserialization_errors_key)

    // *************************************** Permissions ************************************** //

    // Flags for whether the system UI dialog for requesting certain permissions has been shown before.
    // If the user has viewed the dialog at least once, we should check if they pressed "don't ask again"
    // or pressed "deny" repeatedly (via [androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale]).
    // This is because trying to show the system dialog again after the user has indicated they don't want to see it
    // is likely tracked by Play Console statistics and may lead to lower Play Store discoverability.
    //
    // @see com.ichi2.anki.ui.windows.permissions.PermissionsFragment.requestPermissionThroughDialogOrSettings
    // @see com.ichi2.utils.Permissions.isUserOpenToPermission

    /**
     * Whether the system UI dialog for requesting notification permissions has been shown before.
     *
     * Flags like [reminderNotifsRequestShown] etc. are not enough because those flags check if
     * the BottomSheet dialog explaining the need for notification permissions has been shown before,
     * whereas this flag checks if the system dialog has been shown before.
     *
     * If the user restores their data from a backup or migrates to a new device, this flag may be true
     * when in reality notification permissions have not been requested for the device. This is most prominently
     * an issue for the review reminders feature, so to ensure the user is able to receive review reminder notifications after
     * a data restore / migration, a Snackbar noting that notification permissions are missing will be shown
     * on the [com.ichi2.anki.reviewreminders.ScheduleRemindersFragment] fragment if notification permissions are not granted.
     *
     * @see com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.checkForNotificationPermissions
     */
    var notificationsPermissionRequested by booleanPref(R.string.notifications_permission_requested_key, false)

    /**
     * Whether the system UI dialog for requesting audio recording permissions has been shown before.
     */
    var recordAudioPermissionRequested by booleanPref(R.string.record_audio_permission_requested_key, false)

    var internetPermissionRequested by booleanPref(R.string.internet_permission_requested_key, false)

    // **************************************** Reviewer **************************************** //

    val ignoreDisplayCutout by booleanPref(R.string.ignore_display_cutout_key, false)
    val autoFocusTypeAnswer by booleanPref(R.string.type_in_answer_focus_key, true)
    val showAnswerFeedback by booleanPref(R.string.show_answer_feedback_key, defaultValue = true)
    var showAnswerButtons by booleanPref(R.string.show_answer_buttons_key, true)
    val keepScreenOn by booleanPref(R.string.keep_screen_on_preference, defaultValue = false)
    val hideHardAndEasyButtons by booleanPref(R.string.hide_hard_and_easy_key, defaultValue = false)

    val doubleTapInterval by intPref(R.string.double_tap_timeout_pref_key, defaultValue = 200)
    val newStudyScreenAnswerButtonSize by intPref(R.string.answer_button_size_pref_key, defaultValue = 100)

    val swipeSensitivity: Float
        get() = getInt(R.string.pref_swipe_sensitivity_key, 100) / 100F

    var frameStyle: FrameStyle by enumPref(R.string.reviewer_frame_style_key, FrameStyle.CARD)
    val hideSystemBars: HideSystemBars by enumPref(R.string.hide_system_bars_key, HideSystemBars.NONE)
    var toolbarPosition: ToolbarPosition by enumPref(R.string.reviewer_toolbar_position_key, ToolbarPosition.TOP)

    //region Appearance

    var appTheme: AppTheme by enumPref(R.string.app_theme_key, AppTheme.FOLLOW_SYSTEM)
    var dayTheme: DayTheme by enumPref(R.string.day_theme_key, DayTheme.LIGHT)
    var nightTheme: NightTheme by enumPref(R.string.night_theme_key, NightTheme.BLACK)

    //endregion

    // **************************************** Controls **************************************** //
    //region Controls

    val tapGestureMode: TapGestureMode
        get() =
            when (getBoolean(R.string.gestures_corner_touch_preference, false)) {
                true -> TapGestureMode.NINE_POINT
                false -> TapGestureMode.FOUR_POINT
            }

    //endregion
    // ************************************** Accessibility ************************************* //

    val answerButtonsSize: Int by intPref(R.string.answer_button_size_preference, 100)
    val cardZoom: Int by intPref(R.string.card_zoom_preference, 100)
    val removeAppAnimations by booleanPref(R.string.safe_display_key, defaultValue = false)

    // **************************************** Advanced **************************************** //

    val isHtmlTypeAnswerEnabled by booleanPref(R.string.use_input_tag_key, defaultValue = false)
    var useFixedPortInReviewer by booleanPref(R.string.use_fixed_port_pref_key, false)

    var reviewerPort by intPref(R.string.reviewer_port_pref_key, defaultValue = 0)

    // ************************************* Developer options ********************************** //

    /**
     * Whether developer options should be shown to the user.
     * True in case [BuildConfig.DEBUG] is true
     * or if the user has enabled it with the secret on [com.ichi2.anki.preferences.AboutFragment]
     *
     * @see com.ichi2.anki.preferences.DeveloperOptionsFragment
     */
    var isDeveloperOptionsEnabled: Boolean
        get() = getBoolean(R.string.developer_options_enabled_by_user_key, false) || BuildConfig.DEBUG
        set(value) = putBoolean(R.string.developer_options_enabled_by_user_key, value)

    var isNewStudyScreenEnabled by booleanPref(R.string.new_reviewer_options_key, false)

    val devIsCardBrowserFragmented: Boolean
        get() = getBoolean(R.string.dev_card_browser_fragmented, false)

    @set:VisibleForTesting
    var devUsingCardBrowserSearchView: Boolean by booleanPref(R.string.dev_card_browser_search_view, false)

    val isWebDebugEnabled: Boolean
        get() = (getBoolean(R.string.html_javascript_debugging_key, false) || BuildConfig.DEBUG) && !isRunningAsUnitTest

    // ************************************* Switch Profile option ********************************** //

    /**
     * Whether the switch profile feature is enabled.
     */
    val switchProfileEnabled by booleanPref(R.string.pref_enable_switch_profile_key, false)

    // **************************************** UI Config *************************************** //

    /**
     * Get the SharedPreferences used for UI configuration such as Resizable layouts
     */
    fun getUiConfig(context: Context): SharedPreferences = context.getSharedPreferences(UI_CONFIG_PREFERENCES_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val UI_CONFIG_PREFERENCES_NAME = "ui-config"
    }
}
