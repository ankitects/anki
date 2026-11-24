// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val HIDE_ERROR_DIALOGS = "hide_error_dialogs"

/** Causes an ANR in the test APK's separate process and opens its trace. The caller must close the stream. */
@RequiresApi(Build.VERSION_CODES.R)
fun captureAnrTrace(): InputStream {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    val resolver = instrumentation.targetContext.contentResolver
    val previousHideDialogs = Settings.Global.getString(resolver, HIDE_ERROR_DIALOGS)
    return automation.withShellPermissions(Manifest.permission.DUMP, Manifest.permission.WRITE_SECURE_SETTINGS) {
        try {
            // Let Android close the unresponsive process without requiring a dialog interaction.
            assertTrue(Settings.Global.putInt(resolver, HIDE_ERROR_DIALOGS, 1), "Cannot hide ANR dialogs")
            instrumentation.withAnrActivity { pid, startedAt ->
                automation.injectAnrInput()
                val activityManager = instrumentation.targetContext.getSystemService(ActivityManager::class.java)
                val anr = activityManager.awaitAnrExit(instrumentation.context.packageName, pid, startedAt)
                assertNotNull(anr.traceInputStream, "Android recorded an ANR without a trace")
            }
        } finally {
            assertTrue(Settings.Global.putString(resolver, HIDE_ERROR_DIALOGS, previousHideDialogs), "Cannot restore ANR dialogs")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun <T> UiAutomation.withShellPermissions(
    vararg permissions: String,
    block: () -> T,
): T {
    adoptShellPermissionIdentity(*permissions)
    try {
        return block()
    } finally {
        dropShellPermissionIdentity()
    }
}

/** Waits for the activity's focused window before running [block], and stops its process on failure. */
@RequiresApi(Build.VERSION_CODES.R)
private fun <T> Instrumentation.withAnrActivity(block: (pid: Int, startedAt: Long) -> T): T {
    val ready = ArrayBlockingQueue<Bundle>(1)
    val receiver =
        object : ResultReceiver(null) {
            override fun onReceiveResult(
                resultCode: Int,
                resultData: Bundle,
            ) {
                ready.offer(resultData)
            }
        }

    // Android timestamps these exits using the real wall clock.
    val startedAt = System.currentTimeMillis()
    context.startActivity(
        Intent()
            .setClassName(context.packageName, AnrTestActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("ready", receiver),
    )
    val probe = assertNotNull(ready.poll(20, TimeUnit.SECONDS), "ANR activity never gained focus")
    probe.classLoader = AnrTestActivity::class.java.classLoader
    val stop = assertNotNull(BundleCompat.getParcelable(probe, "stop", ResultReceiver::class.java))
    try {
        return block(probe.getInt("pid"), startedAt)
    } finally {
        stop.send(0, Bundle())
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun ActivityManager.awaitAnrExit(
    testPackage: String,
    pid: Int,
    startedAt: Long,
): ApplicationExitInfo {
    var exit: ApplicationExitInfo? = null
    waitUntil(60.seconds, message = { "Android did not record an ANR for test PID $pid; latest exit: $exit" }) {
        exit = getHistoricalProcessExitReasons(testPackage, pid, 1).firstOrNull()
        exit != null
    }
    return assertNotNull(exit).also {
        assertEquals(ApplicationExitInfo.REASON_ANR, it.reason)
        assertEquals("$testPackage:anr_trace_test", it.processName)
        assertTrue(it.timestamp >= startedAt, "Must inspect this run's ANR, not an older exit")
    }
}

private fun UiAutomation.injectAnrInput() {
    val now = SystemClock.uptimeMillis()
    for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
        val event = KeyEvent(now, now, action, KeyEvent.KEYCODE_DPAD_CENTER, 0)
        // Waiting for delivery would hang behind the intentionally blocked main thread.
        assertTrue(injectInputEvent(event, false), "Failed to inject ANR-triggering input")
    }
}
