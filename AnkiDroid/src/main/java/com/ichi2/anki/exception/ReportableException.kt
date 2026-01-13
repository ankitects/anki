// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.exception

/**
 * Wrapper around an exception surfaced in a screen's state, with an extra flag about the
 * exception being reportable or not.
 */
data class ReportableException(
    val source: Throwable,
    /** true if this exception should be sent to [com.ichi2.anki.CrashReportService] */
    val isReportable: Boolean = true,
)
