// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

/**
 * Represents an ANR recorded by Android during a previous app run.
 *
 * Uses the main-thread stack captured by Android during the ANR, or an empty stack if decoding fails.
 *
 * @param stackTrace Captured main-thread frames from the previous app run, or null if decoding failed.
 */
internal class AnrReportException(
    stackTrace: Array<StackTraceElement>?,
) : Exception(
        (if (stackTrace == null) "Previous ANR: unable to decode main-thread trace." else "Previous ANR.") +
            "\nOther report metadata describes this reporting launch.",
        // cause
        null,
        // enable suppression
        false,
        // a writable stack trace is required
        true,
    ) {
    init {
        this.stackTrace = stackTrace ?: emptyArray()
    }
}
