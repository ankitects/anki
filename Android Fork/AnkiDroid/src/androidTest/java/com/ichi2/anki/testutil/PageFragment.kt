// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.pages.PageFragment
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/** Poll across page reloads, which can discard an individual JavaScript evaluation callback. */
fun PageFragment.waitForPageCondition(
    condition: String,
    message: String,
) {
    val satisfied = AtomicBoolean(false)
    waitUntil(timeout = 30.seconds, message = { message }) {
        // Reloading can discard an evaluation callback. Retry without waiting for each one.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webViewLayout.evaluateJavascript(condition) {
                if (it == "true") satisfied.set(true)
            }
        }
        satisfied.get()
    }
}
