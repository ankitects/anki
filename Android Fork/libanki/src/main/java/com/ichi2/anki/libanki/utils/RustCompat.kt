// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.utils

/** Rust semantics used by the backend that differ from Kotlin/JVM. */
object RustCompat {
    /** Rust `char::is_whitespace` and `str::trim` use Unicode's White_Space property. */
    fun isWhitespace(char: Char): Boolean =
        char in '\u0009'..'\u000d' || char == ' ' || char == '\u0085' || char == '\u00a0' || char == '\u1680' ||
            char in '\u2000'..'\u200a' || char == '\u2028' || char == '\u2029' || char == '\u202f' || char == '\u205f' ||
            char == '\u3000'
}
