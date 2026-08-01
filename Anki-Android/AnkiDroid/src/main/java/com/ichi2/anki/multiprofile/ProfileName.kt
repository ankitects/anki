/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

/**
 * A validated, user-facing profile display name.
 *
 * Values can only be obtained through [validate] (for user input) or
 * [fromTrustedSource] (for persisted / hard-coded values). This makes invalid
 * profile names unrepresentable in the domain layer.
 */
@JvmInline
value class ProfileName private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 50

        /**
         * Validates raw user input. Trims leading and trailing whitespace
         * before checking the rules; interior whitespace (including multiple
         * spaces between words) is preserved as-is.
         *
         * Any non-whitespace character is permitted — including punctuation,
         * emoji, and symbols — matching the desktop Anki behavior. Only
         * length and emptiness are enforced here; uniqueness is checked at
         * the registry layer.
         *
         * Returns a [ValidationResult] — never throws.
         */
        fun validate(raw: String): ValidationResult {
            val cleaned = raw.trim()
            return when {
                cleaned.isEmpty() -> ValidationResult.Empty
                cleaned.length > MAX_LENGTH ->
                    ValidationResult.TooLong(cleaned.length)
                else -> ValidationResult.Valid(ProfileName(cleaned))
            }
        }

        /**
         * Constructs a [ProfileName] from a value that has already been
         * validated elsewhere (persisted metadata, hard-coded defaults).
         *
         * DO NOT use this for raw user input — use [validate] instead.
         */
        internal fun fromTrustedSource(value: String): ProfileName = ProfileName(value)
    }

    /** Outcome of validating a candidate profile name. */
    sealed interface ValidationResult {
        data class Valid(
            val name: ProfileName,
        ) : ValidationResult

        data object Empty : ValidationResult

        data class TooLong(
            val actualLength: Int,
        ) : ValidationResult
    }
}
