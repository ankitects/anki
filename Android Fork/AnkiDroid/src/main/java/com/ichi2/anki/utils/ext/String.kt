// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.utils.ext

import java.text.Normalizer
import java.util.regex.Pattern

private val DIACRITICAL_MARKS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

/**
 * Normalizes the given string by removing diacritical marks (accents) to enable accent-insensitive searches.
 *
 * This method uses Unicode normalization in **NFD (Normalization Form Decomposition)** mode, which separates
 * base characters from their diacritical marks. Then, it removes all combining diacritical marks using a regex.
 *
 * Example usage:
 * ```
 * val input = "café naïve résumé"
 * val normalized = input.normalizeForSearch()
 * println(normalized) // Output: "cafe naive resume"
 * ```
 *
 * @receiver The input string that may contain accented characters.
 * @return A new string with accents removed.
 */
fun String.normalizeForSearch(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return DIACRITICAL_MARKS_PATTERN.matcher(normalized).replaceAll("")
}
