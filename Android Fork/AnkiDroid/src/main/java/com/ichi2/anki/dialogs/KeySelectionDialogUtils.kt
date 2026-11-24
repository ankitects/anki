// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.view.KeyEvent

typealias KeyCode = Int

object KeySelectionDialogUtils {
    fun disallowModifierKeyCodes(): (KeyCode) -> Boolean {
        val modifierKeyCodes =
            hashSetOf(
                KeyEvent.KEYCODE_SHIFT_LEFT,
                KeyEvent.KEYCODE_SHIFT_RIGHT,
                KeyEvent.KEYCODE_CTRL_LEFT,
                KeyEvent.KEYCODE_CTRL_RIGHT,
                KeyEvent.KEYCODE_ALT_LEFT,
                KeyEvent.KEYCODE_ALT_RIGHT,
                KeyEvent.KEYCODE_META_LEFT,
                KeyEvent.KEYCODE_META_RIGHT,
                KeyEvent.KEYCODE_FUNCTION,
            )
        return { !modifierKeyCodes.contains(it) }
    }
}
