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

import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class CustomButtonsSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_custom_buttons
    override val analyticsScreenNameConstant: String
        get() = "prefs.custom_buttons"

    override fun initSubscreen() {
        setDynamicTitles()

        // Reset toolbar button customizations
        val resetCustomButtons = requirePreference<Preference>("reset_custom_buttons")
        resetCustomButtons.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                AlertDialog.Builder(requireContext()).show {
                    title(R.string.reset_settings_to_default)
                    positiveButton(R.string.reset) {
                        // Reset the settings to default
                        requireContext().sharedPrefs().edit {
                            allKeys().forEach {
                                remove(it)
                            }
                        }
                        // #9263: refresh the screen to display the changes
                        preferenceScreen.removeAll()
                        addPreferencesFromResource(preferenceResource)
                        initSubscreen()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
                true
            }
    }

    private fun setDynamicTitles() {
        findPreference<ListPreference>(getString(R.string.custom_button_flag_key))?.title = TR.browsingFlag()
        findPreference<ListPreference>(getString(R.string.custom_button_card_info_key))?.title = TR.sentenceCase.cardInfo
        findPreference<ListPreference>(getString(R.string.custom_button_bury_key))?.title = TR.studyingBury()
        findPreference<ListPreference>(getString(R.string.custom_button_suspend_key))?.title = TR.studyingSuspend()
        findPreference<ListPreference>(getString(R.string.custom_button_mark_card_key))?.title = TR.sentenceCase.markNote
        findPreference<ListPreference>(getString(R.string.custom_button_previous_card_info_key))?.title = TR.sentenceCase.previousCardInfo
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun allKeys(): HashSet<String> = preferenceScreen.allPreferences().mapTo(hashSetOf()) { it.key }
}
