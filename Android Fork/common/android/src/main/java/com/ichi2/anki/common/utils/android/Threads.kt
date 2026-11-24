// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.anki.common.utils.android

import android.os.Looper
import java.lang.RuntimeException

/**
 * Helper class for checking for programming errors while using threads.
 */
object Threads {
    /**
     * @return true if called from the application main thread
     */
    val isOnMainThread: Boolean
        get() =
            try {
                Looper.getMainLooper().thread == Thread.currentThread()
            } catch (exc: RuntimeException) {
                if (exc.message?.contains("Looper not mocked") == true) {
                    // When unit tests are run outside of Robolectric, the call to getMainLooper()
                    // will fail. We swallow the exception in this case, and assume the call was
                    // not made on the main thread.
                    false
                } else {
                    throw exc
                }
            }
}
