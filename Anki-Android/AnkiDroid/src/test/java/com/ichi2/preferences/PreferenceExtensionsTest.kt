/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.RuntimeException
import java.util.function.Supplier

// Unknown issue: @CheckResult should provide warnings on this class when return value is unused, but doesn't.
class PreferenceExtensionsTest {
    private val mockPreferences: SharedPreferences =
        SPMockBuilder().createSharedPreferences().apply {
            edit {
                putString(VALID_KEY, VALID_RESULT)
            }
        }

    private fun getOrSetString(
        key: String,
        supplier: Supplier<String>,
    ): String = mockPreferences.getOrSetString(key, supplier)

    private fun getOrSetLong(
        key: String,
        supplier: Supplier<Long>,
    ): Long = mockPreferences.getOrSetLong(key, supplier)

    // String Tests

    @Test
    fun existingKeyReturnsMappedValue() {
        val ret = getOrSetString(VALID_KEY, UNUSED_SUPPLIER)
        assertEquals(ret, VALID_RESULT)
    }

    @Test
    fun missingKeyReturnsLambdaValue() {
        val ret = getOrSetString(MISSING_KEY) { LAMBDA_RETURN }
        assertEquals(ret, LAMBDA_RETURN)
    }

    @Test
    fun missingKeySetsPreference() {
        getOrSetString(MISSING_KEY) { LAMBDA_RETURN }
        assertThat(mockPreferences.getString(MISSING_KEY, null), equalTo(LAMBDA_RETURN))
    }

    @SuppressWarnings("unused")
    @Test
    fun noLambdaExceptionIfKeyExists() {
        assertDoesNotThrow { getOrSetString(VALID_KEY, EXCEPTION_SUPPLIER) }
    }

    @Test(expected = ExpectedException::class)
    fun rethrowLambdaExceptionIfKeyIsMissing() {
        getOrSetString(MISSING_KEY, EXCEPTION_SUPPLIER)
    }

    // Long Tests

    @Test
    fun existingLongKeyReturnsStoredValue() {
        mockPreferences.edit { putLong(LONG_VALID_KEY, LONG_VALID_VALUE) }
        val result = getOrSetLong(LONG_VALID_KEY, LONG_UNUSED_SUPPLIER)
        assertEquals(LONG_VALID_VALUE, result)
    }

    @Test
    fun missingLongKeyPersistsValue() {
        getOrSetLong(LONG_MISSING_KEY) { LONG_LAMBDA_VALUE }
        assertEquals(LONG_LAMBDA_VALUE, mockPreferences.getLong(LONG_MISSING_KEY, -1))
    }

    @Test
    fun handlesNegativeLongValue() {
        getOrSetLong(NEGATIVE_TEST_KEY) { LONG_NEGATIVE_VALUE }
        assertEquals(LONG_NEGATIVE_VALUE, mockPreferences.getLong(NEGATIVE_TEST_KEY, 0))
    }

    private class ExpectedException : RuntimeException()

    private class UnexpectedException : RuntimeException()

    companion object {
        // String constants
        private const val VALID_KEY = "VALID"
        private const val VALID_RESULT = "WAS VALID KEY"
        private const val MISSING_KEY = "INVALID"
        private const val LAMBDA_RETURN = "LAMBDA"

        // Long constants
        private const val LONG_VALID_KEY = "LONG_VALID"
        private const val LONG_VALID_VALUE = 42L
        private const val LONG_MISSING_KEY = "LONG_INVALID"
        private const val LONG_LAMBDA_VALUE = 100L
        private const val LONG_NEGATIVE_VALUE = -500L
        private const val NEGATIVE_TEST_KEY = "negative_test"

        // Suppliers
        private val UNUSED_SUPPLIER = Supplier<String> { throw UnexpectedException() }
        private val EXCEPTION_SUPPLIER = Supplier<String> { throw ExpectedException() }
        private val LONG_UNUSED_SUPPLIER = Supplier<Long> { throw UnexpectedException() }
    }
}
