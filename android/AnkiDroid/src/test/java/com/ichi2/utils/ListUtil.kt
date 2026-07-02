/*
 * Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>
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
 */
package com.ichi2.utils

import org.junit.Assert
import org.junit.Test
import org.junit.internal.ArrayComparisonFailure
import java.lang.AssertionError
import kotlin.Throws
import kotlin.test.assertFailsWith

class ListUtil {
    @Test
    fun equalsTest() {
        assertFailsWith<ArrayComparisonFailure> { assertListEquals(listOf(2L, 3L), listOf(2L, 4L)) }
        assertFailsWith<ArrayComparisonFailure> { assertListEquals(listOf(2L, 3L), listOf(2L)) }
        assertFailsWith<ArrayComparisonFailure> { assertListEquals(listOf(2L, 3L), listOf(5L)) }
        assertFailsWith<ArrayComparisonFailure> { assertListEquals(listOf(2L, 3L), listOf(2L, 3L, 5L)) }
        assertFailsWith<AssertionError> { assertListEquals(listOf(2L, 3L), null) }
        assertFailsWith<AssertionError> { assertListEquals(null, listOf(2L, 4L)) }
        assertListEquals(null, null)
        assertListEquals(listOf(2L, 3L), listOf(2L, 3L))
    }

    companion object {
        /**
         * Asserts that two object lists are equal (same size and components in same order). If they are not, an
         * [AssertionError] is thrown with the given message. It states "array" instead of list If
         * `expecteds` and `actuals` are `null`,
         * they are considered equal.
         *
         * @param message the identifying message for the [AssertionError] (`null`
         * okay)
         * @param expected Object list or list of arrays (multi-dimensional array) with
         * expected values.
         * @param actuals Object list or list of arrays (multi-dimensional array) with
         * actual values
         */
        @Throws(ArrayComparisonFailure::class)
        fun assertListEquals(
            message: String?,
            expected: List<Any?>?,
            actuals: List<Any?>?,
        ) {
            val expectedArray: Array<Any?>? = expected?.toTypedArray()
            val actualArray: Array<Any?>? = actuals?.toTypedArray()
            Assert.assertArrayEquals(message, expectedArray, actualArray)
        }

        /**
         * Asserts that two object arrays are equal. If they are not, an
         * [AssertionError] is thrown. If `expected` and
         * `actual` are `null`, they are considered
         * equal.
         *
         * @param expected Object list or list of arrays (multi-dimensional array) with
         * expected values
         * @param actuals Object list or list of arrays (multi-dimensional array) with
         * actual values
         */
        fun assertListEquals(
            expected: List<Any?>?,
            actuals: List<Any?>?,
        ) {
            assertListEquals(null, expected, actuals)
        }
    }
}
