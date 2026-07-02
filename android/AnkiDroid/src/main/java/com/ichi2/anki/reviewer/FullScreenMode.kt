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
package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import androidx.core.content.edit
import timber.log.Timber

/** Whether Reviewer content should take the full screen */
enum class FullScreenMode(
    private val prefValue: String,
) {
    /** Display both navigation and buttons (default) */
    BUTTONS_AND_MENU("0"),

    /** Remove the menu bar, keeps answer button. */
    BUTTONS_ONLY("1"),

    /** Remove both menu bar and buttons. Can only be set if gesture is on. */
    FULLSCREEN_ALL_GONE("2"),
    ;

    fun getPreferenceValue() = prefValue

    fun isFullScreenReview() = this != BUTTONS_AND_MENU

    companion object {
        const val PREF_KEY = "fullscreenMode"
        val DEFAULT = BUTTONS_AND_MENU

        fun fromPreference(prefs: SharedPreferences): FullScreenMode {
            val value = prefs.getString(PREF_KEY, DEFAULT.prefValue)
            return enumValues<FullScreenMode>().firstOrNull { it.prefValue == value } ?: DEFAULT
        }

        fun isFullScreenReview(prefs: SharedPreferences): Boolean = fromPreference(prefs).isFullScreenReview()

        fun upgradeFromLegacyPreference(preferences: SharedPreferences) {
            if (!preferences.contains("fullscreenReview")) return

            Timber.i("Old version of Anki - Fixing Fullscreen")
            // clear fullscreen flag as we use a integer
            val fullScreenModeKey = PREF_KEY
            val old = preferences.getBoolean("fullscreenReview", false)
            val newValue = if (old) BUTTONS_ONLY else BUTTONS_AND_MENU
            preferences.edit {
                putString(fullScreenModeKey, newValue.getPreferenceValue())
                remove("fullscreenReview")
            }
        }

        fun setPreference(
            prefs: SharedPreferences,
            mode: FullScreenMode,
        ) {
            prefs.edit { putString(PREF_KEY, mode.getPreferenceValue()) }
        }
    }
}
