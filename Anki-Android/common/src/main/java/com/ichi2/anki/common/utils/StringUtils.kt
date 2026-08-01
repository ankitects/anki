/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.

 ----

 This file incorporates code under the following license:

   Copyright (C) 2006 The Android Open Source Project

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.ichi2.anki.common.utils

import com.ichi2.anki.common.annotations.DuplicatedCode
import org.jetbrains.annotations.Contract
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.min

object StringUtils {
    /** Converts the string to where the first letter is uppercase, and the rest of the string is lowercase  */
    // TODO(low): some libAnki functions can use this instead of capitalize() alternatives
    @Contract("null -> null; !null -> !null")
    fun toTitleCase(s: String?): String? {
        if (s == null) return null
        if (s.isBlank()) return s

        return s[0].uppercase(Locale.getDefault()) + s.substring(1).lowercase(Locale.getDefault())
    }
}

fun String.trimToLength(maxLength: Int): String = this.substring(0, min(this.length, maxLength))

fun String.indexOfOrNull(
    c: Char,
    startIndex: Int = 0,
    ignoreCase: Boolean = false,
): Int? =
    when (val index = this.indexOf(c, startIndex, ignoreCase)) {
        -1 -> null
        else -> index
    }

fun String.lastIndexOfOrNull(
    c: Char,
    startIndex: Int = lastIndex,
    ignoreCase: Boolean = false,
): Int? =
    when (val index = this.lastIndexOf(c, startIndex, ignoreCase)) {
        -1 -> null
        else -> index
    }

fun String.lastIndexOfOrNull(
    s: String,
    startIndex: Int = lastIndex,
    ignoreCase: Boolean = false,
): Int? =
    when (val index = this.lastIndexOf(s, startIndex, ignoreCase)) {
        -1 -> null
        else -> index
    }

fun emptyStringMutableList(size: Int): MutableList<String> = MutableList(size) { "" }

fun emptyStringArray(size: Int): Array<String> = Array(size) { "" }

/**
 * Html-encode the string.
 * @receiver the string to be encoded
 * @return the encoded string
 */
// replaces:
// androidx.core.text.htmlEncode
// android.text.TextUtils.htmlEncode
@DuplicatedCode("copied from android.text.TextUtils.htmlEncode, converted to kotlin extension")
fun String.htmlEncode(): String {
    val sb = StringBuilder()
    var c: Char
    for (i in 0..<this.length) {
        c = this[i]
        when (c) {
            '<' -> sb.append("&lt;") // $NON-NLS-1$
            '>' -> sb.append("&gt;") // $NON-NLS-1$
            '&' -> sb.append("&amp;") // $NON-NLS-1$
            '\'' -> // http://www.w3.org/TR/xhtml1
                // The named character reference &apos; (the apostrophe, U+0027) was introduced in
                // XML 1.0 but does not appear in HTML. Authors should therefore use &#39; instead
                // of &apos; to work as expected in HTML 4 user agents.
                sb.append("&#39;") // $NON-NLS-1$
            '"' -> sb.append("&quot;") // $NON-NLS-1$
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

/**
 * Truncates the string to the given maximum length and appends an ellipsis (`…`)
 * if the text exceeds that length.
 *
 * Prefer [android.text.TextUtils.ellipsize] when you have a reference to a TextView
 *
 * @param ellipsizeAfter when to ellipsize the text.
 *  `ellipsizeAfter = 2` returns converts `"foo"` to `"fo…"`
 *
 *  @throws IllegalStateException if [`ellipsizeAfter`][ellipsizeAfter]` <= 0`
 */
fun String.ellipsize(
    ellipsizeAfter: Int,
    locale: Locale = Locale.ROOT,
): String {
    require(ellipsizeAfter > 0) { "invalid length: $ellipsizeAfter" }
    if (this.length <= ellipsizeAfter) return this
    val lastIndex = this.indexOfLastGraphemeCluster(maxChars = ellipsizeAfter, locale)
    return this.take(lastIndex) + "…"
}

private fun String.indexOfLastGraphemeCluster(
    maxChars: Int,
    locale: Locale,
): Int {
    val iterator = BreakIterator.getCharacterInstance(locale)
    iterator.setText(this)

    var end = iterator.first()
    var lastSafe = end

    while (end != BreakIterator.DONE) {
        if (end > maxChars) return lastSafe
        lastSafe = end
        end = iterator.next()
    }

    return lastSafe
}
