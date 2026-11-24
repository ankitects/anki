// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import android.app.Activity
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import anki.collection.OpChanges
import com.ichi2.anki.TestUtils
import com.ichi2.anki.observability.ChangeManager
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Polls until [condition] returns true, syncing with the instrumentation between attempts.
 * Fails the test with [message] if the condition does not hold within [timeout].
 */
fun waitUntil(
    timeout: Duration = 10.seconds,
    message: () -> String,
    condition: () -> Boolean,
) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = SystemClock.uptimeMillis() + timeout.inWholeMilliseconds
    while (SystemClock.uptimeMillis() < deadline) {
        if (condition()) return
        instrumentation.waitForIdleSync()
        SystemClock.sleep(50)
    }
    fail(message())
}

/**
 * Polls until an activity of type [T] is resumed and returns it.
 * Fails the test if [T] does not resume within [timeout].
 */
inline fun <reified T : Activity> awaitResumedActivity(timeout: Duration = 10.seconds): T {
    var activity: T? = null
    waitUntil(
        timeout,
        message = { "Timed out waiting for ${T::class.java.simpleName}; resumed = ${TestUtils.activityInstance}" },
    ) {
        activity = TestUtils.activityInstance as? T
        activity != null
    }
    return checkNotNull(activity)
}

/**
 * Runs [block] on the instrumentation thread with the resumed activity of type [T].
 * Even if [block] fails, closes the activity and awaits destruction before collection cleanup.
 */
inline fun <reified T : Activity> useResumedActivity(block: (T) -> Unit) {
    val activity = awaitResumedActivity<T>()
    try {
        block(activity)
    } finally {
        activity.finishAndAwaitDestruction()
    }
}

/** Keep the collection open until the activity can no longer access it. */
fun Activity.finishAndAwaitDestruction() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync { finishAndRemoveTask() }
    waitUntil(message = { "Timed out waiting for ${javaClass.simpleName} to be destroyed" }) {
        var destroyed = false
        instrumentation.runOnMainSync { destroyed = isDestroyed }
        destroyed
    }
}

/**
 * Waits for all published [OpChanges] to be delivered to [ChangeManager] subscribers.
 */
fun ChangeManager.awaitPendingOpChanges() {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
