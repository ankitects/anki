// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.workarounds

import android.app.Activity
import android.os.Bundle
import android.os.Process
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.android.ApplicationContextInitializer
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.exception.ManuallyReportedException
import com.ichi2.anki.common.utils.android.showThemedToast
import timber.log.Timber

/**
 * Handles an issue where the app is loaded via `bmgr`, which means `AnkiDroidApp.onCreate` is not called
 */
object AppLoadedFromBackupWorkaround {
    /**
     * @param savedInstanceState bundle provided to [Activity.onCreate]
     * @param activitySuperOnCreate The [Activity.onCreate] to be called
     *
     * We have [activitySuperOnCreate] as Activity.onCreate is protected, so we can't easily call this from here.
     * A lambda is better than reflection, although it adds another parameter
     *
     * @return true if `AnkiDroidApp` was not initialised properly, an 'activity failed' toast was
     * displayed and the app will be killed. `false` if the app started normally
     */
    fun Activity.showedActivityFailedScreen(
        savedInstanceState: Bundle?,
        activitySuperOnCreate: (Bundle?) -> Unit,
    ): Boolean {
        if (ApplicationContextInitializer.instanceOrNull != null) {
            return false
        }
        // #7630: Can be triggered with `adb shell bmgr restore com.ichi2.anki` after AnkiDroid settings are changed.
        // Application.onCreate() is not called if:
        // * The App was open
        // * A restore took place
        // * The app is reopened (until it exits: finish() does not do this - and removes it from the app list)
        Timber.w("Activity started with no application instance")
        showThemedToast(
            this,
            getString(CommonString.ankidroid_cannot_open_after_backup_try_again),
            false,
        )
        CrashReportService.sendExceptionReport(
            ManuallyReportedException("19050: Activity started with no application instance"),
            origin = "showedActivityFailedScreen",
            additionalInfo = null,
            onlyIfSilent = true,
            context = this,
        )

        // Avoids a SuperNotCalledException
        activitySuperOnCreate(savedInstanceState)
        // Process.killProcess is a hard kill. I suspect that some Android OSes leave has the app in
        // an invalid state after this occurs (meaning Application.onCreate is not called).
        // Before killProcess, gracefully kill the app, removing it from the recents list
        finishAndRemoveTask()

        // If we don't kill the process, the backup is not "done" and reopening the app show the same message.
        Thread {
            // 3.5 seconds sleep, as the toast is killed on process death.
            // Same as the default value of LENGTH_LONG
            try {
                Thread.sleep(3500)
            } catch (e: InterruptedException) {
                Timber.w(e)
            }
            Timber.e("killing process")
            Process.killProcess(Process.myPid())
        }.start()
        return true
    }
}
