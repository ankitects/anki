/*
 * Renjin : JVM-based interpreter for the R language for the statistical analysis
 * Copyright © 2010-2019 BeDataDriven Groep B.V. and contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, a copy is available at
 * https://www.gnu.org/licenses/gpl-2.0.txt
 *
 * https://github.com/mobanisto/gcc-bridge/blob/3bd5df076d7d3b6d7b0a66653c2d114e4bfaf381/runtime/src/main/java/org/renjin/gcc/runtime/Stdlib.java
 */
package net.ankiweb.rsdroid.utils

object StringToLong {
    fun strtol(s: String): Long {
        var s1 = s
        var radix = 0

        // Find the start of the number
        var start = 0

        // Skip beginning whitespace
        while (start < s1.length && Character.isWhitespace(s1[start])) {
            start++
        }
        var pos = start

        // Check for +/- prefix
        if (pos < s1.length && (s1[pos] == '-' || s1[pos] == '+')) {
            pos++
        } else if (pos + 1 < s1.length && s1[pos] == '0' && (s1[pos + 1] == 'x' || s1[pos + 1] == 'X')) {
            start += 2
            pos = start
            radix = 16
        } else if (pos < s1.length && s1[pos] == '0') {
            radix = 8
        }

        // Otherwise if radix is not specified, and there is no prefix,
        // assume decimal
        if (radix == 0) {
            radix = 10
        }

        // Advance until we run out of digits
        while (pos < s1.length && s1[pos].digitToIntOrNull(radix) ?: -1 != -1) {
            pos++
        }

        // If empty, return 0 and exit
        if (start == pos) {
            return 0
        }
        s1 = s1.substring(start, pos)
        return try {
            s1.toLong(radix)
        } catch (e: NumberFormatException) {
            Long.MAX_VALUE
        }
    }
}
