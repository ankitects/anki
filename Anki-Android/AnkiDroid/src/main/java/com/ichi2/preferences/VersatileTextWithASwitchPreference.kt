/*
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.R

/**
 * A combination of an EditTextPreference and a SwitchPreference.
 * It mostly looks like a SwitchPreference, but the switch and the title can be clicked separately.
 * If some text is set, it is possible to have the switch either of on off.
 * However, if text is null or empty, the switch can only be in the off state.
 *
 * The text value is stored with the key of the preference.
 * The key of the switch is suffixed with [SWITCH_SUFFIX].
 *
 * Notes on behavior:
 *   * If text is present, tapping on the switch toggles it.
 *   * If text is empty, tapping on the switch will open the dialog, as if the title was tapped.
 *   * If the dialog is closed with Cancel, no changes happen.
 *   * If the dialog is closed with the positive button, and text is present, the switch will be set to on.
 *   * If the dialog is closed with the positive button, and text is empty, the switch will be set to off.
 *
 * The preference inherits from [VersatileTextPreference] and supports any attributes it does,
 * including the regular [EditTextPreference] attributes.
 */
class VersatileTextWithASwitchPreference(
    context: Context,
    attrs: AttributeSet?,
) : VersatileTextPreference(context, attrs),
    DialogFragmentProvider {
    init {
        widgetLayoutResource = R.layout.preference_widget_switch_with_separator
    }

    private val preferences get() = sharedPreferences!!

    private val switchKey get() = key + SWITCH_SUFFIX

    private val canBeSwitchedOn get() = !text.isNullOrEmpty()

    // * If there is no text, we make the switch not focusable and not clickable.
    //   This is exactly how SwitchPreference sets up its widget;
    //   if you tap on the switch, it will behave as if you tapped on the preference itself.
    // * If there is text, the switch can be tapped separately.
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        with(holder.findViewById(R.id.switch_widget) as MaterialSwitch) {
            isFocusable = canBeSwitchedOn
            isClickable = canBeSwitchedOn
            isChecked = preferences.getBoolean(switchKey, false)
            setOnCheckedChangeListener { _, checked ->
                preferences.edit { putBoolean(switchKey, checked) }
            }
        }
    }

    fun onDialogClosedAndNewTextSet() {
        preferences.edit { putBoolean(switchKey, canBeSwitchedOn) }
    }

    override fun makeDialogFragment() = VersatileTextWithASwitchPreferenceDialogFragment()

    companion object {
        const val SWITCH_SUFFIX = "_switch"
    }
}

class VersatileTextWithASwitchPreferenceDialogFragment : VersatileTextPreferenceDialogFragment() {
    // This replicates what the overridden method does, which is needed as we want to catch
    // the event when the positive button was pressed and the change listener approved the new value.
    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            val preference = preference as VersatileTextWithASwitchPreference
            val newText = editText.text.toString()

            if (preference.callChangeListener(newText)) {
                preference.text = newText
                preference.onDialogClosedAndNewTextSet()
            }
        }
    }
}
