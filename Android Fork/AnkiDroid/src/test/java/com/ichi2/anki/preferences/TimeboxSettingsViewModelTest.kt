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
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TimeboxSettingsViewModelTest : JvmTest() {
    @get:Rule
    val preferences = JvmPreferencesRule()

    @Test
    fun `TimeboxUiState defaults to loading`() {
        val defaultState = TimeboxUiState()
        assertThat("Should default to loading", defaultState.isLoading, equalTo(true))
    }

    @Test
    fun `initializes disabled when time limit is zero`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(0.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat("Should not be loading", state.isLoading, equalTo(false))
            assertThat("Should be disabled", state.isEnabled, equalTo(false))
            assertThat(
                "Default minutes should be 20",
                state.limit.minutes,
                equalTo(TimeboxTimeLimit.DEFAULT_TIMEBOX_MINUTES),
            )
        }

    @Test
    fun `initializes enabled when time limit is greater than zero`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(15.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat("Should be enabled", state.isEnabled, equalTo(true))
            assertThat("Minutes should match configured value", state.limit.minutes, equalTo(15))
        }

    @Test
    fun `toggling enabled on saves minutes to collection`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(0.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            viewModel.toggleEnabled(true)
            advanceUntilIdle()

            assertThat("UI state should be enabled", viewModel.uiState.value.isEnabled, equalTo(true))
            val savedMinutes = CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES)
            assertThat("Collection limit should be 20 minutes", savedMinutes, equalTo(20))
        }

    @Test
    fun `toggling enabled off sets collection limit to zero`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(15.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            viewModel.toggleEnabled(false)
            advanceUntilIdle()

            assertThat("UI state should be disabled", viewModel.uiState.value.isEnabled, equalTo(false))
            val savedMinutes = CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES)
            assertThat("Collection limit should be 0", savedMinutes, equalTo(0))
        }

    @Test
    fun `increment and decrement updates state and saves when enabled`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(10.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            viewModel.increment()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.limit.minutes, equalTo(11))
            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(11))

            viewModel.decrement()
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.limit.minutes, equalTo(10))
            assertThat(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES), equalTo(10))
        }

    @Test
    fun `setMinutes rejects out-of-range values`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(10.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            viewModel.setMinutes(0)
            assertThat("Below minimum should be rejected", viewModel.uiState.value.limit.minutes, equalTo(10))

            viewModel.setMinutes(10000)
            assertThat("Above maximum should be rejected", viewModel.uiState.value.limit.minutes, equalTo(10))

            viewModel.setMinutes(9999)
            assertThat("Valid upper bound should be accepted", viewModel.uiState.value.limit.minutes, equalTo(9999))
        }

    @Test
    fun `initializes with persisted custom limit when disabled`() =
        runTest {
            Prefs.customTimeboxLimit = 45
            CollectionPreferences.setTimeboxTimeLimit(0.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat("Should be disabled", state.isEnabled, equalTo(false))
            assertThat("Should restore persisted minutes", state.limit.minutes, equalTo(45))
        }

    @Test
    fun `setting custom minutes persists to prefs and restores on toggle re-enable`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(15.toDuration(DurationUnit.MINUTES))

            val viewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            viewModel.setMinutes(45)
            advanceUntilIdle()

            assertThat("Prefs should persist customized limit", Prefs.customTimeboxLimit, equalTo(45))

            viewModel.toggleEnabled(false)
            advanceUntilIdle()

            assertThat(
                "Collection limit should be zero when disabled",
                CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
                equalTo(0),
            )
            assertThat("UI state should retain minutes when disabled", viewModel.uiState.value.limit.minutes, equalTo(45))

            viewModel.toggleEnabled(true)
            advanceUntilIdle()

            assertThat(
                "Collection limit should restore custom minutes on re-enable",
                CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
                equalTo(45),
            )
        }

    @Test
    fun `custom limit is preserved across viewModel re-initialization while disabled`() =
        runTest {
            CollectionPreferences.setTimeboxTimeLimit(35.toDuration(DurationUnit.MINUTES))

            val firstViewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            firstViewModel.toggleEnabled(false)
            advanceUntilIdle()
            assertThat(
                "Collection limit should be zero",
                CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
                equalTo(0),
            )

            val secondViewModel = TimeboxSettingsViewModel()
            advanceUntilIdle()

            assertThat("New ViewModel should be disabled", secondViewModel.uiState.value.isEnabled, equalTo(false))
            assertThat("New ViewModel should restore 35 minutes", secondViewModel.uiState.value.limit.minutes, equalTo(35))

            secondViewModel.toggleEnabled(true)
            advanceUntilIdle()
            assertThat(
                "Collection limit should restore 35 minutes",
                CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES),
                equalTo(35),
            )
        }

    @Test
    fun `TimeboxTimeLimit canIncrement and canDecrement adhere to bounds`() {
        assertThat(TimeboxTimeLimit.canDecrement(TimeboxTimeLimit.MIN_TIMEBOX_MINUTES), equalTo(false))
        assertThat(TimeboxTimeLimit.canDecrement(TimeboxTimeLimit.MIN_TIMEBOX_MINUTES + 1), equalTo(true))
        assertThat(TimeboxTimeLimit.canIncrement(TimeboxTimeLimit.MAX_TIMEBOX_MINUTES), equalTo(false))
        assertThat(TimeboxTimeLimit.canIncrement(TimeboxTimeLimit.MAX_TIMEBOX_MINUTES - 1), equalTo(true))
    }

    @Test
    fun `TimeboxTimeLimit orElse returns self when enabled and fallback when disabled`() {
        val fallback = TimeboxTimeLimit(25)
        assertThat(TimeboxTimeLimit(10).orElse(fallback), equalTo(TimeboxTimeLimit(10)))
        assertThat(TimeboxTimeLimit.DISABLED.orElse(fallback), equalTo(fallback))
    }

    @Test
    fun `resolveDisplayLimit updates prefs when enabled and falls back when disabled`() {
        Prefs.customTimeboxLimit = 20
        val enabledLimit = TimeboxTimeLimit(45)
        val resolvedEnabled = enabledLimit.resolveDisplayLimit(Prefs)
        assertThat(resolvedEnabled, equalTo(TimeboxTimeLimit(45)))
        assertThat(Prefs.customTimeboxLimit, equalTo(45))

        val disabledLimit = TimeboxTimeLimit.DISABLED
        val resolvedDisabled = disabledLimit.resolveDisplayLimit(Prefs)
        assertThat(resolvedDisabled, equalTo(TimeboxTimeLimit(45)))
    }
}
