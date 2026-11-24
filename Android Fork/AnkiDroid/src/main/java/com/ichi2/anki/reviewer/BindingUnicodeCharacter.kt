// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

/** @see Binding.UnicodeCharacter */
@JvmInline
value class BindingUnicodeCharacter(
    val value: Char,
) {
    init {
        require(value != FORBIDDEN_UNICODE_CHAR) { "The preference separator cannot be bound" }
    }

    companion object {
        /** Separates bindings in preferences; using it as a key would split the binding during deserialization. */
        private const val FORBIDDEN_UNICODE_CHAR = MappableBinding.PREF_SEPARATOR

        /** Returns null for the preference separator, which cannot be stored as a binding. */
        fun fromCharOrNull(value: Char): BindingUnicodeCharacter? =
            if (value == FORBIDDEN_UNICODE_CHAR) null else BindingUnicodeCharacter(value)
    }
}
