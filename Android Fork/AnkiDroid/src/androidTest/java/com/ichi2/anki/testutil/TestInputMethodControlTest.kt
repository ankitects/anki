// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class TestInputMethodControlTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uri = "content://${instrumentation.context.packageName}.ime".toUri()

    @Test
    fun instrumentationCanAccessProvider() {
        assertNotNull(instrumentation.context.contentResolver.call(uri, "editor", null, null))
    }

    @Test
    @SdkSuppress(minSdkVersion = 34) // executeShellCommandRwe captures the command's error output.
    fun callerWithDifferentSignatureIsRejected() {
        // The shell has a different signing certificate and makes a real cross-process call.
        val pipes = instrumentation.uiAutomation.executeShellCommandRwe("content call --uri $uri --method editor")
        try {
            pipes[1].close()
            val error = ParcelFileDescriptor.AutoCloseInputStream(pipes[2]).bufferedReader().use { it.readText() }
            assertContains(error, "java.lang.SecurityException: Test IME control requires a matching signature")
        } finally {
            pipes.forEach { it.close() }
        }
    }

    @Test
    fun timeoutReportsTheBlockedThread() {
        val release = CountDownLatch(1)
        val thread = HandlerThread("blocked").apply { start() }
        try {
            Handler(thread.looper).post { release.await() }
            waitUntil(message = { "Thread did not block: ${thread.state}" }) { thread.state == Thread.State.WAITING }

            val failure =
                assertFailsWith<IllegalStateException> {
                    TestInputMethod.Control.runOn(thread.looper, 100, TimeUnit.MILLISECONDS) { Bundle() }
                }

            assertContains(assertNotNull(failure.message), "java.util.concurrent.CountDownLatch.await")
        } finally {
            release.countDown()
            thread.quitSafely()
        }
    }
}
