// SPDX-License-Identifier: GPL-3.0-or-later

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
                putString(PREF_VALID, VALID_RESULT)
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
        val ret = getOrSetString(PREF_VALID, UNUSED_SUPPLIER)
        assertEquals(ret, VALID_RESULT)
    }

    @Test
    fun missingKeyReturnsLambdaValue() {
        val ret = getOrSetString(PREF_MISSING) { LAMBDA_RETURN }
        assertEquals(ret, LAMBDA_RETURN)
    }

    @Test
    fun missingKeySetsPreference() {
        getOrSetString(PREF_MISSING) { LAMBDA_RETURN }
        assertThat(mockPreferences.getString(PREF_MISSING, null), equalTo(LAMBDA_RETURN))
    }

    @SuppressWarnings("unused")
    @Test
    fun noLambdaExceptionIfKeyExists() {
        assertDoesNotThrow { getOrSetString(PREF_VALID, EXCEPTION_SUPPLIER) }
    }

    @Test(expected = ExpectedException::class)
    fun rethrowLambdaExceptionIfKeyIsMissing() {
        getOrSetString(PREF_MISSING, EXCEPTION_SUPPLIER)
    }

    // Long Tests

    @Test
    fun existingLongKeyReturnsStoredValue() {
        mockPreferences.edit { putLong(PREF_LONG_VALID, LONG_VALID_VALUE) }
        val result = getOrSetLong(PREF_LONG_VALID, LONG_UNUSED_SUPPLIER)
        assertEquals(LONG_VALID_VALUE, result)
    }

    @Test
    fun missingLongKeyPersistsValue() {
        getOrSetLong(PREF_LONG_MISSING) { LONG_LAMBDA_VALUE }
        assertEquals(LONG_LAMBDA_VALUE, mockPreferences.getLong(PREF_LONG_MISSING, -1))
    }

    @Test
    fun handlesNegativeLongValue() {
        getOrSetLong(PREF_NEGATIVE_TEST) { LONG_NEGATIVE_VALUE }
        assertEquals(LONG_NEGATIVE_VALUE, mockPreferences.getLong(PREF_NEGATIVE_TEST, 0))
    }

    private class ExpectedException : RuntimeException()

    private class UnexpectedException : RuntimeException()

    companion object {
        // String constants
        private const val PREF_VALID = "VALID"
        private const val VALID_RESULT = "WAS VALID KEY"
        private const val PREF_MISSING = "INVALID"
        private const val LAMBDA_RETURN = "LAMBDA"

        // Long constants
        private const val PREF_LONG_VALID = "LONG_VALID"
        private const val LONG_VALID_VALUE = 42L
        private const val PREF_LONG_MISSING = "LONG_INVALID"
        private const val LONG_LAMBDA_VALUE = 100L
        private const val LONG_NEGATIVE_VALUE = -500L
        private const val PREF_NEGATIVE_TEST = "negative_test"

        // Suppliers
        private val UNUSED_SUPPLIER = Supplier<String> { throw UnexpectedException() }
        private val EXCEPTION_SUPPLIER = Supplier<String> { throw ExpectedException() }
        private val LONG_UNUSED_SUPPLIER = Supplier<Long> { throw UnexpectedException() }
    }
}
