/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.R
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.settings.Prefs

/**
 * Fragment with preferences related to notifications
 */
class AccessibilitySettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_accessibility
    override val analyticsScreenNameConstant: String
        get() = "prefs.accessibility"

    override fun initSubscreen() {
        setupNewStudyScreenSettings()
    }

    private fun setupNewStudyScreenSettings() {
        if (!Prefs.isNewStudyScreenEnabled) return

        requirePreference<Preference>(R.string.answer_button_size_pref_key).isVisible = true

        for (key in legacyStudyScreenSettings) {
            val keyString = getString(key)
            findPreference<Preference>(keyString)?.isVisible = false
        }
    }

    override fun onResume() {
        super.onResume()
        requirePreference<SwitchPreferenceCompat>(R.string.safe_display_key).isEnabled =
            Animations.areSystemAnimationsEnabled(requireContext())
    }

    companion object {
        val legacyStudyScreenSettings =
            listOf(
                R.string.image_zoom_preference,
                R.string.show_large_answer_buttons_preference,
                R.string.pref_card_minimal_click_time,
                R.string.answer_button_size_preference,
                R.string.double_tap_timeout_pref_key, // TODO implement it in the new study screen
            )
    }
}
