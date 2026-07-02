/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.utils

import android.content.Context
import androidx.annotation.CheckResult
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.android.showThemedToast
import java.io.PrintWriter
import java.io.StringWriter

object ExceptionUtil {
    @CheckResult
    fun getExceptionMessage(e: Throwable?): String = getExceptionMessage(e, "\n")

    @CheckResult
    fun getExceptionMessage(
        e: Throwable?,
        separator: String?,
    ): String {
        val ret = StringBuilder()
        var cause: Throwable? = e
        while (cause != null) {
            if (cause.localizedMessage != null || cause === e) {
                if (cause !== e) {
                    ret.append(separator)
                }
                ret.append(cause.localizedMessage)
            }
            cause = cause.cause
        }
        return ret.toString()
    }

    fun getFullStackTrace(ex: Throwable): String {
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    /** Executes a function, and logs the exception to ACRA and shows a toast if an issue occurs */
    fun executeSafe(
        context: Context,
        origin: String,
        runnable: (() -> Unit),
    ) {
        try {
            runnable.invoke()
        } catch (e: Exception) {
            CrashReportService.sendExceptionReport(e, origin)
            showThemedToast(
                context,
                context.getString(R.string.multimedia_editor_something_wrong),
                true,
            )
        }
    }
}
