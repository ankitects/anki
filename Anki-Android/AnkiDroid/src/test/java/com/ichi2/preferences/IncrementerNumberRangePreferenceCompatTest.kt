/*
 * Copyright (c) 2026 Rakshita Chauhan <chauhanrakshita64@gmail.com>
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

import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat.Companion.ValidationResult
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat.Companion.validate
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncrementerNumberRangePreferenceCompatTest {
    @Test
    fun testValidationLogic() {
        val min = 30
        val max = 99999

        assertThat(
            "A number within the min/max range should be VALID",
            validate("60", 60, min, max),
            IsEqual(ValidationResult.VALID),
        )

        assertThat(
            "An empty string should return EMPTY state",
            validate("", null, min, max),
            IsEqual(ValidationResult.EMPTY),
        )

        assertThat(
            "A number greater than the maximum should return OVERFLOW",
            validate("100000", 100000, min, max),
            IsEqual(ValidationResult.OVERFLOW),
        )

        assertThat(
            "A number less than the minimum should return UNDERFLOW",
            validate("29", 29, min, max),
            IsEqual(ValidationResult.UNDERFLOW),
        )

        assertThat(
            "A number exceeding Integer limits should return INVALID",
            validate("9999999999", null, min, max),
            IsEqual(ValidationResult.INVALID),
        )
    }
}
