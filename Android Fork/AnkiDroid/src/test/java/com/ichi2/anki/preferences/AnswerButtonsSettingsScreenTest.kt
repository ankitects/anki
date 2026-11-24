// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class AnswerButtonsSettingsScreenTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    private val defaultState =
        AnswerButtonsSettingsState(
            showAnswerButtons = true,
            showNextTimes = true,
            hideHardAndEasyButtons = false,
            useThemeColors = false,
            buttonSize = 100,
        )

    private fun setScreen(
        state: AnswerButtonsSettingsState = defaultState,
        onHideHardAndEasyButtonsChange: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            AnkiDroidTheme {
                AnswerButtonsSettingsScreen(
                    state = state,
                    showNextTimesTitle = "Show next times",
                    onShowAnswerButtonsChange = {},
                    onShowNextTimesChange = {},
                    onHideHardAndEasyButtonsChange = onHideHardAndEasyButtonsChange,
                    onUseThemeColorsChange = {},
                    onButtonSizeChange = {},
                    onNavigateUp = {},
                )
            }
        }
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    @Test
    fun `preview shows only one answer area`() {
        setScreen()
        composeRule.onAllNodesWithText(string(CommonR.string.ease_button_again), substring = true).assertCountEquals(1)
    }

    @Test
    fun `preview hides hard and easy buttons`() {
        setScreen(defaultState.copy(showNextTimes = false, hideHardAndEasyButtons = true))
        composeRule.onAllNodesWithText(string(CommonR.string.ease_button_again)).assertCountEquals(1)
        composeRule.onAllNodesWithText(string(CommonR.string.ease_button_hard)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(CommonR.string.ease_button_easy)).assertCountEquals(0)
    }

    @Test
    fun `preview hides answer area if answer buttons are disabled`() {
        setScreen(defaultState.copy(showAnswerButtons = false))
        composeRule.onAllNodesWithText(string(CommonR.string.ease_button_again), substring = true).assertCountEquals(0)
        composeRule.onNodeWithText(string(CommonR.string.answer_buttons_hidden)).assertIsDisplayed()
    }

    @Test
    fun `preview doesn't show buttons hidden text if answer buttons are enabled`() {
        setScreen()
        composeRule.onAllNodesWithText(string(CommonR.string.answer_buttons_hidden)).assertCountEquals(0)
    }

    @Test
    fun `clicking a switch row toggles it`() {
        var hideHardAndEasy: Boolean? = null
        setScreen(onHideHardAndEasyButtonsChange = { hideHardAndEasy = it })
        composeRule.onNodeWithText(string(CommonR.string.hide_hard_and_easy)).performClick()
        assertEquals(true, hideHardAndEasy)
    }
}
