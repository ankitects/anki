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
package com.ichi2.anki.previewer

import android.content.SharedPreferences
import android.view.KeyEvent
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.Binding.Companion.unicode
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.MappableAction
import com.ichi2.anki.reviewer.MappableBinding

enum class PreviewerAction : MappableAction<MappableBinding> {
    BACK,
    NEXT,
    MARK,
    EDIT,
    TOGGLE_BACKSIDE_ONLY,

    // TODO: rename to REPLAY_MEDIA and handle previewer_replay_audio_key
    REPLAY_AUDIO,
    TOGGLE_FLAG_RED,
    TOGGLE_FLAG_ORANGE,
    TOGGLE_FLAG_GREEN,
    TOGGLE_FLAG_BLUE,
    TOGGLE_FLAG_PINK,
    TOGGLE_FLAG_TURQUOISE,
    TOGGLE_FLAG_PURPLE,
    UNSET_FLAG,
    ;

    override val preferenceKey = "previewer_$name"

    override fun getBindings(prefs: SharedPreferences): List<MappableBinding> {
        val prefValue = prefs.getString(preferenceKey, null) ?: return defaultBindings
        return MappableBinding.fromPreferenceString(prefValue)
    }

    private val defaultBindings: List<MappableBinding> get() {
        val binding =
            when (this) {
                BACK -> keyCode(KeyEvent.KEYCODE_DPAD_LEFT)
                NEXT -> keyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
                MARK -> unicode('*')
                REPLAY_AUDIO -> keyCode(KeyEvent.KEYCODE_R)
                EDIT -> keyCode(KeyEvent.KEYCODE_E)
                TOGGLE_BACKSIDE_ONLY -> keyCode(KeyEvent.KEYCODE_B)
                TOGGLE_FLAG_RED -> keyCode(KeyEvent.KEYCODE_1, ctrl())
                TOGGLE_FLAG_ORANGE -> keyCode(KeyEvent.KEYCODE_2, ctrl())
                TOGGLE_FLAG_GREEN -> keyCode(KeyEvent.KEYCODE_3, ctrl())
                TOGGLE_FLAG_BLUE -> keyCode(KeyEvent.KEYCODE_4, ctrl())
                TOGGLE_FLAG_PINK -> keyCode(KeyEvent.KEYCODE_5, ctrl())
                TOGGLE_FLAG_TURQUOISE -> keyCode(KeyEvent.KEYCODE_6, ctrl())
                TOGGLE_FLAG_PURPLE -> keyCode(KeyEvent.KEYCODE_7, ctrl())
                UNSET_FLAG -> return emptyList()
            }
        return listOf(MappableBinding(binding))
    }
}
