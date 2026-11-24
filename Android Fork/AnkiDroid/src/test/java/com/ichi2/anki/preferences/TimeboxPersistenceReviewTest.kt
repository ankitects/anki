// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.compose.theme.AnkiDroidTheme
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RunWith(AndroidJUnit4::class)
class TimeboxPersistenceReviewTest : RobolectricTest() {
    @Test
    fun `loading preserves a valid desktop timebox above 999 minutes`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(1440.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()

            assertThat(viewModel.uiState.value.limit.minutes, equalTo(1440))
        }

    @Test
    fun `toggle round trip preserves a valid desktop timebox above 999 minutes`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(1440.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()
            viewModel.toggleEnabled(false)
            advanceRobolectricLooper()
            viewModel.toggleEnabled(true)
            advanceRobolectricLooper()

            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(1440))
        }

    @Test
    fun `disabled reload preserves a valid cached desktop timebox above 999 minutes`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(0.toDuration(DurationUnit.MINUTES))
            Prefs.customTimeboxLimit = 1440

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()
            viewModel.toggleEnabled(true)
            advanceRobolectricLooper()

            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(1440))
        }
}

@RunWith(AndroidJUnit4::class)
class TimeboxScreenPersistenceReviewTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `opening settings preserves a valid desktop timebox above 999 minutes`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(1440.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceRobolectricLooper()
            composeRule.setContent {
                val state by viewModel.uiState.collectAsState()
                AnkiDroidTheme {
                    TimeboxSettingsScreen(
                        isLoading = state.isLoading,
                        isEnabled = state.isEnabled,
                        minutes = state.limit.minutes,
                        onToggleEnabled = viewModel::toggleEnabled,
                        onIncrement = viewModel::increment,
                        onDecrement = viewModel::decrement,
                        onSetMinutes = viewModel::setMinutes,
                        onNavigateUp = {},
                    )
                }
            }
            composeRule.waitForIdle()
            advanceRobolectricLooper()

            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(1440))
        }
}
