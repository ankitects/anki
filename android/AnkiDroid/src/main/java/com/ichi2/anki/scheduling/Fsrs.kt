/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
            "4.1.1", "5.1.0" -> "FSRS 6"
            else -> null
        }
}
