// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.fragment.app.commit
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.reviewreminders.CheckResult.Failed
import com.ichi2.anki.reviewreminders.CheckResult.Passed
import com.ichi2.anki.reviewreminders.CheckResult.Warning
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment.FragmentHost
import org.robolectric.Shadows.shadowOf
import android.app.NotificationChannel as AndroidNotificationChannel

/** Runs [block] on a [ReminderTroubleshootingFragment] in its standalone activity */
context(test: RobolectricTest)
fun withTroubleshootingFragment(block: ReminderTroubleshootingFragment.() -> Unit) =
    withStandaloneScheduleReminders { activity ->
        val fragment = ReminderTroubleshootingFragment.newInstance(FragmentHost.STANDALONE_ACTIVITY)
        activity.supportFragmentManager.commit { replace(R.id.fragment_container, fragment) }
        advanceRobolectricLooper()
        fragment.block()
    }

/**
 * Sets the outcomes of the visible troubleshooting checks using Android service shadows.
 * Unspecified checks pass. The real repository and ViewModel still evaluate the checks.
 *
 * Only outcomes produced by these system settings are supported: [Passed]/[Failed] for
 * notification checks, [Passed]/[Warning] for Do Not Disturb and power saving, and all three
 * for battery optimization. [CheckResult.Error] represents an exception, not a failed setting.
 * Exact-alarm checking remains unavailable while its permission is absent from the manifest.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O) // createNotificationChannel, NotificationChannel
internal fun Context.setTroubleshootingChecks(
    notificationPermission: CheckResult = Passed,
    notificationChannel: CheckResult = Passed,
    doNotDisturb: CheckResult = Passed,
    batteryOptimization: CheckResult = Passed,
    powerSavingMode: CheckResult = Passed,
) {
    val notificationsEnabled = notificationPermission.isPassingOr(Failed, "notification permission")
    val channelEnabled = notificationChannel.isPassingOr(Failed, "notification channel")
    val doNotDisturbOff = doNotDisturb.isPassingOr(Warning, "Do Not Disturb")
    val powerSavingOff = powerSavingMode.isPassingOr(Warning, "power saving")
    require(batteryOptimization in listOf(Passed, Warning, Failed)) {
        "Unsupported battery optimization result: $batteryOptimization"
    }

    val notificationManager = getSystemService<NotificationManager>()!!
    shadowOf(notificationManager).apply {
        setNotificationsEnabled(notificationsEnabled)
        setNotificationPolicyAccessGranted(true)
    }
    notificationManager.setInterruptionFilter(
        if (doNotDisturbOff) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
    )
    notificationManager.createNotificationChannel(
        AndroidNotificationChannel(
            NotificationChannel.REVIEW_REMINDERS.id,
            "Review reminders",
            if (channelEnabled) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_NONE,
        ),
    )
    shadowOf(getSystemService<PowerManager>()!!).apply {
        setIgnoringBatteryOptimizations(packageName, batteryOptimization == Passed)
        setIsPowerSaveMode(!powerSavingOff)
    }
    shadowOf(getSystemService<ActivityManager>()!!).setBackgroundRestricted(batteryOptimization == Failed)
}

private fun CheckResult.isPassingOr(
    other: CheckResult,
    checkName: String,
): Boolean {
    require(this == Passed || this == other) { "Unsupported $checkName result: $this" }
    return this == Passed
}
