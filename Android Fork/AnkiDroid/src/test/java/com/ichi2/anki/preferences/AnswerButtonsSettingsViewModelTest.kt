// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.testutils.JvmPreferencesRule
import com.ichi2.testutils.JvmTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test

class AnswerButtonsSettingsViewModelTest : JvmTest() {
    @get:Rule
    val preferences = JvmPreferencesRule()

    @Test
    fun `initial state is read from the preferences`() =
        runTest {
            Prefs.showAnswerButtons = false
            Prefs.hideHardAndEasyButtons = true
            Prefs.answerButtonsUseThemeColors = true
            Prefs.newStudyScreenAnswerButtonSize = 150
            CollectionPreferences.setShowIntervalsOnButtons(false)

            val viewModel = AnswerButtonsSettingsViewModel()
            advanceUntilIdle()

            assertThat(
                viewModel.uiState.value,
                equalTo(
                    AnswerButtonsSettingsState(
                        showAnswerButtons = false,
                        showNextTimes = false,
                        hideHardAndEasyButtons = true,
                        useThemeColors = true,
                        buttonSize = 150,
                    ),
                ),
            )
        }

    @Test
    fun `changes are saved`() =
        runTest {
            CollectionPreferences.setShowIntervalsOnButtons(false)
            val viewModel = AnswerButtonsSettingsViewModel()
            advanceUntilIdle()

            viewModel.setShowAnswerButtons(false)
            viewModel.setShowNextTimes(true)
            viewModel.setHideHardAndEasyButtons(true)
            viewModel.setUseThemeColors(true)
            viewModel.setButtonSize(170)
            advanceUntilIdle()

            assertThat(Prefs.showAnswerButtons, equalTo(false))
            assertThat(CollectionPreferences.getShowIntervalOnButtons(), equalTo(true))
            assertThat(Prefs.hideHardAndEasyButtons, equalTo(true))
            assertThat(Prefs.answerButtonsUseThemeColors, equalTo(true))
            assertThat(Prefs.newStudyScreenAnswerButtonSize, equalTo(170))
        }
}
