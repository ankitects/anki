// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.Context
import android.view.KeyEvent
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.utils.StringUtils
import com.ichi2.anki.common.utils.ext.ifNotZero
import com.ichi2.anki.common.utils.lastIndexOfOrNull
import timber.log.Timber
import java.util.Objects

/**
 * An input that can be mapped to an action.
 */
sealed interface Binding {
    /** A touch screen gesture, such as a swipe or double tap. */
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

    /** A keyboard or gamepad key binding, including its modifier keys. */
    interface KeyBinding : Binding {
        val modifierKeys: ModifierKeys
    }

    /** A shortcut matched by its Android key code. */
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

    /**
     * A keyboard shortcut matched by the character produced by a key press.
     *
     * [KeyCode] is insufficient: e.g. `@` doesn't have a consistent keypress across keyboards.
     */
    @Suppress("EqualsOrHashCode")
    data class UnicodeCharacter(
        val unicodeCharacter: BindingUnicodeCharacter,
        override val modifierKeys: ModifierKeys = AppDefinedModifierKeys.allowShift(),
    ) : KeyBinding {
        override fun toDisplayString(context: Context): String =
            buildString {
                append(KEY_PREFIX)
                append(' ')
                append(modifierKeys.toString())
                append(unicodeCharacter.value)
            }

        override fun toString(): String =
            buildString {
                append(UNICODE_PREFIX)
                append(modifierKeys.toString())
                append(unicodeCharacter.value)
            }

        // don't include the modifierKeys
        override fun hashCode(): Int = Objects.hash(unicodeCharacter.value)
    }

    fun toDisplayString(context: Context): String

    /** how the binding is serialised to preferences */
    abstract override fun toString(): String

    val isValid get() = true

    open class ModifierKeys internal constructor(
        val shift: Boolean = false,
        val ctrl: Boolean = false,
        val alt: Boolean = false,
    ) {
        /**
         * Returns whether Shift, Ctrl, and Alt exactly match those pressed in [event].
         */
        fun matches(event: KeyEvent): Boolean {
            // return false if Ctrl+1 is pressed and 1 is expected
            return shiftMatches(event) && ctrlMatches(event) && altMatches(event)
        }

        private fun shiftMatches(event: KeyEvent): Boolean = shift == event.isShiftPressed

        private fun ctrlMatches(event: KeyEvent): Boolean = ctrl == event.isCtrlPressed

        private fun altMatches(event: KeyEvent): Boolean = alt == event.isAltPressed

        /**
         * Whether the given Shift state is accepted when comparing modifier keys.
         *
         * [AppDefinedModifierKeys] accepts either Shift state because producing a character can
         * require Shift on some keyboard layouts. [matches] compares Shift exactly instead.
         */
        open fun shiftMatches(shiftPressed: Boolean): Boolean = shift == shiftPressed

        /**
         * Modifier prefix used when storing a binding, such as `Ctrl+Shift+`.
         */
        override fun toString() =
            buildString {
                if (ctrl) append("Ctrl+")
                if (alt) append("Alt+")
                if (shift) append("Shift+")
            }

        /**
         * Compares modifiers while allowing Shift to be optional for character bindings.
         */
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
            fun none(): ModifierKeys = ModifierKeys()

            fun ctrl(): ModifierKeys = ModifierKeys(ctrl = true)

            fun shift(): ModifierKeys = ModifierKeys(shift = true)

            fun alt(): ModifierKeys = ModifierKeys(alt = true)

            /**
             * Parses a [ModifierKeys] from a string.
             * @param s A binding string whose modifier prefix was generated by [ModifierKeys.toString].
             * Malformed input is not validated.
             * @return The [ModifierKeys], and the remainder of the string
             */
            fun parse(s: String): Pair<ModifierKeys, String> {
                // A trailing '+' is the bound character, not a modifier separator.
                val plusIndex = s.lastIndexOfOrNull('+', startIndex = s.lastIndex - 1) ?: return Pair(none(), s)
                val modifiers = fromString(s.take(plusIndex + 1))
                return Pair(modifiers, s.substring(plusIndex + 1))
            }

            fun fromString(from: String): ModifierKeys = ModifierKeys(from.contains("Shift"), from.contains("Ctrl"), from.contains("Alt"))
        }
    }

    /** Modifier keys which cannot be defined by a binding  */
    class AppDefinedModifierKeys private constructor() : ModifierKeys() {
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
            event.keyCode.ifNotZero { keyCode -> ret.add(KeyCode(keyCode, modifiers)) }

            // passing in metaState: 0 means that Ctrl+1 returns '1' instead of '\0'
            // NOTE: We do not differentiate on upper/lower case via KeyEvent.META_CAPS_LOCK_ON
            event
                .getUnicodeChar(event.metaState and (KeyEvent.META_SHIFT_ON or KeyEvent.META_NUM_LOCK_ON))
                .ifNotZero { unicodeChar ->
                    BindingUnicodeCharacter.fromCharOrNull(unicodeChar.toChar())?.let { character ->
                        ret.add(unicode(character, modifiers))
                    }
                }

            return ret
        }

        fun fromString(from: String): Binding? {
            if (from.isEmpty()) return null
            try {
                return when (from[0]) {
                    JOYSTICK_CHAR_PREFIX -> AxisButtonBinding.from(from.substring(1))
                    GESTURE_PREFIX -> GestureInput(Gesture.valueOf(from.substring(1)))
                    UNICODE_PREFIX -> {
                        val (modifierKeys, char) = ModifierKeys.parse(from.substring(1))
                        val unicodeChar = BindingUnicodeCharacter.fromCharOrNull(char[0]) ?: return null
                        UnicodeCharacter(unicodeChar, modifierKeys)
                    }
                    KEY_PREFIX -> {
                        val (modifierKeys, keyCodeAsString) = ModifierKeys.parse(from.substring(1))
                        val keyCode = keyCodeAsString.toInt()
                        KeyCode(keyCode, modifierKeys)
                    }
                    else -> null
                }
            } catch (ex: Exception) {
                Timber.w(ex)
            }
            return null
        }

        /**
         * Specifies a unicode binding from an unknown input device
         * See [AppDefinedModifierKeys]
         */
        fun unicode(
            unicodeChar: BindingUnicodeCharacter,
            modifierKeys: ModifierKeys = AppDefinedModifierKeys.allowShift(),
        ): KeyBinding = UnicodeCharacter(unicodeChar, modifierKeys)

        fun gesture(gesture: Gesture) = GestureInput(gesture)
    }
}
