// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import java.util.concurrent.CountDownLatch

/**
 * Keeps provider workers from accessing application state before Application.onCreate finishes.
 *
 * Android publishes content providers before calling `Application.onCreate`.
 *
 * Call [onProviderCreate] from the provider's onCreate, then [awaitCompletion] before accessing
 * application state. This waits for synchronous application startup, not any asynchronous work
 * it launches.
 */
internal class ContentProviderStartup {
    private val initialized = CountDownLatch(1)

    /** Queues completion after the current main-thread application startup message finishes. */
    @MainThread
    fun onProviderCreate() {
        Handler(Looper.getMainLooper()).post { initialized.countDown() }
    }

    /**
     * Waits for startup on worker threads. Main-thread callers continue without waiting on
     * their own message queue.
     *
     * @throws IllegalStateException if interrupted while waiting; the interrupt flag is preserved.
     */
    fun awaitCompletion() {
        if (Looper.myLooper() == Looper.getMainLooper()) return

        try {
            initialized.await()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for AnkiDroid startup", e)
        }
    }
}
