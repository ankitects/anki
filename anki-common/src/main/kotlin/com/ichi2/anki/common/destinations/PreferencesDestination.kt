// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

/** Opens AnkiDroid's settings. */
sealed class PreferencesDestination : Destination() {
    /** Opens settings at the top level (header list / general). */
    data object Root : PreferencesDestination()

    /** Opens the Advanced settings screen. */
    data object Advanced : PreferencesDestination()

    /** Opens the Accessibility settings screen. */
    data object Accessibility : PreferencesDestination()
}
