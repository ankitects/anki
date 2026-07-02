/*
 * Copyright (c) 2025 Pankaj Gupta <pankajgupta0695@gmail.com>
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
package com.ichi2.preferences

import android.content.Context
import android.widget.EditText
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NumberRangePreferenceCompatTest : RobolectricTest() {
    private val preference = TestableNumberRangePreferenceCompat(targetContext)
    private val preferenceMock: NumberRangePreferenceCompat = mockk(relaxed = true)

    @Test
    fun getValidatedRangeFromIntWithinRangeReturnsInput() {
        preference.setMinValue(0)
        assertThat(preference.getValidatedRangeFromInt(50), equalTo(50))
    }

    @Test
    fun getValidatedRangeFromIntBelowMinReturnsMin() {
        preference.setMinValue(10)
        assertThat(preference.getValidatedRangeFromInt(5), equalTo(10))
    }

    @Test
    fun getValidatedRangeFromStringEmptyStringReturnsMin() {
        preference.setMinValue(5)
        assertThat(preference.getValidatedRangeFromString(""), equalTo(5))
    }

    @Test
    fun getValidatedRangeFromStringValidNumberReturnsValidatedNumber() {
        preference.setMinValue(0)
        assertThat(preference.getValidatedRangeFromString("50"), equalTo(50))
    }

    @Test
    fun getValidatedRangeFromStringInvalidNumberReturnsMin() {
        preference.setMinValue(5)
        assertThat(preference.getValidatedRangeFromString("abc"), equalTo(5))
    }

    @Test
    fun onDialogClosedWithBlankTextDoesNotCallSetValue() {
        clearMocks(preferenceMock)
        every { preferenceMock.getValidatedRangeFromString(any()) } answers { 0 }
        every { preferenceMock.callChangeListener(any()) } returns true

        val dialogFragment = createDialogWithText("", preferenceMock)
        dialogFragment.onDialogClosed(true)

        verify(exactly = 0) { preferenceMock.setValue(any<Int>()) }
    }

    @Test
    fun onDialogClosedWithCancelledDialogDoesNotCallSetValue() {
        clearMocks(preferenceMock)
        every { preferenceMock.getValidatedRangeFromString(any()) } answers { 99 }
        every { preferenceMock.callChangeListener(any()) } returns true

        val dialogFragment = createDialogWithText("99", preferenceMock)
        dialogFragment.onDialogClosed(false)

        verify(exactly = 0) { preferenceMock.setValue(any<Int>()) }
    }

    @Test
    fun onDialogClosedWithValidTextCallsSetValue() {
        clearMocks(preferenceMock)
        every { preferenceMock.getValidatedRangeFromString("75") } returns 75
        every { preferenceMock.callChangeListener(75) } returns true

        val dialogFragment = createDialogWithText("75", preferenceMock)
        dialogFragment.onDialogClosed(true)

        verify(exactly = 1) { preferenceMock.setValue(75) }
    }

    private fun createDialogWithText(
        text: String,
        pref: NumberRangePreferenceCompat,
    ): NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat {
        val dialogFragment = NumberRangePreferenceCompat.NumberRangeDialogFragmentCompat()

        val preferenceField = PreferenceDialogFragmentCompat::class.java.getDeclaredField("mPreference")
        preferenceField.isAccessible = true
        preferenceField.set(dialogFragment, pref)

        val editText = EditText(targetContext)
        editText.setText(text)
        dialogFragment.editText = editText

        return dialogFragment
    }

    // Test subclass to allow setting min value for testing
    private class TestableNumberRangePreferenceCompat(
        context: Context,
    ) : NumberRangePreferenceCompat(context) {
        fun setMinValue(value: Int) {
            min = value
        }

        override fun callChangeListener(newValue: Any?): Boolean = true
    }
}
