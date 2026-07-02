/*
 *  Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.android.input

import android.view.KeyEvent
import android.view.KeyboardShortcutInfo
import androidx.annotation.StringRes
import com.ichi2.anki.AnkiActivityProvider
import com.ichi2.anki.CollectionManager.TR
import net.ankiweb.rsdroid.Translations

/**
 * Data class representing a keyboard shortcut.
 *
 * @param shortcut The string representation of the keyboard shortcut (e.g., "Ctrl+Alt+S").
 * @param label The string resource for the shortcut label.
 */
data class Shortcut(
    val shortcut: String,
    val label: String,
) {
    /**
     * Converts the shortcut string into a KeyboardShortcutInfo object.
     *
     * @return A KeyboardShortcutInfo object representing the keyboard shortcut.
     */
    fun toShortcutInfo(): KeyboardShortcutInfo {
        val parts = shortcut.split("+")
        val key = parts.last()
        val keycode: Int = getKey(key)
        val modifierFlags: Int = parts.dropLast(1).sumOf { getModifier(it) }

        return KeyboardShortcutInfo(label, keycode, modifierFlags)
    }

    /**
     * Maps a modifier string to its corresponding KeyEvent meta flag.
     *
     * @param modifier The modifier string (e.g., "Ctrl", "Alt", "Shift").
     * @return The corresponding KeyEvent meta flag.
     */
    private fun getModifier(modifier: String): Int =
        when (modifier) {
            "Ctrl" -> KeyEvent.META_CTRL_ON
            "Alt" -> KeyEvent.META_ALT_ON
            "Shift" -> KeyEvent.META_SHIFT_ON

            else -> 0
        }

    /**
     * Maps a key string to its corresponding keycode.
     *
     * @param key The key string.
     * @return The corresponding keycode, or 0 if the key string is invalid or not recognized.
     */
    private fun getKey(key: String): Int =
        when (key) {
            "/" -> KeyEvent.KEYCODE_SLASH
            "Esc" -> KeyEvent.KEYCODE_ESCAPE
            in "0".."9" -> KeyEvent.KEYCODE_0 + (key.toInt() - 0) // Handle number keys
            else -> KeyEvent.keyCodeFromString(key)
        }
}

/**
 * Provides a [Shortcut], from the shortcut keys and the resource id of its description.
 */
fun AnkiActivityProvider.shortcut(
    shortcut: String,
    @StringRes labelRes: Int,
) = Shortcut(shortcut, ankiActivity.getString(labelRes))

/**
 * Provides a [Shortcut], from the shortcut keys and the function from anki strings.
 */
fun shortcut(
    shortcut: String,
    getTranslation: Translations.() -> String,
) = Shortcut(shortcut, getTranslation(TR))
