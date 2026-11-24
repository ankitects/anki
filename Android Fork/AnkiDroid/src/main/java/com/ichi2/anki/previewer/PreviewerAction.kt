// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.content.SharedPreferences
import android.view.KeyEvent
import com.ichi2.anki.reviewer.Binding.Companion.unicode
import com.ichi2.anki.reviewer.Binding.KeyCode
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.BindingUnicodeCharacter
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
                BACK -> KeyCode(KeyEvent.KEYCODE_DPAD_LEFT)
                NEXT -> KeyCode(KeyEvent.KEYCODE_DPAD_RIGHT)
                MARK -> unicode(BindingUnicodeCharacter('*'))
                REPLAY_AUDIO -> KeyCode(KeyEvent.KEYCODE_R)
                EDIT -> KeyCode(KeyEvent.KEYCODE_E)
                TOGGLE_BACKSIDE_ONLY -> KeyCode(KeyEvent.KEYCODE_B)
                TOGGLE_FLAG_RED -> KeyCode(KeyEvent.KEYCODE_1, ctrl())
                TOGGLE_FLAG_ORANGE -> KeyCode(KeyEvent.KEYCODE_2, ctrl())
                TOGGLE_FLAG_GREEN -> KeyCode(KeyEvent.KEYCODE_3, ctrl())
                TOGGLE_FLAG_BLUE -> KeyCode(KeyEvent.KEYCODE_4, ctrl())
                TOGGLE_FLAG_PINK -> KeyCode(KeyEvent.KEYCODE_5, ctrl())
                TOGGLE_FLAG_TURQUOISE -> KeyCode(KeyEvent.KEYCODE_6, ctrl())
                TOGGLE_FLAG_PURPLE -> KeyCode(KeyEvent.KEYCODE_7, ctrl())
                UNSET_FLAG -> return emptyList()
            }
        return listOf(MappableBinding(binding))
    }
}
