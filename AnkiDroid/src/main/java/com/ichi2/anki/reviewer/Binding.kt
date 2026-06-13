/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.reviewer

import android.content.Context
import android.view.KeyEvent
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.utils.StringUtils
import com.ichi2.anki.common.utils.ext.ifNotZero
import com.ichi2.anki.common.utils.lastIndexOfOrNull
import timber.log.Timber
import java.util.Objects

sealed interface Binding {
    data class GestureInput(
        val gesture: Gesture,
    ) : Binding {
        override fun toDisplayString(context: Context): String = gesture.toDisplayString(context)

        override fun toString() =
            buildString {
                append(GESTURE_PREFIX)
                append(gesture)
            }
    }

    /**
     * Maps an extremity on an [Axis] to a button press
     *
     * Example: [Axis.X] with a thresholds of {-1, 1} may be mapped to different actions
     * (signifying moving left/right on a gamepad joystick)
     */
    data class AxisButtonBinding(
        val axis: Axis,
        val threshold: Float,
    ) : Binding {
        private val plusOrMinus get() = if (threshold == -1f) "-" else "+"

        override fun toDisplayString(context: Context) = "$JOYSTICK_STRING_PREFIX $axis [$plusOrMinus]"

        override fun toString() =
            buildString {
                append(JOYSTICK_CHAR_PREFIX)
                append(axis.motionEventValue)
                append(" ")
                append(threshold)
            }

        companion object {
            fun from(preferenceString: String): AxisButtonBinding {
                val s = preferenceString.split(" ")

                val axis = Axis.fromInt(s[0].toInt())
                val threshold = s[1].toFloat()

                return AxisButtonBinding(axis, threshold)
            }
        }
    }

    interface KeyBinding : Binding {
        val modifierKeys: ModifierKeys
    }

    @Suppress("EqualsOrHashCode")
    data class KeyCode(
        val keycode: Int,
        override val modifierKeys: ModifierKeys = ModifierKeys.none(),
    ) : KeyBinding {
        private fun getKeyCodePrefix(): String =
            when {
                KeyEvent.isGamepadButton(keycode) -> GAMEPAD_PREFIX
                else -> KEY_PREFIX.toString()
            }

        override fun toDisplayString(context: Context): String =
            buildString {
                append(getKeyCodePrefix())
                append(' ')
                append(modifierKeys.toString())
                val keyCodeString = KeyEvent.keyCodeToString(keycode)
                // replace "Button" as we use the gamepad icon
                append(StringUtils.toTitleCase(keyCodeString.replace("KEYCODE_", "").replace("BUTTON_", "").replace('_', ' ')))
            }

        override fun toString() =
            buildString {
                append(KEY_PREFIX)
                append(modifierKeys.toString())
                append(keycode)
            }

        // don't include the modifierKeys
        override fun hashCode(): Int = Objects.hash(keycode)
    }

    @Suppress("EqualsOrHashCode")
    data class UnicodeCharacter(
        val unicodeCharacter: Char,
        override val modifierKeys: ModifierKeys = AppDefinedModifierKeys.allowShift(),
    ) : KeyBinding {
        override fun toDisplayString(context: Context): String =
            buildString {
                append(KEY_PREFIX)
                append(' ')
                append(modifierKeys.toString())
                append(unicodeCharacter)
            }

        override fun toString(): String =
            buildString {
                append(UNICODE_PREFIX)
                append(modifierKeys.toString())
                append(unicodeCharacter)
            }

        // don't include the modifierKeys
        override fun hashCode(): Int = Objects.hash(unicodeCharacter)
    }

    data object UnknownBinding : Binding {
        override fun toDisplayString(context: Context): String = ""

        override fun toString(): String = ""

        override val isValid: Boolean
            get() = false
    }

    fun toDisplayString(context: Context): String

    /** how the binding is serialised to preferences */
    abstract override fun toString(): String

    val isValid get() = true

    open class ModifierKeys internal constructor(
        val shift: Boolean,
        val ctrl: Boolean,
        val alt: Boolean,
    ) {
        fun matches(event: KeyEvent): Boolean {
            // return false if Ctrl+1 is pressed and 1 is expected
            return shiftMatches(event) && ctrlMatches(event) && altMatches(event)
        }

        private fun shiftMatches(event: KeyEvent): Boolean = shift == event.isShiftPressed

        private fun ctrlMatches(event: KeyEvent): Boolean = ctrl == event.isCtrlPressed

        private fun altMatches(event: KeyEvent): Boolean = alt == event.isAltPressed

        open fun shiftMatches(shiftPressed: Boolean): Boolean = shift == shiftPressed

        override fun toString() =
            buildString {
                if (ctrl) append("Ctrl+")
                if (alt) append("Alt+")
                if (shift) append("Shift+")
            }

        private fun semiStructuralEquals(keys: ModifierKeys): Boolean {
            if (this.alt != keys.alt || this.ctrl != keys.ctrl) {
                return false
            }
            // shiftMatches may be overridden
            return (
                this.shiftMatches(true) == keys.shiftMatches(true) ||
                    this.shiftMatches(false) == keys.shiftMatches(false)
            )
        }

        override fun equals(other: Any?): Boolean = other is ModifierKeys && semiStructuralEquals(other)

        override fun hashCode(): Int = Objects.hash(ctrl, alt, shiftMatches(true))

        companion object {
            fun none(): ModifierKeys = ModifierKeys(shift = false, ctrl = false, alt = false)

            fun ctrl(): ModifierKeys = ModifierKeys(shift = false, ctrl = true, alt = false)

            fun shift(): ModifierKeys = ModifierKeys(shift = true, ctrl = false, alt = false)

            fun alt(): ModifierKeys = ModifierKeys(shift = false, ctrl = false, alt = true)

            /**
             * Parses a [ModifierKeys] from a string.
             * @param s The string to parse
             * @return The [ModifierKeys], and the remainder of the string
             */
            fun parse(s: String): Pair<ModifierKeys, String> {
                val plusIndex = s.lastIndexOfOrNull('+') ?: return Pair(none(), s)
                val modifiers = fromString(s.take(plusIndex + 1))
                return Pair(modifiers, s.substring(plusIndex + 1))
            }

            fun fromString(from: String): ModifierKeys = ModifierKeys(from.contains("Shift"), from.contains("Ctrl"), from.contains("Alt"))
        }
    }

    /** Modifier keys which cannot be defined by a binding  */
    class AppDefinedModifierKeys private constructor() : ModifierKeys(false, false, false) {
        override fun shiftMatches(shiftPressed: Boolean): Boolean = true

        companion object {
            /**
             * Specifies a keycode combination binding from an unknown input device
             * Should be due to the "default" key bindings and never from user input
             *
             * If we do not know what the device is, "*" could be a key on the keyboard or Shift + 8
             *
             * So we need to ignore shift, rather than match it to a value
             *
             * If we have bindings in the app, then we know whether we need shift or not (in actual fact, we should
             * be fine to use keycodes).
             */
            fun allowShift(): ModifierKeys = AppDefinedModifierKeys()
        }
    }

    companion object {
        const val FORBIDDEN_UNICODE_CHAR = MappableBinding.PREF_SEPARATOR

        /**
         * https://www.fileformat.info/info/unicode/char/2328/index.htm (Keyboard)
         */
        const val KEY_PREFIX = '\u2328'

        /** https://www.fileformat.info/info/unicode/char/235d/index.htm (similar to a finger)  */
        const val GESTURE_PREFIX = '\u235D'

        /** 🕹️*/
        const val JOYSTICK_STRING_PREFIX = "\uD83D\uDD79\uFE0F"

        /** Can only use one char - pick a circle (wheel) */
        const val JOYSTICK_CHAR_PREFIX: Char = '◯'

        /** https://www.fileformat.info/info/unicode/char/2705/index.htm - checkmark (often used in URLs for unicode)
         * Only used for serialisation. [KEY_PREFIX] is used for display.
         */
        const val UNICODE_PREFIX = '\u2705'

        const val GAMEPAD_PREFIX = "🎮"

        /**
         * This returns multiple bindings due to the "default" implementation not knowing what the keycode for a button is
         */
        fun possibleKeyBindings(event: KeyEvent): List<KeyBinding> {
            val modifiers = ModifierKeys(event.isShiftPressed, event.isCtrlPressed, event.isAltPressed)
            val ret: MutableList<KeyBinding> = ArrayList()
            event.keyCode.ifNotZero { keyCode -> ret.add(keyCode(keyCode, modifiers)) }

            // passing in metaState: 0 means that Ctrl+1 returns '1' instead of '\0'
            // NOTE: We do not differentiate on upper/lower case via KeyEvent.META_CAPS_LOCK_ON
            event
                .getUnicodeChar(event.metaState and (KeyEvent.META_SHIFT_ON or KeyEvent.META_NUM_LOCK_ON))
                .ifNotZero { unicodeChar ->
                    try {
                        ret.add(unicode(unicodeChar.toChar(), modifiers) as KeyBinding)
                    } catch (e: Exception) {
                        // very slight chance it returns unknown()
                        Timber.w(e)
                    }
                }

            return ret
        }

        fun fromString(from: String): Binding {
            if (from.isEmpty()) return UnknownBinding
            try {
                return when (from[0]) {
                    JOYSTICK_CHAR_PREFIX -> AxisButtonBinding.from(from.substring(1))
                    GESTURE_PREFIX -> GestureInput(Gesture.valueOf(from.substring(1)))
                    UNICODE_PREFIX -> {
                        val (modifierKeys, char) = ModifierKeys.parse(from.substring(1))
                        UnicodeCharacter(char[0], modifierKeys)
                    }
                    KEY_PREFIX -> {
                        val (modifierKeys, keyCodeAsString) = ModifierKeys.parse(from.substring(1))
                        val keyCode = keyCodeAsString.toInt()
                        KeyCode(keyCode, modifierKeys)
                    }
                    else -> UnknownBinding
                }
            } catch (ex: Exception) {
                Timber.w(ex)
            }
            return UnknownBinding
        }

        fun unicode(
            modifierKeys: ModifierKeys,
            unicodeChar: Char,
        ): Binding = unicode(unicodeChar, modifierKeys)

        /**
         * Specifies a unicode binding from an unknown input device
         * See [AppDefinedModifierKeys]
         */
        fun unicode(
            unicodeChar: Char,
            modifierKeys: ModifierKeys = AppDefinedModifierKeys.allowShift(),
        ): Binding {
            if (unicodeChar == FORBIDDEN_UNICODE_CHAR) return unknown()
            return UnicodeCharacter(unicodeChar, modifierKeys)
        }

        fun keyCode(
            keyCode: Int,
            modifiers: ModifierKeys = ModifierKeys.none(),
        ) = KeyCode(keyCode, modifiers)

        fun keyCode(
            modifiers: ModifierKeys,
            keyCode: Int,
        ) = KeyCode(keyCode, modifiers)

        fun gesture(gesture: Gesture) = GestureInput(gesture)

        @VisibleForTesting
        fun unknown() = UnknownBinding
    }
}
