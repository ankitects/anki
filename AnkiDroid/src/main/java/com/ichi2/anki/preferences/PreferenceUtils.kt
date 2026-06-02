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

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

fun SharedPreferences.get(key: String): Any? = all[key]

/**
 * Sets the callback to be invoked when this preference is changed by the user
 * (but before the internal state has been updated) on the internal onPreferenceChangeListener,
 * returning true on it by default
 * @param onPreferenceChangeListener The callback to be invoked
 */
fun SwitchPreferenceCompat.setOnPreferenceChangeListener(onPreferenceChangeListener: (newValue: Boolean) -> Unit) {
    this.setOnPreferenceChangeListener { _, newValue ->
        if (newValue !is Boolean) return@setOnPreferenceChangeListener false
        onPreferenceChangeListener(newValue)
        true
    }
}

/**
 * Sets the callback to be invoked when this preference is changed by the user
 * (but before the internal state has been updated) on the internal onPreferenceChangeListener,
 * returning true on it by default
 * @param onPreferenceChangeListener The callback to be invoked
 */
fun ListPreference.setOnPreferenceChangeListener(onPreferenceChangeListener: (newValue: String) -> Unit) {
    this.setOnPreferenceChangeListener { _, newValue ->
        if (newValue !is String) return@setOnPreferenceChangeListener false
        onPreferenceChangeListener(newValue)
        true
    }
}

/** Obtains a non-null reference to the preference defined by the key, or throws  */
inline fun <reified T : Preference> PreferenceFragmentCompat.requirePreference(key: String): T {
    val preference =
        findPreference<Preference>(key)
            ?: throw IllegalStateException("missing preference: '$key'")
    return preference as T
}

/**
 * Obtains a non-null reference to the preference whose
 * key is defined with given [resId] or throws
 * e.g. `requirePreference(R.string.day_theme_key)` returns
 * the preference whose key is `@string/day_theme_key`
 * The resource IDs with preferences keys can be found on `res/values/preferences.xml`
 */
inline fun <reified T : Preference> PreferenceFragmentCompat.requirePreference(
    @StringRes resId: Int,
): T {
    val key = getString(resId)
    return requirePreference(key)
}

inline fun <reified T : Preference> PreferenceFragmentCompat.findPreference(
    @StringRes resId: Int,
): T? {
    val key = getString(resId)
    return findPreference(key)
}

fun PreferenceScreen.allPreferences(): List<Preference> {
    val allPreferences = mutableListOf<Preference>()
    for (i in 0 until preferenceCount) {
        val pref = getPreference(i)
        if (pref is PreferenceGroup) {
            for (j in 0 until pref.preferenceCount) {
                allPreferences.add(pref.getPreference(j))
            }
        } else {
            allPreferences.add(pref)
        }
    }
    return allPreferences
}
