// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

import com.ichi2.utils.ExceptionUtil.causesAndSuppressed
import java.util.IdentityHashMap

/**
 * Wraps an exception, stripping PII.
 */
internal class SanitizedThrowable private constructor(
    original: Throwable,
) : Throwable(original.javaClass.name) {
    init {
        stackTrace = original.stackTrace
    }

    // ACRA prints the throwable, so retain the original class name in its stack trace.
    override fun toString(): String = message!!

    companion object {
        /** Copy causes and suppressed exceptions, breaking cycles for downstream crash reporters. */
        fun from(throwable: Throwable): SanitizedThrowable {
            val copies = IdentityHashMap<Throwable, SanitizedThrowable>()
            // Build from leaves to root, linking only to completed copies to break cycles.
            for (original in throwable.causesAndSuppressed().toList().asReversed()) {
                val copy = SanitizedThrowable(original)
                copy.initCause(copies[original.cause])
                original.suppressed.mapNotNull { copies[it] }.forEach(copy::addSuppressed)
                copies[original] = copy
            }
            return copies.getValue(throwable)
        }
    }
}
