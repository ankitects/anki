// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.preferences

import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
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
        findPreference<ListPreference>(getString(R.string.custom_button_delete_key))?.title = TR.sentenceCase.deleteNote
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun allKeys(): HashSet<String> = preferenceScreen.allPreferences().mapTo(hashSetOf()) { it.key }
}
