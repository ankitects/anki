/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewreminders

import com.ichi2.anki.reviewreminders.TroubleshootingCheck.DoNotDisturbOff
import com.ichi2.anki.reviewreminders.TroubleshootingCheck.ExactAlarmPermission
import com.ichi2.anki.reviewreminders.TroubleshootingCheck.NotificationPermission
import com.ichi2.anki.reviewreminders.TroubleshootingCheck.PowerSavingModeOff
import com.ichi2.anki.reviewreminders.TroubleshootingCheck.UnrestrictedOptimizationEnabled
import com.ichi2.testutils.TestException
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class ReminderTroubleshootingViewModelTest {
    @Test
    fun `notification permission passed when granted`() {
        val repo = mockRepository(notificationPermission = true)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.notificationPermissionResult, equalTo(CheckResult.Passed))
    }

    @Test
    fun `notification permission failed when not granted`() {
        val repo = mockRepository(notificationPermission = false)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.notificationPermissionResult, equalTo(CheckResult.Failed))
    }

    @Test
    fun `notification permission error on exception`() {
        val repo =
            mockRepository().also {
                every { it.isNotificationPermissionGranted() } throws TestException("test")
            }
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.notificationPermissionResult is CheckResult.Error, equalTo(true))
    }

    @Test
    fun `refreshChecks updates state`() {
        val repo = mockRepository(notificationPermission = false)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.notificationPermissionResult, equalTo(CheckResult.Failed))

        every { repo.isNotificationPermissionGranted() } returns true
        viewModel.refreshChecks()

        assertThat(viewModel.notificationPermissionResult, equalTo(CheckResult.Passed))
    }

    @Test
    fun `Loading hasIssue is false`() = assertThat(CheckResult.Loading.hasIssue, equalTo(false))

    @Test
    fun `Passed hasIssue is false`() = assertThat(CheckResult.Passed.hasIssue, equalTo(false))

    @Test
    fun `Failed hasIssue is true`() = assertThat(CheckResult.Failed.hasIssue, equalTo(true))

    @Test
    fun `Warning hasIssue is true`() = assertThat(CheckResult.Warning.hasIssue, equalTo(true))

    @Test
    fun `Unavailable hasIssue is false`() = assertThat(CheckResult.Unavailable.hasIssue, equalTo(false))

    @Test
    fun `Error hasIssue is true`() = assertThat(CheckResult.Error(TestException("")).hasIssue, equalTo(true))

    @Test
    fun `do not disturb passed when off`() {
        val repo = mockRepository(doNotDisturb = true)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.doNotDisturbResult, equalTo(CheckResult.Passed))
    }

    @Test
    fun `do not disturb warning when on`() {
        val repo = mockRepository(doNotDisturb = false)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.doNotDisturbResult, equalTo(CheckResult.Warning))
    }

    @Test
    fun `battery optimization passed when unrestricted`() {
        val repo = mockRepository(batteryOptimization = BatteryOptimizationState.Unrestricted)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.batteryOptimizationResult, equalTo(CheckResult.Passed))
    }

    @Test
    fun `battery optimization warning when optimized`() {
        val repo = mockRepository(batteryOptimization = BatteryOptimizationState.Optimized)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.batteryOptimizationResult, equalTo(CheckResult.Warning))
    }

    @Test
    fun `battery optimization failed when restricted`() {
        val repo = mockRepository(batteryOptimization = BatteryOptimizationState.Restricted)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.batteryOptimizationResult, equalTo(CheckResult.Failed))
    }

    @Test
    fun `power saving mode passed when off`() {
        val repo = mockRepository(powerSavingModeOff = true)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.powerSavingModeResult, equalTo(CheckResult.Passed))
    }

    @Test
    fun `power saving mode warning when on`() {
        val repo = mockRepository(powerSavingModeOff = false)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.powerSavingModeResult, equalTo(CheckResult.Warning))
    }

    @Test
    fun `exact alarm permission passed when granted`() {
        val repo = mockRepository(exactAlarmPermission = true)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.exactAlarmPermissionResult, equalTo(CheckResult.Passed))
    }

    @Test
    fun `exact alarm permission warning when not granted`() {
        val repo = mockRepository(exactAlarmPermission = false)
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.exactAlarmPermissionResult, equalTo(CheckResult.Warning))
    }

    @Test
    fun `exact alarm permission unavailable when not in manifest`() {
        val repo =
            mockRepository().also {
                every { it.isExactAlarmPermissionGranted() } returns null
            }
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.exactAlarmPermissionResult, equalTo(CheckResult.Unavailable))
    }

    @Test
    fun `summary status is Ok when all checks pass`() =
        withViewModel {
            assertThat(state.value.summaryStatus, equalTo(SummaryStatus.Ok))
        }

    @Test
    fun `summary status is Warning when non-critical check fails`() =
        withViewModel(batteryOptimization = BatteryOptimizationState.Optimized) {
            assertThat(state.value.summaryStatus, equalTo(SummaryStatus.Warning))
        }

    @Test
    fun `summary status is Warning when DND is on`() =
        withViewModel(doNotDisturb = false) {
            assertThat(state.value.summaryStatus, equalTo(SummaryStatus.Warning))
        }

    @Test
    fun `summary status is Error when notification permission denied`() =
        withViewModel(notificationPermission = false) {
            assertThat(state.value.summaryStatus, equalTo(SummaryStatus.Error))
        }

    @Test
    fun `summary status is Error when notification permission throws`() {
        val repo =
            mockRepository().also {
                every { it.isNotificationPermissionGranted() } throws TestException("test")
            }
        val viewModel = ReminderTroubleshootingViewModel(repo)

        assertThat(viewModel.state.value.summaryStatus, equalTo(SummaryStatus.Error))
    }

    @Test
    fun `summary status Error takes priority over other warnings`() =
        withViewModel(notificationPermission = false, doNotDisturb = false) {
            assertThat(state.value.summaryStatus, equalTo(SummaryStatus.Error))
        }

    // The `when` below will fail to compile if a new TroubleshootingCheck is added,
    // reminding you to add it to this test and to ReminderTroubleshootingState.checks
    @Test
    fun `checks contains all checks`() =
        withViewModel {
            var count = 0
            for (check in state.value.checks) {
                when (check) {
                    is NotificationPermission -> count++
                    is DoNotDisturbOff -> count++
                    is UnrestrictedOptimizationEnabled -> count++
                    is PowerSavingModeOff -> count++
                    is ExactAlarmPermission -> count++
                }
            }
            assertThat(count, equalTo(5))
        }

    private fun mockRepository(
        notificationPermission: Boolean = true,
        doNotDisturb: Boolean = true,
        batteryOptimization: BatteryOptimizationState = BatteryOptimizationState.Unrestricted,
        powerSavingModeOff: Boolean = true,
        exactAlarmPermission: Boolean = true,
    ): ReminderTroubleshootingRepository =
        mockk {
            every { isNotificationPermissionGranted() } returns notificationPermission
            every { isDoNotDisturbOff() } returns doNotDisturb
            every { getBatteryOptimizationState() } returns batteryOptimization
            every { isPowerSavingModeOff() } returns powerSavingModeOff
            every { isExactAlarmPermissionGranted() } returns exactAlarmPermission
        }

    private fun withViewModel(
        notificationPermission: Boolean = true,
        doNotDisturb: Boolean = true,
        batteryOptimization: BatteryOptimizationState = BatteryOptimizationState.Unrestricted,
        powerSavingModeOff: Boolean = true,
        exactAlarmPermission: Boolean = true,
        block: ReminderTroubleshootingViewModel.() -> Unit,
    ) {
        val repo = mockRepository(notificationPermission, doNotDisturb, batteryOptimization, powerSavingModeOff, exactAlarmPermission)
        ReminderTroubleshootingViewModel(repo).block()
    }
}

private val ReminderTroubleshootingViewModel.notificationPermissionResult: CheckResult
    get() = state.value.notificationPermission.result

private val ReminderTroubleshootingViewModel.doNotDisturbResult: CheckResult
    get() = state.value.doNotDisturbOff.result

private val ReminderTroubleshootingViewModel.batteryOptimizationResult: CheckResult
    get() = state.value.batteryOptimizationDisabled.result

private val ReminderTroubleshootingViewModel.powerSavingModeResult: CheckResult
    get() = state.value.powerSavingModeOff.result

private val ReminderTroubleshootingViewModel.exactAlarmPermissionResult: CheckResult
    get() = state.value.exactAlarmPermission.result
