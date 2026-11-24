// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import com.ichi2.anki.RobolectricTest
import com.ichi2.preferences.HeaderPreference
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Test for [Preferences] without [RobolectricTest]. For performance
 */
class PreferencesSimpleTest {
    @ParameterizedTest
    @MethodSource("buildCategorySummary_LTR_Test_args")
    fun buildCategorySummary_LTR_Test(
        entries: Array<String>,
        expectedSummary: String,
    ) {
        assertThat(HeaderPreference.buildHeaderSummary(*entries), equalTo(expectedSummary))
    }

    companion object {
        @JvmStatic // required for @MethodSource
        fun buildCategorySummary_LTR_Test_args(): Stream<Arguments> =
            Stream.of(
                Arguments.of(arrayOf(""), ""),
                Arguments.of(arrayOf("foo"), "foo"),
                Arguments.of(arrayOf("foo", "bar"), "foo • bar"),
                Arguments.of(arrayOf("foo", "bar", "hi", "there"), "foo • bar • hi • there"),
            )
    }
}
