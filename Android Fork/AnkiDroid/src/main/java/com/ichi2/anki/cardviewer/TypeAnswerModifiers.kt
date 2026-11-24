// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

/**
 * Represents modifiers for `[[type:...]]`
 *
 * Examples:
 * - `[[type:nosuggest:nc:Field]]`
 * - `[[type:cloze:Field]]`
 */
internal data class TypeAnswerModifiers(
    val fieldName: String,
    val combining: Boolean,
    val cloze: Boolean,
    val noSuggest: Boolean,
) {
    companion object {
        /**
         * Represents the known modifiers in a [[type: ]] declaration, and remaining data
         */
        fun parse(rawField: String): TypeAnswerModifiers {
            var remaining = rawField
            var combining = true
            var cloze = false
            var noSuggest = false
            while (true) {
                when {
                    remaining.startsWith("nosuggest:") -> {
                        noSuggest = true
                        remaining = remaining.removePrefix("nosuggest:")
                    }
                    remaining.startsWith("cloze:") -> {
                        cloze = true
                        remaining = remaining.removePrefix("cloze:")
                    }
                    remaining.startsWith("nc:") -> {
                        combining = false
                        remaining = remaining.removePrefix("nc:")
                    }
                    else -> return TypeAnswerModifiers(
                        fieldName = remaining,
                        combining = combining,
                        cloze = cloze,
                        noSuggest = noSuggest,
                    )
                }
            }
        }
    }
}
