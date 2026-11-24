// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.content.Context
import androidx.annotation.CheckResult
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.android.showThemedToast
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Collections
import java.util.IdentityHashMap

object ExceptionUtil {
    /** This throwable followed lazily by its causes. Cyclic chains repeat instances indefinitely. */
    internal val Throwable.causeChain: Sequence<Throwable>
        get() = generateSequence(this) { it.cause }

    /**
     * Produces a sequence of this throwable and all throwables reachable through
     * [Throwable.cause] or [Throwable.getSuppressed].
     */
    internal fun Throwable.causesAndSuppressed(): Sequence<Throwable> =
        sequence {
            val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            val pending = ArrayDeque<Throwable>()
            pending.add(this@causesAndSuppressed)
            while (pending.isNotEmpty()) {
                val throwable = pending.removeLast()
                if (!seen.add(throwable)) continue
                yield(throwable)
                // Visit causes first so shared suppressed references cannot truncate the cause chain.
                pending.addAll(throwable.suppressed)
                throwable.cause?.let { pending.add(it) }
            }
        }

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
                context.getString(CommonString.multimedia_editor_something_wrong),
                true,
            )
        }
    }
}
