// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CommonPreferenceKey
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.TapGestureMode
import com.ichi2.anki.common.preferences.AnimationPreferences
import com.ichi2.anki.common.preferences.NotificationPreferences
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.isRunningAsUnitTest
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.preferences.SharedPreferencesProvider
import com.ichi2.anki.preferences.TimeboxTimeLimit
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
// Resolve both dependencies from the current application rather than retaining a previous instance.
object Prefs : PrefsRepository(AnkiDroidApp.sharedPreferencesProvider, { AnkiDroidApp.appResources })

// TODO: enforce that `preferences.xml` is used
open class PrefsRepository protected constructor(
    private val preferences: SharedPreferencesProvider,
    private val resourcesProvider: () -> Resources,
) : AnimationPreferences,
    NotificationPreferences {
    constructor(sharedPrefs: SharedPreferences, resources: Resources) : this(SharedPreferencesProvider { sharedPrefs }, { resources })

    constructor(context: Context) : this(context.sharedPrefs(), context.resources)

    private val resources: Resources get() = resourcesProvider()

    val sharedPrefs: SharedPreferences
        get() = preferences.sharedPrefs()

    @VisibleForTesting
    fun key(
        @StringRes resId: Int,
    ): String = resources.getString(resId)

    private fun getBoolean(
        @StringRes keyResId: Int,
        defValue: Boolean,
    ): Boolean = sharedPrefs.getBoolean(key(keyResId), defValue)

    private fun putBoolean(
        @StringRes keyResId: Int,
        value: Boolean,
    ) {
        sharedPrefs.edit { putBoolean(key(keyResId), value) }
    }

    private fun getString(
        @StringRes keyResId: Int,
        defValue: String?,
    ): String? = sharedPrefs.getString(key(keyResId), defValue)

    private fun putString(
        @StringRes keyResId: Int,
        value: String?,
    ) {
        sharedPrefs.edit { putString(key(keyResId), value) }
    }

    private fun getInt(
        @StringRes keyResId: Int,
        defValue: Int,
    ): Int = sharedPrefs.getInt(key(keyResId), defValue)

    private fun putInt(
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

    private fun <E> getEnum(
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

    private fun <E> putEnum(
        @StringRes keyResId: Int,
        value: E,
    ) where E : Enum<E>, E : PrefEnum {
        val stringValue = resources.getString(value.entryResId)
        putString(keyResId, stringValue)
    }

    // **************************************** Delegates *************************************** //

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

    fun booleanPref(
        @StringRes keyResId: Int,
        defaultValue: Boolean,
    ): ReadWriteProperty<Any?, Boolean> = booleanPref(key(keyResId), defaultValue)

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

    @JvmName("nonNullStringPref")
    fun stringPref(
        @StringRes keyResId: Int,
        defaultValue: String,
    ): ReadWriteProperty<Any?, String> =
        object : ReadWriteProperty<Any?, String> {
            override fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ): String = getString(keyResId, defaultValue) ?: defaultValue

            override fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: String,
            ) {
                putString(keyResId, value)
            }
        }

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

    // ****************************************** Editing ****************************************** //

    /**
     * Last deck successfully used when saving note data from a 'share' intent,
     * or zero if no destination has been saved.
     */
    var lastSharedDeckId: DeckId by longPref(R.string.last_shared_deck_id_key, defaultValue = 0L)

    // ****************************************** Sync ****************************************** //

    @set:VisibleForTesting
    var isAutoSyncEnabled by booleanPref(R.string.automatic_sync_choice_key, false)
    val displaySyncStatus by booleanPref(R.string.sync_status_badge_key, defaultValue = true)
    var allowSyncOnMeteredConnections by booleanPref(R.string.metered_sync_key, defaultValue = false)

    var username by stringPref(R.string.username_key)
    var hkey by stringPref(R.string.hkey_key)
    var currentSyncUri by stringPref(R.string.current_sync_uri_key)

    /**
     * End time of the last manual or automatic sync attempt, in epoch milliseconds.
     * Rate-limits foreground auto-sync and sync intents, including failed, canceled and no-change attempts.
     *
     * If a request is blocked before starting (rate limit; metered connection etc...) do not update it,
     * so it can try again once the block is lifted.
     */
    var lastSyncTime by longPref(R.string.last_sync_time_key, defaultValue = 0L)

    @set:VisibleForTesting
    var shouldFetchMedia: ShouldFetchMedia by enumPref(R.string.sync_fetch_media_key, ShouldFetchMedia.ALWAYS)

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
    override var newReviewRemindersEnabled by booleanPref(CommonPreferenceKey.pref_new_review_reminders, defaultValue = false)

    private val minimumCardsDueForNotification by stringPref(CommonPreferenceKey.pref_notifications_minimum_cards_due_key, "1000000")

    override val legacyNotificationsEnabled: Boolean
        get() = minimumCardsDueForNotification.toInt() < 1000000

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

    // TODO: "Has the app done X" flags might not belong in the common SharedPrefs file and should maybe be pulled out.
    // Storing these flags somewhat diverges from the intention of SharedPrefs, which is intended for user preferences.
    // Cleaner organization / storage of these flags may be possible.

    /**
     * Whether the bottom sheet for requesting notification permissions has been shown to the user, but only for
     * API <33. Why is this required? On API 33+, we continue showing the bottom sheet until the OS tells us we should not show it anymore
     * (i.e. the user has denied the permission and checked "Don't ask again", or has denied the permission enough times).
     * However, on API <33, no explicit OS notification permission is available, but the user can still disable notifications for the app in the OS settings.
     * The bottom sheet should therefore still be shown. Since there is no longer OS feedback on whether the user has denied
     * the request in the past, we need to keep track of this ourselves to avoid spamming the user with the bottom sheet over and over.
     *
     * Technically, we could just reuse [notificationsPermissionRequested] for this purpose. However, that makes
     * [notificationsPermissionRequested] have two purposes and blurs its clear definition as "has the OS system dialog for requesting
     * notifications been presented to the user before". On API <33, the OS system dialog for requesting notifications does not exist,
     * and so technically [notificationsPermissionRequested] should always be false. Therefore, we keep this separate.
     */
    var notificationsBottomSheetShownBelowAPI33 by booleanPref(R.string.notifications_bottom_sheet_below_33, defaultValue = false)

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
     */
    var notificationsPermissionRequested by booleanPref(R.string.notifications_permission_requested_key, false)

    /**
     * Whether the system UI dialog for requesting audio recording permissions has been shown before.
     */
    var recordAudioPermissionRequested by booleanPref(R.string.record_audio_permission_requested_key, false)

    // **************************************** Reviewer **************************************** //

    val ignoreDisplayCutout by booleanPref(R.string.ignore_display_cutout_key, false)
    val autoFocusTypeAnswer by booleanPref(R.string.type_in_answer_focus_key, true)
    val showAnswerFeedback by booleanPref(R.string.show_answer_feedback_key, defaultValue = true)
    var showAnswerButtons by booleanPref(R.string.show_answer_buttons_key, true)
    val keepScreenOn by booleanPref(R.string.keep_screen_on_preference, defaultValue = false)
    var hideHardAndEasyButtons by booleanPref(R.string.hide_hard_and_easy_key, defaultValue = false)
    var answerButtonsUseThemeColors by booleanPref(R.string.answer_buttons_theme_colors_key, defaultValue = false)

    @set:VisibleForTesting
    var doubleTapInterval by intPref(R.string.double_tap_timeout_pref_key, defaultValue = 200)
    var newStudyScreenAnswerButtonSize by intPref(R.string.answer_button_size_pref_key, defaultValue = 100)
    var customTimeboxLimit by intPref(
        R.string.custom_timebox_limit_key,
        defaultValue = TimeboxTimeLimit.DEFAULT_TIMEBOX_MINUTES,
    )

    private val swipeSensitivityPercent by intPref(R.string.pref_swipe_sensitivity_key, 100)
    val swipeSensitivity: Float
        get() = swipeSensitivityPercent / 100F

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

    private val isCornerTouchEnabled by booleanPref(R.string.gestures_corner_touch_preference, false)
    val tapGestureMode: TapGestureMode
        get() =
            when (isCornerTouchEnabled) {
                true -> TapGestureMode.NINE_POINT
                false -> TapGestureMode.FOUR_POINT
            }

    //endregion
    // ************************************** Accessibility ************************************* //

    val answerButtonsSize: Int by intPref(R.string.answer_button_size_preference, 100)
    val cardZoom: Int by intPref(R.string.card_zoom_preference, 100)
    override val removeAppAnimations by booleanPref(R.string.safe_display_key, defaultValue = false)

    // **************************************** Advanced **************************************** //

    var useSoftwareRendering by booleanPref(R.string.disable_hardware_render_key, defaultValue = false)
    val isHtmlTypeAnswerEnabled by booleanPref(R.string.use_input_tag_key, defaultValue = false)
    var allowTemplatesToRecordAudio by booleanPref(R.string.pref_allow_template_audio_recording, false)

    // ************************************* Developer options ********************************** //

    /**
     * Whether developer options should be shown to the user.
     * True in case [BuildConfig.DEBUG] is true
     * or if the user has enabled it with the secret on [com.ichi2.anki.preferences.AboutFragment]
     *
     * @see com.ichi2.anki.preferences.DeveloperOptionsFragment
     */
    private var isDeveloperOptionsEnabledByUser by booleanPref(R.string.developer_options_enabled_by_user_key, false)
    var isDeveloperOptionsEnabled: Boolean
        get() = isDeveloperOptionsEnabledByUser || BuildConfig.DEBUG
        set(value) {
            isDeveloperOptionsEnabledByUser = value
        }

    var isNewStudyScreenEnabled by booleanPref(R.string.new_reviewer_options_key, false)

    @set:VisibleForTesting
    var devIsCardBrowserFragmented: Boolean by booleanPref(R.string.dev_card_browser_fragmented, false)

    @set:VisibleForTesting
    var devBottomNavEnabled: Boolean by booleanPref(R.string.dev_bottom_nav_key, false)

    @set:VisibleForTesting
    var devMaterialYouEnabled: Boolean by booleanPref(R.string.dev_material_you_key, false)

    @set:VisibleForTesting
    var devUsingCardBrowserSearchView: Boolean by booleanPref(R.string.dev_card_browser_search_view, false)

    @set:VisibleForTesting
    var devUsingCardBrowserSearchTools: Boolean by booleanPref(R.string.dev_card_browser_search_tools, false)

    private val isWebDebugEnabledByUser by booleanPref(R.string.html_javascript_debugging_key, false)
    val isWebDebugEnabled: Boolean
        get() = (isWebDebugEnabledByUser || BuildConfig.DEBUG) && !isRunningAsUnitTest

    // ************************************* Switch Profile option ********************************** //

    /**
     * Whether the switch profile feature is enabled.
     */
    val switchProfileEnabled by booleanPref(R.string.pref_enable_switch_profile_key, false)

    // ************************************** Card Browser ************************************** //

    /**
     * Whether the Card Browser should use an efficient 'no sorting' mode when displaying results
     *
     * **AnkiDroid Only**
     *
     * TODO: This should differentiate cards & notes mode
     */
    var cardBrowserNoSorting by booleanPref(R.string.pref_browser_no_sorting, false)
}
