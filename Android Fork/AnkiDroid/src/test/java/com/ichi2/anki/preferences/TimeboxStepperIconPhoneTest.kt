// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class TimeboxStepperIconPhoneTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Timebox stepper icons scale up on a 360dp phone`() = assertIconSize(qualifiers = "w360dp-h800dp", expected = 28.dp)

    @Test
    fun `Timebox stepper icons scale up on a 384dp phone`() = assertIconSize(qualifiers = "w384dp-h832dp", expected = 28.dp)

    private fun assertIconSize(
        qualifiers: String,
        expected: Dp,
    ) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        composeRule.setContent {
            AnkiDroidTheme {
                TimeboxSettingsScreen(
                    isLoading = false,
                    isEnabled = true,
                    minutes = 20,
                    onToggleEnabled = {},
                    onIncrement = {},
                    onDecrement = {},
                    onSetMinutes = {},
                    onNavigateUp = {},
                )
            }
        }
        composeRule.waitForIdle()
        for (description in listOf(CommonString.minus_sign, CommonString.plus_sign)) {
            composeRule
                .onNodeWithContentDescription(targetContext.getString(description), useUnmergedTree = true)
                .assertWidthIsEqualTo(expected)
        }
    }
}
