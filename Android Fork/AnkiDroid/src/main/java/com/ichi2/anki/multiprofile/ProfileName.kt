// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

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

    /**
     * Whether [names] already contains this name, ignoring case.
     *
     * Stricter than Anki Desktop, which compares with SQLite's `NOCASE` and only folds ASCII:
     * `Ä` and `ä` are two names there but one name here. Profile names are never shared
     * with Desktop, so the two checks never have to agree.
     */
    fun existsIn(names: Iterable<String>): Boolean = names.any { it.equals(value, ignoreCase = true) }

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
