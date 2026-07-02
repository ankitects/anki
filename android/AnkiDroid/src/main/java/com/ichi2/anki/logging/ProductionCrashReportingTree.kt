/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.logging

import android.annotation.SuppressLint
import android.util.Log
import com.ichi2.anki.AnkiDroidApp
import timber.log.Timber
import java.util.regex.Pattern

/**
 * A tree which logs necessary data for crash reporting.
 *
 * Requirements:
 * 1) ignore verbose and debug log levels
 * 2) use the fixed AnkiDroidApp.TAG log tag (ACRA filters logcat for it when reporting errors)
 * 3) dynamically discover the class name and prepend it to the message for warn and error
 */
@SuppressLint("LogNotTimber")
class ProductionCrashReportingTree : Timber.Tree() {
    /**
     * Extract the tag which should be used for the message from the `element`. By default
     * this will use the class name without any anonymous class suffixes (e.g., `Foo$1`
     * becomes `Foo`).
     *
     *
     * Note: This will not be called if an API with a manual tag was called with a non-null tag
     */
    fun createStackElementTag(element: StackTraceElement): String {
        val m = ANONYMOUS_CLASS.matcher(element.className)
        val tag = if (m.find()) m.replaceAll("") else element.className
        return tag.substring(tag.lastIndexOf('.') + 1)
    } // --- this is not present in the Timber.DebugTree copy/paste ---

    // We are in production and should not crash the app for a logging failure
    // throw new IllegalStateException(
    //        "Synthetic stacktrace didn't have enough elements: are you using proguard?");
    // --- end of alteration from upstream Timber.DebugTree.getTag ---
    // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
    // because Robolectric runs them on the JVM but on Android the elements are different.
    val tag: String
        get() {
            // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
            // because Robolectric runs them on the JVM but on Android the elements are different.
            val stackTrace = Throwable().stackTrace
            return if (stackTrace.size <= CALL_STACK_INDEX) {

                // --- this is not present in the Timber.DebugTree copy/paste ---
                // We are in production and should not crash the app for a logging failure
                "${AnkiDroidApp.TAG} unknown class"
                // throw new IllegalStateException(
                //        "Synthetic stacktrace didn't have enough elements: are you using proguard?");
                // --- end of alteration from upstream Timber.DebugTree.getTag ---
            } else {
                createStackElementTag(stackTrace[CALL_STACK_INDEX])
            }
        }

    // ----  END copied from Timber.DebugTree because DebugTree.getTag() is package private ----
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        when (priority) {
            Log.VERBOSE, Log.DEBUG -> {}
            Log.INFO -> Log.i(AnkiDroidApp.TAG, message, t)
            Log.WARN -> Log.w(AnkiDroidApp.TAG, "${this.tag}/ $message", t)
            Log.ERROR, Log.ASSERT -> Log.e(AnkiDroidApp.TAG, "${this.tag}/ $message", t)
        }
    }

    companion object {
        // ----  BEGIN copied from Timber.DebugTree because DebugTree.getTag() is package private ----
        private const val CALL_STACK_INDEX = 6
        private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")
    }
}
