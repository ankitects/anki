/*
 *  Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.utils.ext

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.servicelayer.bindingFromPreference

fun ViewerCommand.addBinding(
    preferences: SharedPreferences,
    binding: MappableBinding,
) {
    addBinding(preferenceKey, preferences, binding)
}

fun addBinding(
    key: String,
    preferences: SharedPreferences,
    binding: MappableBinding,
) {
    val addAtStart: (MutableList<MappableBinding>, MappableBinding) -> Boolean =
        { collection, element ->
            // reorder the elements, moving the added binding to the first position
            collection.remove(element)
            collection.add(0, element)
            true
        }
    val bindings: MutableList<MappableBinding> = bindingFromPreference(preferences, key)
    addAtStart(bindings, binding)
    val newValue: String = bindings.toPreferenceString()
    preferences.edit { putString(key, newValue) }
}
