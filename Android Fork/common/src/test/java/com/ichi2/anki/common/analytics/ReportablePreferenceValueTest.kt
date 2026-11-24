// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.analytics

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ReportablePreferenceValueTest {
    @Test
    fun `only supported numeric preference values are reportable`() {
        val cases =
            listOf(
                "3" to 3,
                "foo" to null,
                "2147483648" to null,
                "" to null,
                7 to 7,
                true to 1,
                false to 0,
                4.75f to 4,
                42L to 42,
                null to null,
                setOf("3") to null,
            )
        for ((value, expected) in cases) {
            assertEquals(expected, getPreferenceReportableValue(value), "value: $value")
        }
    }
}
