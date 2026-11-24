// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.analytics

import timber.log.Timber

/**
 * Records one analytics event and logs a stack trace without interrupting the existing fallback.
 * A received event proves execution; no events do not prove the path is unreachable.
 *
 * @param location a stable developer-defined identifier unique to the branch, containing no user data.
 */
fun reportPotentiallyDeadCode(location: String) {
    val exception = IllegalStateException("Potentially dead code executed: $location")
    Timber.w(exception)

    try {
        Analytics.send(AnalyticsEvent.PotentiallyDeadCode(location))
    } catch (e: Exception) {
        Timber.w(e, "Failed to report potentially dead code to analytics")
    }
}
