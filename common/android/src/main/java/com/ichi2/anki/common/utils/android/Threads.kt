/*
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
