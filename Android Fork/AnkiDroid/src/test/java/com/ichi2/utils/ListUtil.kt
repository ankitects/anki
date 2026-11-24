// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>

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
