// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.reviewreminders.CheckResult.Failed
import com.ichi2.anki.reviewreminders.CheckResult.Warning
import org.junit.Test

class ReminderTroubleshootingScreenshotTest : ScreenshotTest() {
    @Test
    fun `all checks passing`() {
        targetContext.setTroubleshootingChecks()
        captureReminderTroubleshooting("allChecksPassing")
    }

    @Test
    fun `checks with warnings`() {
        targetContext.setTroubleshootingChecks(
            doNotDisturb = Warning,
            batteryOptimization = Warning,
            powerSavingMode = Warning,
        )

        captureReminderTroubleshooting("checksWithWarnings")
    }

    @Test
    fun `failed checks`() {
        targetContext.setTroubleshootingChecks(
            notificationPermission = Failed,
            notificationChannel = Failed,
            batteryOptimization = Failed,
        )

        captureReminderTroubleshooting("failedChecks")
    }

    /** Shows [ReminderTroubleshootingFragment] as a standalone screen and captures it as [name] */
    private fun captureReminderTroubleshooting(name: String) = withTroubleshootingFragment { captureScreen(name) }
}
