/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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
