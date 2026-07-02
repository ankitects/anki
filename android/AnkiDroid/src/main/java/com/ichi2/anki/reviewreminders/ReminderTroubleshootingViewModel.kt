/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The result of a troubleshooting check
 */
sealed class CheckResult {
    data object Loading : CheckResult()

    data object Passed : CheckResult()

    data object Failed : CheckResult()

    data object Warning : CheckResult()

    data object Unavailable : CheckResult()

    data class Error(
        val exception: Exception,
    ) : CheckResult()

    /** Whether this result indicates a problem (failed, warning, or error). */
    val hasIssue: Boolean
        get() = this is Failed || this is Warning || this is Error

    companion object {
        fun from(
            check: () -> Boolean?,
            onFailure: CheckResult,
        ): CheckResult =
            try {
                when (check()) {
                    true -> Passed
                    false -> onFailure
                    null -> Unavailable
                }
            } catch (e: Exception) {
                Error(e)
            }
    }
}

/**
 * A potential issue which could cause issues receiving reminders.
 *
 * The sealed class ensures that all cases are handled.
 */
sealed class TroubleshootingCheck {
    abstract val result: CheckResult

    data class NotificationPermission(
        override val result: CheckResult = CheckResult.Loading,
    ) : TroubleshootingCheck()

    /**
     * Checks whether Do Not Disturb is off.
     *
     * This is a [warning][CheckResult.Warning], not a failure, because DND may be
     * intentionally enabled and can silently mute notifications depending on the
     * user's DND filter settings.
     */
    data class DoNotDisturbOff(
        override val result: CheckResult = CheckResult.Loading,
    ) : TroubleshootingCheck()

    /**
     * App Info - App battery usage - Allow background usage -
     *
     * - 'Disabled' → [CheckResult.Failed] - Background usage is disabled.
     * - 'Optimized' → [CheckResult.Warning] - Optimized based on your usage.
     * - 'Unrestricted' → [CheckResult.Passed] - Allow battery usage in the background.
     */
    data class UnrestrictedOptimizationEnabled(
        override val result: CheckResult = CheckResult.Loading,
    ) : TroubleshootingCheck()

    /**
     * Power saving can delay notifications being fired.
     *
     * OEMs have different restrictions on Android when this feature is enabled
     *
     * In addition: Doze mode can defer AlarmManager calls unless
     * a `set...AndAllowWhenIdle` call is made.
     *
     * [developer.android.com: Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
     */
    data class PowerSavingModeOff(
        override val result: CheckResult = CheckResult.Loading,
    ) : TroubleshootingCheck()

    /**
     * A user can request [android.Manifest.permission.SCHEDULE_EXACT_ALARM] on API 31+
     * which allows scheduling of exact alarms.
     *
     * TODO: We do not currently support this in the manifest, but we should do
     * before launch
     *
     * https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
     */
    data class ExactAlarmPermission(
        override val result: CheckResult = CheckResult.Loading,
    ) : TroubleshootingCheck()

    // Potential future indicators:
    // - Check if 'BootService' executed
    // - Daylio uses 'Last System Reboot Time', but the rationale/heuristics are unknown

    // Other indicators which are currently out of scope:
    // - Notification cap warning: `MAX_PACKAGE_NOTIFICATIONS` - If a user has too many visible
    //    notifications, this should be obvious, and it dilutes the actionable checks.
}

/**
 * The overall status of the troubleshooting checks
 */
enum class SummaryStatus {
    /** All visible checks are passing. */
    Ok,

    /** Other checks have warnings or failures — reminders may not work correctly. */
    Warning,

    /** Notification permission is denied or errored — reminders cannot work. */
    Error,
}

data class ReminderTroubleshootingState(
    val notificationPermission: TroubleshootingCheck.NotificationPermission =
        TroubleshootingCheck.NotificationPermission(),
    val doNotDisturbOff: TroubleshootingCheck.DoNotDisturbOff =
        TroubleshootingCheck.DoNotDisturbOff(),
    val batteryOptimizationDisabled: TroubleshootingCheck.UnrestrictedOptimizationEnabled =
        TroubleshootingCheck.UnrestrictedOptimizationEnabled(),
    val powerSavingModeOff: TroubleshootingCheck.PowerSavingModeOff =
        TroubleshootingCheck.PowerSavingModeOff(),
    val exactAlarmPermission: TroubleshootingCheck.ExactAlarmPermission =
        TroubleshootingCheck.ExactAlarmPermission(),
) {
    val checks: List<TroubleshootingCheck>
        get() =
            listOf(
                notificationPermission,
                doNotDisturbOff,
                batteryOptimizationDisabled,
                powerSavingModeOff,
                exactAlarmPermission,
            )

    /**
     * The overall status of troubleshooting checks
     *
     * @see SummaryStatus
     */
    val summaryStatus: SummaryStatus
        get() {
            // If the notification permission is defined, notifications cannot be shown.
            val permissionCheck = notificationPermission.result
            if (permissionCheck is CheckResult.Failed || permissionCheck is CheckResult.Error) {
                return SummaryStatus.Error
            }

            // Any other 'Failed' check is a warning:
            // UnrestrictedOptimizationEnabled has 'Background usage is disabled.' => 'Failure'
            // But this is designed to warn the user prominently about a mistake, rather than
            // appear as a fatal error for the reminders.
            val hasIssues = checks.any { it.result.hasIssue }
            return if (hasIssues) SummaryStatus.Warning else SummaryStatus.Ok
        }
}

class ReminderTroubleshootingViewModel(
    private val repository: ReminderTroubleshootingRepository,
) : ViewModel() {
    val state: StateFlow<ReminderTroubleshootingState>
        field = MutableStateFlow(ReminderTroubleshootingState())

    init {
        refreshChecks()
    }

    fun refreshChecks() {
        state.value =
            state.value.copy(
                notificationPermission =
                    state.value.notificationPermission.copy(
                        result = CheckResult.from(repository::isNotificationPermissionGranted, onFailure = CheckResult.Failed),
                    ),
                doNotDisturbOff =
                    state.value.doNotDisturbOff.copy(
                        result = CheckResult.from(repository::isDoNotDisturbOff, onFailure = CheckResult.Warning),
                    ),
                batteryOptimizationDisabled =
                    state.value.batteryOptimizationDisabled.copy(
                        result = refreshBatteryOptimization(),
                    ),
                powerSavingModeOff =
                    state.value.powerSavingModeOff.copy(
                        result = CheckResult.from(repository::isPowerSavingModeOff, onFailure = CheckResult.Warning),
                    ),
                exactAlarmPermission =
                    state.value.exactAlarmPermission.copy(
                        result = CheckResult.from(repository::isExactAlarmPermissionGranted, onFailure = CheckResult.Warning),
                    ),
            )
    }

    private fun refreshBatteryOptimization(): CheckResult =
        try {
            val batteryState =
                repository.getBatteryOptimizationState()
                    ?: return CheckResult.Unavailable
            when (batteryState) {
                BatteryOptimizationState.Unrestricted -> CheckResult.Passed
                BatteryOptimizationState.Optimized -> CheckResult.Warning
                // Optimized is the default, but the Android UI encourages a user to select
                // 'Restricted', rather than tap into the option and select 'Unrestricted'
                // Setting this check to 'Failed' informs the user that they have made things worse.
                BatteryOptimizationState.Restricted -> CheckResult.Failed
            }
        } catch (e: Exception) {
            CheckResult.Error(e)
        }
}
