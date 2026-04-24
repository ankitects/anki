/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.servicelayer

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.browser.BrowserColumnCollection
import com.ichi2.anki.browser.CardBrowserColumn.ANSWER
import com.ichi2.anki.browser.CardBrowserColumn.CARD
import com.ichi2.anki.browser.CardBrowserColumn.CHANGED
import com.ichi2.anki.browser.CardBrowserColumn.CREATED
import com.ichi2.anki.browser.CardBrowserColumn.DECK
import com.ichi2.anki.browser.CardBrowserColumn.DUE
import com.ichi2.anki.browser.CardBrowserColumn.EASE
import com.ichi2.anki.browser.CardBrowserColumn.EDITED
import com.ichi2.anki.browser.CardBrowserColumn.INTERVAL
import com.ichi2.anki.browser.CardBrowserColumn.LAPSES
import com.ichi2.anki.browser.CardBrowserColumn.NOTE_TYPE
import com.ichi2.anki.browser.CardBrowserColumn.QUESTION
import com.ichi2.anki.browser.CardBrowserColumn.REVIEWS
import com.ichi2.anki.browser.CardBrowserColumn.SFLD
import com.ichi2.anki.browser.CardBrowserColumn.TAGS
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.noteeditor.CustomToolbarButton
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.reviewer.ReviewerBinding.Companion.fromPreferenceString
import com.ichi2.utils.HashUtil.hashSetInit
import timber.log.Timber
import java.util.Locale
import kotlin.collections.ArrayList
import kotlin.math.round

private typealias VersionIdentifier = Int
private typealias LegacyVersionIdentifier = Long

object PreferenceUpgradeService {
    fun upgradePreferences(
        context: Context,
        previousVersionCode: LegacyVersionIdentifier,
    ): Boolean = upgradePreferences(context.sharedPrefs(), previousVersionCode)

    /** @return Whether any preferences were upgraded */
    internal fun upgradePreferences(
        preferences: SharedPreferences,
        previousVersionCode: LegacyVersionIdentifier,
    ): Boolean {
        val pendingPreferenceUpgrades = PreferenceUpgrade.getPendingUpgrades(preferences, previousVersionCode)

        pendingPreferenceUpgrades.forEach {
            it.performUpgrade(preferences)
        }

        return pendingPreferenceUpgrades.isNotEmpty()
    }

    /**
     * Specifies that no preference upgrades need to happen.
     * Typically because the app has been run for the first time, or the preferences
     * have been deleted
     */
    @JvmStatic // required for mockito for now
    fun setPreferencesUpToDate(preferences: SharedPreferences) {
        Timber.i("Marking preferences as up to date")
        PreferenceUpgrade.setPreferenceToLatestVersion(preferences)
    }

    abstract class PreferenceUpgrade private constructor(
        val versionIdentifier: VersionIdentifier,
    ) {
        /*
        To add a new preference upgrade:
         * yield a new class from getAllInstances (do not use `legacyPreviousVersionCode` in the constructor)
         * Implement the upgrade() method
         * Set mVersionIdentifier to 1 more than the previous versionIdentifier
         * Run tests in PreferenceUpgradeServiceTest
         */

        companion object {
            /** A version code where the value doesn't matter as we're not using the result */
            private const val IGNORED_LEGACY_VERSION_CODE = 0L
            const val UPGRADE_VERSION_PREF_KEY = "preferenceUpgradeVersion"

            /** Returns all instances of preference upgrade classes */
            private fun getAllInstances(legacyPreviousVersionCode: LegacyVersionIdentifier) =
                sequence {
                    yield(LegacyPreferenceUpgrade(legacyPreviousVersionCode))
                    yield(UpdateNoteEditorToolbarPrefs())
                    yield(UpgradeGesturesToControls())
                    yield(UpgradeDayAndNightThemes())
                    yield(UpgradeFetchMedia())
                    yield(UpgradeAppLocale())
                    yield(RemoveScrollingButtons())
                    yield(RemoveAnswerRecommended())
                    yield(RemoveBackupMax())
                    yield(RemoveInCardsMode())
                    yield(RemoveReviewerETA())
                    yield(SetShowDeckTitle())
                    yield(ResetAnalyticsOptIn())
                    yield(RemoveNoCodeFormatting())
                    yield(UpgradeBrowserColumns())
                    yield(RemoveLastExportedAtTime())
                    yield(RemoveLongTouchGesture())
                    yield(UpgradeDoubleTapTimeout())
                    yield(RemoveHostNum())
                    yield(UpgradeHideAnswerButtons())
                    yield(UpgradeToggleBacksideOnlyControl())
                    yield(UpgradeThemes())
                    yield(UpgradeAnswerControls())
                    yield(RemoveDeveloperFindReplace())
                }

            /** Returns a list of preference upgrade classes which have not been applied */
            fun getPendingUpgrades(
                preferences: SharedPreferences,
                legacyPreviousVersionCode: LegacyVersionIdentifier,
            ): List<PreferenceUpgrade> {
                val currentPrefVersion: VersionIdentifier = getPreferenceVersion(preferences)

                return getAllInstances(legacyPreviousVersionCode)
                    .filter {
                        it.versionIdentifier > currentPrefVersion
                    }.toList()
            }

            /** Sets the preference version such that no upgrades need to be applied */
            fun setPreferenceToLatestVersion(preferences: SharedPreferences) {
                val versionWhichRequiresNoUpgrades = getLatestVersion()
                setPreferenceVersion(preferences, versionWhichRequiresNoUpgrades)
            }

            internal fun getPreferenceVersion(preferences: SharedPreferences) = preferences.getInt(UPGRADE_VERSION_PREF_KEY, 0)

            internal fun setPreferenceVersion(
                preferences: SharedPreferences,
                versionIdentifier: VersionIdentifier,
            ) {
                Timber.i("upgrading preference version to '$versionIdentifier'")
                preferences.edit { putInt(UPGRADE_VERSION_PREF_KEY, versionIdentifier) }
            }

            /** Returns the collection of all preference version numbers */
            @VisibleForTesting
            fun getAllVersionIdentifiers(): Sequence<VersionIdentifier> =
                getAllInstances(IGNORED_LEGACY_VERSION_CODE).map { it.versionIdentifier }

            /**
             * @return the latest "version" of the preferences
             * If the preferences are set to this version, then no upgrades will take place
             */
            private fun getLatestVersion(): VersionIdentifier = getAllVersionIdentifiers().maxOrNull() ?: 0
        }

        /** Handles preference upgrades before 2021-08-01,
         * upgrades were detected via a version code comparison
         * rather than comparing a preference value
         */
        private class LegacyPreferenceUpgrade(
            val previousVersionCode: LegacyVersionIdentifier,
        ) : PreferenceUpgrade(1) {
            override fun upgrade(preferences: SharedPreferences) {
                if (!needsLegacyPreferenceUpgrade(previousVersionCode)) {
                    return
                }

                Timber.i("running upgradePreferences()")
                // clear all prefs if super old version to prevent any errors
                if (previousVersionCode < 20300130) {
                    Timber.i("Old version of Anki - Clearing preferences")
                    preferences.edit { clear() }
                }
                // when upgrading from before 2.5alpha35
                if (previousVersionCode < 20500135) {
                    Timber.i("Old version of Anki - Fixing Zoom")
                    // Card zooming behaviour was changed the preferences renamed
                    val oldCardZoom = preferences.getInt("relativeDisplayFontSize", 100)
                    val oldImageZoom = preferences.getInt("relativeImageSize", 100)
                    preferences.edit {
                        putInt("cardZoom", oldCardZoom)
                        putInt("imageZoom", oldImageZoom)
                    }
                    if (!preferences.getBoolean("useBackup", true)) {
                        preferences.edit { putInt("backupMax", 0) }
                    }
                    preferences.edit {
                        remove("useBackup")
                        remove("intentAdditionInstantAdd")
                    }
                }
                FullScreenMode.upgradeFromLegacyPreference(preferences)
            }

            fun needsLegacyPreferenceUpgrade(previous: Long): Boolean = previous < CHECK_PREFERENCES_AT_VERSION

            companion object {
                /**
                 * The latest package version number that included changes to the preferences that requires handling. All
                 * collections being upgraded to (or after) this version must update preferences.
                 *
                 * #9309 Do not modify this variable - it no longer works.
                 *
                 * Instead, add an unconditional check for the old preference before the call to
                 * "needsPreferenceUpgrade", and perform the upgrade.
                 */
                const val CHECK_PREFERENCES_AT_VERSION = 20500225
            }
        }

        fun performUpgrade(preferences: SharedPreferences) {
            Timber.i("Running preference upgrade: ${this.javaClass.simpleName}")
            upgrade(preferences)

            setPreferenceVersion(preferences, this.versionIdentifier)
        }

        protected abstract fun upgrade(preferences: SharedPreferences)

        /**
         * update toolbar buttons with new preferences, when button text empty or null then it adds the index as button text
         */
        internal class UpdateNoteEditorToolbarPrefs : PreferenceUpgrade(4) {
            override fun upgrade(preferences: SharedPreferences) {
                val buttons = getNewToolbarButtons(preferences)

                // update prefs
                preferences.edit {
                    remove("note_editor_custom_buttons")
                    putStringSet("note_editor_custom_buttons", CustomToolbarButton.toStringSet(buttons))
                }
            }

            private fun getNewToolbarButtons(preferences: SharedPreferences): ArrayList<CustomToolbarButton> {
                // get old toolbar prefs
                val set = preferences.getStringSet("note_editor_custom_buttons", hashSetInit<String>(0)) as Set<String?>
                // new list with buttons size
                val buttons = ArrayList<CustomToolbarButton>(set.size)

                // parse fields with separator
                for (s in set) {
                    val fields =
                        s!!
                            .split(
                                Consts.FIELD_SEPARATOR.toRegex(),
                                CustomToolbarButton.KEEP_EMPTY_ENTRIES.coerceAtLeast(0),
                            ).toTypedArray()
                    if (fields.size != 3) {
                        continue
                    }

                    val index: Int =
                        try {
                            fields[0].toInt()
                        } catch (e: Exception) {
                            Timber.w(e)
                            continue
                        }

                    // add new button with the index + 1 as button text
                    val visualIndex: Int = index + 1
                    val buttonText = visualIndex.toString()

                    // fields 1 is prefix, fields 2 is suffix
                    buttons.add(CustomToolbarButton(index, buttonText, fields[1], fields[2]))
                }
                return buttons
            }
        }

        internal class UpgradeGesturesToControls : PreferenceUpgrade(5) {
            val oldCommandValues =
                mapOf(
                    Pair(1, "binding_SHOW_ANSWER"),
                    Pair(2, "binding_FLIP_OR_ANSWER_EASE1"),
                    Pair(3, "binding_FLIP_OR_ANSWER_EASE2"),
                    Pair(4, "binding_FLIP_OR_ANSWER_EASE3"),
                    Pair(5, "binding_FLIP_OR_ANSWER_EASE4"),
                    Pair(8, "binding_UNDO"),
                    Pair(9, "binding_EDIT"),
                    Pair(10, "binding_MARK"),
                    Pair(12, "binding_BURY_CARD"),
                    Pair(13, "binding_SUSPEND_CARD"),
                    Pair(14, "binding_DELETE"),
                    Pair(16, "binding_PLAY_MEDIA"),
                    Pair(17, "binding_EXIT"),
                    Pair(18, "binding_BURY_NOTE"),
                    Pair(19, "binding_SUSPEND_NOTE"),
                    Pair(20, "binding_TOGGLE_FLAG_RED"),
                    Pair(21, "binding_TOGGLE_FLAG_ORANGE"),
                    Pair(22, "binding_TOGGLE_FLAG_GREEN"),
                    Pair(23, "binding_TOGGLE_FLAG_BLUE"),
                    Pair(38, "binding_TOGGLE_FLAG_PINK"),
                    Pair(39, "binding_TOGGLE_FLAG_TURQUOISE"),
                    Pair(40, "binding_TOGGLE_FLAG_PURPLE"),
                    Pair(24, "binding_UNSET_FLAG"),
                    Pair(30, "binding_PAGE_UP"),
                    Pair(31, "binding_PAGE_DOWN"),
                    Pair(32, "binding_TAG"),
                    Pair(33, "binding_CARD_INFO"),
                    Pair(35, "binding_RECORD_VOICE"),
                    Pair(36, "binding_REPLAY_VOICE"),
                    Pair(46, "binding_SAVE_VOICE"),
                    Pair(37, "binding_TOGGLE_WHITEBOARD"),
                    Pair(44, "binding_CLEAR_WHITEBOARD"),
                    Pair(45, "binding_CHANGE_WHITEBOARD_PEN_COLOR"),
                    Pair(41, "binding_SHOW_HINT"),
                    Pair(42, "binding_SHOW_ALL_HINTS"),
                    Pair(43, "binding_ADD_NOTE"),
                )

            override fun upgrade(preferences: SharedPreferences) {
                upgradeGestureToBinding(preferences, "gestureSwipeUp", Gesture.SWIPE_UP)
                upgradeGestureToBinding(preferences, "gestureSwipeDown", Gesture.SWIPE_DOWN)
                upgradeGestureToBinding(preferences, "gestureSwipeLeft", Gesture.SWIPE_LEFT)
                upgradeGestureToBinding(preferences, "gestureSwipeRight", Gesture.SWIPE_RIGHT)
                upgradeGestureToBinding(preferences, "gestureDoubleTap", Gesture.DOUBLE_TAP)
                upgradeGestureToBinding(preferences, "gestureTapTopLeft", Gesture.TAP_TOP_LEFT)
                upgradeGestureToBinding(preferences, "gestureTapTop", Gesture.TAP_TOP)
                upgradeGestureToBinding(preferences, "gestureTapTopRight", Gesture.TAP_TOP_RIGHT)
                upgradeGestureToBinding(preferences, "gestureTapLeft", Gesture.TAP_LEFT)
                upgradeGestureToBinding(preferences, "gestureTapCenter", Gesture.TAP_CENTER)
                upgradeGestureToBinding(preferences, "gestureTapRight", Gesture.TAP_RIGHT)
                upgradeGestureToBinding(
                    preferences,
                    "gestureTapBottomLeft",
                    Gesture.TAP_BOTTOM_LEFT,
                )
                upgradeGestureToBinding(preferences, "gestureTapBottom", Gesture.TAP_BOTTOM)
                upgradeGestureToBinding(
                    preferences,
                    "gestureTapBottomRight",
                    Gesture.TAP_BOTTOM_RIGHT,
                )
                upgradeVolumeGestureToBinding(
                    preferences,
                    "gestureVolumeUp",
                    KeyEvent.KEYCODE_VOLUME_UP,
                )
                upgradeVolumeGestureToBinding(
                    preferences,
                    "gestureVolumeDown",
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                )
            }

            private fun upgradeVolumeGestureToBinding(
                preferences: SharedPreferences,
                oldGesturePreferenceKey: String,
                volumeKeyCode: Int,
            ) {
                upgradeBinding(preferences, oldGesturePreferenceKey, keyCode(volumeKeyCode))
            }

            private fun upgradeGestureToBinding(
                preferences: SharedPreferences,
                oldGesturePreferenceKey: String,
                gesture: Gesture,
            ) {
                upgradeBinding(preferences, oldGesturePreferenceKey, Binding.gesture(gesture))
            }

            @VisibleForTesting
            internal fun upgradeBinding(
                preferences: SharedPreferences,
                oldGesturePreferenceKey: String,
                binding: Binding,
            ) {
                Timber.d("Replacing gesture '%s' with binding", oldGesturePreferenceKey)

                // This exists as a user may have mapped "volume down" to "UNDO".
                // Undo already exists as a key binding, and we don't want to trash this during an upgrade
                if (!preferences.contains(oldGesturePreferenceKey)) {
                    Timber.v("No preference to upgrade")
                    return
                }

                try {
                    replaceBinding(preferences, oldGesturePreferenceKey, binding)
                } finally {
                    Timber.v("removing pref key: '%s'", oldGesturePreferenceKey)
                    // remove the old key
                    preferences.edit { remove(oldGesturePreferenceKey) }
                }
            }

            private fun replaceBinding(
                preferences: SharedPreferences,
                oldGesturePreferenceKey: String,
                binding: Binding,
            ) {
                // the preference should be set, but if it's null, then we have nothing to do
                val pref = preferences.getString(oldGesturePreferenceKey, "0") ?: return
                // If the preference doesn't map (for example: it was removed), then nothing to do
                val asInt = pref.toIntOrNull() ?: return
                val command = oldCommandValues[asInt] ?: return

                Timber.i("Moving preference from '%s' to '%s'", oldGesturePreferenceKey, command)

                // add to the binding_COMMANDNAME preference
                val mappableBinding = ReviewerBinding(binding, CardSide.BOTH)
                val addAtEnd: (MutableList<MappableBinding>, MappableBinding) -> Boolean =
                    { collection, element ->
                        // do not reorder the elements
                        if (collection.contains(element)) {
                            false
                        } else {
                            collection.add(element)
                            true
                        }
                    }
                val bindings: MutableList<MappableBinding> = bindingFromPreference(preferences, command)
                addAtEnd(bindings, mappableBinding)
                val newValue: String = bindings.toPreferenceString()
                preferences.edit { putString(command, newValue) }
            }
        }

        internal class UpgradeDayAndNightThemes : PreferenceUpgrade(6) {
            override fun upgrade(preferences: SharedPreferences) {
                val dayTheme = preferences.getString("dayTheme", "0")
                val nightTheme = preferences.getString("nightTheme", "0")

                preferences.edit {
                    if (dayTheme == "1") { // plain
                        putString("dayTheme", "2")
                    } else { // light
                        putString("dayTheme", "1")
                    }
                    if (nightTheme == "1") { // dark
                        putString("nightTheme", "4")
                    } else { // black
                        putString("nightTheme", "3")
                    }
                    remove("invertedColors")
                }
            }
        }

        internal class UpgradeFetchMedia : PreferenceUpgrade(9) {
            override fun upgrade(preferences: SharedPreferences) {
                val fetchMediaSwitch = preferences.getBoolean(RemovedPreferences.SYNC_FETCHES_MEDIA, true)
                val status = if (fetchMediaSwitch) "always" else "never"
                preferences.edit {
                    remove(RemovedPreferences.SYNC_FETCHES_MEDIA)
                    putString("syncFetchMedia", status)
                }
            }
        }

        internal class UpgradeAppLocale : PreferenceUpgrade(10) {
            override fun upgrade(preferences: SharedPreferences) {
                fun getLocale(localeCode: String): Locale {
                    // Language separators are '_' or '-' at different times in display/resource fetch
                    val locale: Locale =
                        if (localeCode.contains("_") || localeCode.contains("-")) {
                            try {
                                val localeParts = localeCode.split("[_-]".toRegex(), 2).toTypedArray()
                                Locale.forLanguageTag(localeParts[0] + '-' + localeParts[1])
                            } catch (e: ArrayIndexOutOfBoundsException) {
                                Timber.w(e, "getLocale variant split fail, using code '%s' raw.", localeCode)
                                Locale.forLanguageTag(localeCode)
                            }
                        } else {
                            Locale.forLanguageTag(localeCode) // guaranteed to be non null
                        }
                    return locale
                }
                // 1. upgrade value from `locale.toString()` to `locale.toLanguageTag()`,
                // because the new API uses language tags
                val languagePrefValue = preferences.getString("language", "")!!
                val languageTag =
                    if (languagePrefValue.isNotEmpty()) {
                        getLocale(languagePrefValue).toLanguageTag()
                    } else {
                        null
                    }
                preferences.edit {
                    putString("language", languageTag ?: "")
                }
                // 2. Set the locale with the new AndroidX API
                val localeList = LocaleListCompat.forLanguageTags(languageTag)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }

        internal class RemoveScrollingButtons : PreferenceUpgrade(11) {
            override fun upgrade(preferences: SharedPreferences) {
                preferences.edit { remove("scrolling_buttons") }
            }
        }

        internal class RemoveAnswerRecommended : PreferenceUpgrade(12) {
            override fun upgrade(preferences: SharedPreferences) {
                moveControlBindings(preferences, "binding_FLIP_OR_ANSWER_RECOMMENDED", "binding_FLIP_OR_ANSWER_EASE3")
                moveControlBindings(
                    preferences,
                    "binding_FLIP_OR_ANSWER_BETTER_THAN_RECOMMENDED",
                    "binding_FLIP_OR_ANSWER_EASE4",
                )
            }

            private fun moveControlBindings(
                preferences: SharedPreferences,
                sourcePrefKey: String,
                destinyPrefKey: String,
            ) {
                val sourcePrefValue = preferences.getString(sourcePrefKey, null) ?: return
                val destinyPrefValue = preferences.getString(destinyPrefKey, null)

                val joinedBindings =
                    ReviewerBinding.fromPreferenceString(destinyPrefValue) +
                        ReviewerBinding.fromPreferenceString(sourcePrefValue)
                preferences.edit {
                    putString(destinyPrefKey, joinedBindings.toPreferenceString())
                    remove(sourcePrefKey)
                }
            }
        }

        /**
         * Switch from using a single backup option to using separate preferences for
         * daily/weekly/monthly as well as frequency of backups.
         */
        internal class RemoveBackupMax : PreferenceUpgrade(13) {
            override fun upgrade(preferences: SharedPreferences) {
                val legacyValue = preferences.getInt("backupMax", 4)
                preferences.edit {
                    remove("backupMax")
                    putInt("minutes_between_automatic_backups", 30) // 30 minutes default
                    putInt("daily_backups_to_keep", legacyValue)
                    putInt("weekly_backups_to_keep", legacyValue)
                    putInt("monthly_backups_to_keep", legacyValue)
                }
            }
        }

        /** We should have used [anki.config.ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE] */
        internal class RemoveInCardsMode : PreferenceUpgrade(14) {
            override fun upgrade(preferences: SharedPreferences) {
                preferences.edit {
                    remove("inCardsMode")
                }
            }
        }

        internal class RemoveReviewerETA : PreferenceUpgrade(15) {
            override fun upgrade(preferences: SharedPreferences) {
                // reverted: #15405
                // preferences.edit { remove("showETA") }
            }
        }

        /** default to true for existing users  */
        internal class SetShowDeckTitle : PreferenceUpgrade(16) {
            override fun upgrade(preferences: SharedPreferences) {
                if (!preferences.contains("showDeckTitle")) {
                    preferences.edit { putBoolean("showDeckTitle", true) }
                }
            }
        }

        /**
         * Issue 14386: Opening preferences opted users in to analytics in 2.16 due to an oversight
         *
         * Despite the fact that analytics were broken at the time due to Google's migration from
         * Universal Analytics to Google Analytics 4, we want analytics to STRICTLY be opt-in
         *
         * As we likely have inadvertent opt-ins, we stated that we would opt everyone out:
         * https://ankidroid.org/docs/changelog.html#_version_2_16_5_20230906
         *
         * We now use "analytics_opt_in"
         *
         * @see [UsageAnalytics.ANALYTICS_OPTIN_KEY]
         */
        internal class ResetAnalyticsOptIn : PreferenceUpgrade(17) {
            override fun upgrade(preferences: SharedPreferences) = preferences.edit { remove("analyticsOptIn") }
        }

        internal class RemoveNoCodeFormatting : PreferenceUpgrade(18) {
            override fun upgrade(preferences: SharedPreferences) = preferences.edit { remove("noCodeFormatting") }
        }

        internal class UpgradeBrowserColumns : PreferenceUpgrade(19) {
            override fun upgrade(preferences: SharedPreferences) {
                // Columns were stored as an index into COLUMN[1/2]_KEYS
                // This produced a CardBrowserColumn object, and the index was used as an index
                // into a string array

                // This has a number of issues:
                // * Cards Mode and Notes Mode uses the same column definitions
                // * The index was opaque: 0 meant different things in column 1 and column 2
                // * COLUMN[N]_KEYS differed from the available columns in Anki Desktop
                // * A user could only select two columns, even on a Tablet/Chromebook/TV

                // To improve this, we define: CardBrowserColumnCollection
                // This uses 1 preference for cards or notes mode, rather than 1 preference per
                // column

                // The values are now equivalent to the keys which are sent to Anki Desktop
                // and allow an arbitrary ordering and number of values
                // "activeNoteCols" -> "question|cardEase"

                fun clearLegacyKeys() {
                    Timber.d("removing legacy keys")
                    preferences.edit {
                        remove(DISPLAY_COLUMN_1_KEY)
                        remove(DISPLAY_COLUMN_2_KEY)
                    }
                }

                val currentColumn1Index = preferences.getInt(DISPLAY_COLUMN_1_KEY, -1)
                val currentColumn2Index = preferences.getInt(DISPLAY_COLUMN_2_KEY, -1)

                if (currentColumn1Index == -1 || currentColumn2Index == -1) {
                    Timber.d("no update needed")
                    clearLegacyKeys()
                    return
                }

                val currentColumn1 = LEGACY_COLUMN1_KEYS[currentColumn1Index]
                val currentColumn2 = LEGACY_COLUMN2_KEYS[currentColumn2Index]

                BrowserColumnCollection.update(preferences, CardsOrNotes.CARDS) { columns ->
                    if (columns.size < 2) return@update false
                    Timber.d("upgrading browser 'cards' columns")
                    columns[0] = currentColumn1
                    columns[1] = currentColumn2
                    true
                }

                BrowserColumnCollection.update(preferences, CardsOrNotes.NOTES) { columns ->
                    if (columns.size < 2) return@update false
                    Timber.d("upgrading browser 'notes' columns")
                    columns[0] = currentColumn1
                    columns[1] = currentColumn2
                    true
                }

                clearLegacyKeys()
            }

            companion object {
                private const val DISPLAY_COLUMN_1_KEY = "cardBrowserColumn1"
                private const val DISPLAY_COLUMN_2_KEY = "cardBrowserColumn2"

                @VisibleForTesting
                internal val LEGACY_COLUMN1_KEYS = arrayOf(QUESTION, SFLD)

                @VisibleForTesting
                internal val LEGACY_COLUMN2_KEYS =
                    arrayOf(ANSWER, CARD, DECK, NOTE_TYPE, QUESTION, TAGS, LAPSES, REVIEWS, INTERVAL, EASE, DUE, CHANGED, CREATED, EDITED)
            }
        }

        internal class RemoveLastExportedAtTime : PreferenceUpgrade(20) {
            override fun upgrade(preferences: SharedPreferences) {
                preferences.edit {
                    remove("last_successful_export_mod")
                    remove("last_successful_export_second")
                }
            }
        }

        @NeedsTest("long touch gesture is removed from preferences")
        internal class RemoveLongTouchGesture : PreferenceUpgrade(21) {
            private val keys =
                listOf(
                    "binding_SHOW_ANSWER",
                    "binding_FLIP_OR_ANSWER_EASE1",
                    "binding_FLIP_OR_ANSWER_EASE2",
                    "binding_FLIP_OR_ANSWER_EASE3",
                    "binding_FLIP_OR_ANSWER_EASE4",
                    "binding_UNDO",
                    "binding_REDO",
                    "binding_EDIT",
                    "binding_MARK",
                    "binding_BURY_CARD",
                    "binding_SUSPEND_CARD",
                    "binding_DELETE",
                    "binding_PLAY_MEDIA",
                    "binding_EXIT",
                    "binding_BURY_NOTE",
                    "binding_SUSPEND_NOTE",
                    "binding_TOGGLE_FLAG_RED",
                    "binding_TOGGLE_FLAG_ORANGE",
                    "binding_TOGGLE_FLAG_GREEN",
                    "binding_TOGGLE_FLAG_BLUE",
                    "binding_TOGGLE_FLAG_PINK",
                    "binding_TOGGLE_FLAG_TURQUOISE",
                    "binding_TOGGLE_FLAG_PURPLE",
                    "binding_UNSET_FLAG",
                    "binding_PAGE_UP",
                    "binding_PAGE_DOWN",
                    "binding_TAG",
                    "binding_CARD_INFO",
                    "binding_PREVIOUS_CARD_INFO",
                    "binding_RECORD_VOICE",
                    "binding_SAVE_VOICE",
                    "binding_REPLAY_VOICE",
                    "binding_TOGGLE_WHITEBOARD",
                    "binding_TOGGLE_ERASER",
                    "binding_CLEAR_WHITEBOARD",
                    "binding_CHANGE_WHITEBOARD_PEN_COLOR",
                    "binding_SHOW_HINT",
                    "binding_SHOW_ALL_HINTS",
                    "binding_ADD_NOTE",
                    "binding_RESCHEDULE_NOTE",
                    "binding_TOGGLE_AUTO_ADVANCE",
                    "binding_USER_ACTION_1",
                    "binding_USER_ACTION_2",
                    "binding_USER_ACTION_3",
                    "binding_USER_ACTION_4",
                    "binding_USER_ACTION_5",
                    "binding_USER_ACTION_6",
                    "binding_USER_ACTION_7",
                    "binding_USER_ACTION_8",
                    "binding_USER_ACTION_9",
                )

            override fun upgrade(preferences: SharedPreferences) {
                for (key in keys) {
                    val value = preferences.getString(key, null) ?: continue
                    val bindings = fromPreferenceString(value)
                    val unknown = bindings.filter { it.binding is Binding.UnknownBinding }
                    if (unknown.isEmpty()) continue
                    val newBindings = bindings - unknown
                    preferences.edit {
                        putString(key, newBindings.toPreferenceString())
                    }
                }
            }
        }

        internal class UpgradeDoubleTapTimeout : PreferenceUpgrade(22) {
            override fun upgrade(preferences: SharedPreferences) {
                val oldPrefKey = "doubleTapTimeInterval"
                val value = preferences.getInt(oldPrefKey, -1)
                if (value == -1) return
                val newValue =
                    if (value > 1000) {
                        1000
                    } else {
                        val result = value / 10.0
                        val roundedResult = round(result)
                        (roundedResult * 10).toInt()
                    }
                preferences.edit {
                    remove(oldPrefKey)
                    putInt("doubleTapTimeout", newValue)
                }
            }
        }

        internal class RemoveHostNum : PreferenceUpgrade(23) {
            override fun upgrade(preferences: SharedPreferences) {
                preferences.edit {
                    remove("hostNum")
                }
            }
        }

        internal class UpgradeHideAnswerButtons : PreferenceUpgrade(24) {
            override fun upgrade(preferences: SharedPreferences) {
                val oldPrefKey = "hideAnswerButtons"
                val value = preferences.getBoolean(oldPrefKey, false)
                preferences.edit {
                    remove(oldPrefKey)
                    putBoolean("showAnswerButtons", !value)
                }
            }
        }

        internal class UpgradeToggleBacksideOnlyControl : PreferenceUpgrade(25) {
            override fun upgrade(preferences: SharedPreferences) {
                val oldPrefKey = "previewer_BACKSIDE_ONLY"
                val value = preferences.getString(oldPrefKey, null) ?: return
                preferences.edit {
                    remove(oldPrefKey)
                    putString("previewer_TOGGLE_BACKSIDE_ONLY", value)
                }
            }
        }

        internal class UpgradeThemes : PreferenceUpgrade(26) {
            companion object {
                const val KEY_APP_THEME = "appTheme"
                const val KEY_DAY_THEME = "dayTheme"
                const val KEY_NIGHT_THEME = "nightTheme"

                const val THEME_FOLLOW_SYSTEM = "0"
                const val THEME_LIGHT = "1"
                const val THEME_PLAIN = "2"
                const val THEME_BLACK = "3"
                const val THEME_DARK = "4"

                const val THEME_DAY = "1"
                const val THEME_NIGHT = "2"
            }

            @Suppress("MoveVariableDeclarationIntoWhen")
            override fun upgrade(preferences: SharedPreferences) {
                val appTheme = preferences.getString(KEY_APP_THEME, THEME_FOLLOW_SYSTEM)

                when (appTheme) {
                    THEME_FOLLOW_SYSTEM -> return
                    THEME_LIGHT, THEME_PLAIN -> {
                        preferences.edit {
                            putString(KEY_APP_THEME, THEME_DAY)
                            putString(KEY_DAY_THEME, appTheme)
                        }
                    }
                    THEME_BLACK, THEME_DARK -> {
                        preferences.edit {
                            putString(KEY_APP_THEME, THEME_NIGHT)
                            putString(KEY_NIGHT_THEME, appTheme)
                        }
                    }
                }
            }
        }

        class UpgradeAnswerControls : PreferenceUpgrade(27) {
            override fun upgrade(preferences: SharedPreferences) {
                val keysMap =
                    mapOf(
                        "binding_FLIP_OR_ANSWER_EASE1" to "binding_ANSWER_AGAIN",
                        "binding_FLIP_OR_ANSWER_EASE2" to "binding_ANSWER_HARD",
                        "binding_FLIP_OR_ANSWER_EASE3" to "binding_ANSWER_GOOD",
                        "binding_FLIP_OR_ANSWER_EASE4" to "binding_ANSWER_EASY",
                    )
                val showAnswerBindings =
                    fromPreferenceString(
                        preferences.getString(
                            "binding_SHOW_ANSWER",
                            "",
                        ),
                    ).toMutableList()

                for ((key, newKey) in keysMap.entries) {
                    val value = preferences.getString(key, null) ?: continue
                    val currentBindings = fromPreferenceString(value).toMutableList()
                    val bindings = currentBindings.toMutableList()

                    currentBindings.forEach { binding ->
                        when (binding.side) {
                            CardSide.QUESTION -> {
                                if (!showAnswerBindings.any { it.binding == binding.binding }) {
                                    showAnswerBindings.append(binding)
                                }
                                bindings.remove(binding)
                            }
                            CardSide.ANSWER -> {}
                            CardSide.BOTH -> {
                                showAnswerBindings.append(
                                    ReviewerBinding(
                                        binding.binding,
                                        CardSide.QUESTION,
                                    ),
                                )
                                bindings.remove(binding)
                                bindings.append(ReviewerBinding(binding.binding, CardSide.ANSWER))
                            }
                        }
                    }
                    preferences.edit {
                        remove(key)
                        putString(newKey, bindings.toPreferenceString())
                    }
                }
                preferences.edit {
                    putString("binding_SHOW_ANSWER", showAnswerBindings.toPreferenceString())
                }
            }
        }

        internal class RemoveDeveloperFindReplace : PreferenceUpgrade(28) {
            override fun upgrade(preferences: SharedPreferences) {
                preferences.edit {
                    remove("browserFindReplace")
                }
            }
        }
    }
}

object RemovedPreferences {
    const val SYNC_FETCHES_MEDIA = "syncFetchesMedia"
}

fun bindingFromPreference(
    preferences: SharedPreferences,
    key: String,
): MutableList<MappableBinding> {
    val value =
        preferences.getString(key, null)
            ?: return ViewerAction.entries
                .firstOrNull { it.preferenceKey == key }
                ?.getBindings(preferences)
                ?.toMutableList()
                ?: mutableListOf()
    return fromPreferenceString(value).toMutableList()
}
