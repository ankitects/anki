// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

/** The name of a file, without a path or an extension. */
@JvmInline
value class Filename private constructor(
    val value: String,
) {
    init {
        require(!INVALID_CHARACTERS.containsMatchIn(value)) { "'$value' contains invalid characters" }
    }

    override fun toString() = value

    companion object {
        /**
         * @see <a href="https://github.com/ankitects/anki/blob/d4fdbefcebeb4318e3732a450923cbf94c877362/qt/aqt/import_export/exporting.py#L176">exporting.py</a>
         */
        private val INVALID_CHARACTERS = Regex("""[\\/?<>:*|"^]""")

        fun sanitize(value: String) = Filename(INVALID_CHARACTERS.replace(value, "_"))
    }
}
