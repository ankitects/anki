/*
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * * This file includes source under the following license:
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("ktlint")
/*
 *	Source code for the "strtod" library procedure.
 *
 * Copyright 1988-1992 Regents of the University of California
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies.  The University of California
 * makes no representations about the suitability of this
 * software for any purpose.  It is provided "as is" without
 * express or implied warranty.
 */
/**
 * Modifications:
 * change package & class name
 * made strtod(String s) public
 */
package net.ankiweb.rsdroid.utils

import java.nio.charset.StandardCharsets

object StringToDouble {
    const val maxExponent = 511 /* Largest possible base 10 exponent.  Any
     * exponent larger than this will already
     * produce underflow or overflow, so there's
     * no need to worry about additional digits.
     */
    val powersOf10 =
        doubleArrayOf( // Table giving binary powers of 10.  Entry
            10.0, // is 10^2^i.  Used to convert decimal
            100.0, // exponents into floating-point numbers.
            1.0e4,
            1.0e8,
            1.0e16,
            1.0e32,
            1.0e64,
            1.0e128,
            1.0e256,
        )

    /*
     * Only for testing
     */
    fun strtod(s: String): Double {
        val utf8 = s.toByteArray(StandardCharsets.UTF_8)
        return strtod(utf8, 0, utf8.size)
    }

    fun strtod(
        utf8: ByteArray,
        offset: Int,
        length: Int,
    ): Double {
        if (length == 0) {
            throw NumberFormatException()
        }
        var signIsNegative: Boolean
        var expSignIsNegative = true
        var fraction: Double
        var d: Int
        var p = offset
        var end = offset + length
        var c: Int
        var exp = 0 // Exponent read from "EX" field.
        var fracExp: Int /* Exponent that derives from the fractional
         * part.  Under normal circumstatnces, it is
         * the negative of the number of digits in F.
         * However, if I is very long, the last digits
         * of I get dropped (otherwise a long I with a
         * large negative exponent could cause an
         * unnecessary overflow on I alone).  In this
         * case, fracExp is incremented one for each
         * dropped digit. */
        var mantSize: Int // Number of digits in mantissa.
        var decPt: Int /* Number of mantissa digits BEFORE decimal
         * point. */
        val pExp: Int /* Temporarily holds location of exponent
         * in string. */

        /*
         * Strip off leading blanks and check for a sign.
         */
        while (p < end && Character.isWhitespace(utf8[p].toInt())) {
            p++
        }
        while (end > p && Character.isWhitespace(utf8[end - 1].toInt())) {
            end--
        }
        if (!testSimpleDecimal(utf8, p, end - p)) {
            return String(utf8, p, end - p, StandardCharsets.UTF_8).toDouble()
        }
        if (utf8[p] == '-'.code.toByte()) {
            signIsNegative = true
            p += 1
        } else {
            if (utf8[p] == '+'.code.toByte()) {
                p += 1
            }
            signIsNegative = false
        }

        /*
         * Count the number of digits in the mantissa (including the decimal
         * point), and also locate the decimal point.
         */
        decPt = -1
        val mantEnd = end - p
        mantSize = 0
        while (mantSize < mantEnd) {
            c = utf8[p].toInt()
            if (!isdigit(c)) {
                if (c != '.'.code || decPt >= 0) {
                    break
                }
                decPt = mantSize
            }
            p += 1
            mantSize += 1
        }

        /*
         * Now suck up the digits in the mantissa.  Use two integers to
         * collect 9 digits each (this is faster than using floating-point).
         * If the mantissa has more than 18 digits, ignore the extras, since
         * they can't affect the value anyway.
         */
        pExp = p
        p -= mantSize
        if (decPt < 0) {
            decPt = mantSize
        } else {
            mantSize -= 1 // One of the digits was the point.
        }
        if (mantSize > 18) {
            fracExp = decPt - 18
            mantSize = 18
        } else {
            fracExp = decPt - mantSize
        }
        if (mantSize == 0) {
            return if (signIsNegative) {
                -0.0
            } else {
                0.0
            }
        } else {
            var frac1: Double
            var frac2: Double
            frac1 = 0.0
            while (mantSize > 9) {
                c = utf8[p].toInt()
                p += 1
                if (c == '.'.code) {
                    c = utf8[p].toInt()
                    p += 1
                }
                frac1 = 10 * frac1 + (c - '0'.code)
                mantSize -= 1
            }
            frac2 = 0.0
            while (mantSize > 0) {
                c = utf8[p].toInt()
                p += 1
                if (c == '.'.code) {
                    c = utf8[p].toInt()
                    p += 1
                }
                frac2 = 10 * frac2 + (c - '0'.code)
                mantSize -= 1
            }
            fraction = 1e9 * frac1 + frac2
        }

        /*
         * Skim off the exponent.
         */
        p = pExp
        if (p < end) {
            if (utf8[p] == 'E'.code.toByte() || utf8[p] == 'e'.code.toByte()) {
                p += 1
                if (p < end) {
                    if (utf8[p] == '-'.code.toByte()) {
                        expSignIsNegative = true
                        p += 1
                    } else {
                        if (utf8[p] == '+'.code.toByte()) {
                            p += 1
                        }
                        expSignIsNegative = false
                    }
                    while (p < end && isdigit(utf8[p].toInt())) {
                        exp = exp * 10 + (utf8[p] - '0'.code.toByte())
                        p += 1
                    }
                }
            }
        }
        exp =
            if (expSignIsNegative) {
                fracExp - exp
            } else {
                fracExp + exp
            }

        /*
         * Generate a floating-point number that represents the exponent.
         * Do this by processing the exponent one bit at a time to combine
         * many powers of 2 of 10. Then combine the exponent with the
         * fraction.
         */
        if (exp < 0) {
            expSignIsNegative = true
            exp = -exp
        } else {
            expSignIsNegative = false
        }
        if (exp > maxExponent) {
            exp = maxExponent
        }
        var dblExp = 1.0
        d = 0
        while (exp != 0) {
            if (exp and 1 == 1) {
                dblExp *= powersOf10[d]
            }
            exp = exp shr 1
            d += 1
        }
        if (expSignIsNegative) {
            fraction /= dblExp
        } else {
            fraction *= dblExp
        }
        return if (signIsNegative) {
            -fraction
        } else {
            fraction
        }
    }

    private fun testSimpleDecimal(
        utf8: ByteArray,
        off: Int,
        len: Int,
    ): Boolean {
        if (len > 18) {
            return false
        }
        var decimalPts = 0
        var signs = 0
        var nondigits = 0
        var digits = 0
        for (i in off until len + off) {
            val c = utf8[i].toInt()
            if (c == '.'.code) {
                decimalPts++
            } else if (c == '-'.code || c == '+'.code) {
                signs++
            } else if (!isdigit(c)) {
                // could be exponential notations
                nondigits++
            } else {
                digits++
            }
        }
        // There can be up to 5e-16 error
        return decimalPts <= 1 && signs <= 1 && nondigits == 0 && digits < 16
    }

    private fun isdigit(c: Int): Boolean = '0'.code <= c && c <= '9'.code
}
