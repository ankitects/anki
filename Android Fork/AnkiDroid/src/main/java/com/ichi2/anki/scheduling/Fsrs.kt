// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import net.ankiweb.rsdroid.BuildConfig as BackendBuildConfig

/**
 * Functionality for [FSRS](https://github.com/open-spaced-repetition/)
 */
object Fsrs {
    val version = FsrsVersion(BackendBuildConfig.FSRS_VERSION)

    /**
     * A user-facing string for the FSRS version.
     *
     * The underlying library version is not typically known to Anki users
     *
     * `null` is unexpected
     */
    val displayVersion: String?
        get() = version.displayString
}

@JvmInline
value class FsrsVersion(
    val libraryVersion: String,
) {
    val displayString: String? get() =
        when (libraryVersion) {
            "0.6.4" -> "FSRS 4.5"
            "1.4.3", "2.0.3" -> "FSRS 5"
            "4.1.1", "5.1.0", "5.2.0", "6.3.0", "6.6.2" -> "FSRS 6"
            else -> null
        }
}
