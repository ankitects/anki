// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import androidx.annotation.StringRes

/**
 * An enum representing the possible values of a Preference.
 */
interface PrefEnum {
    /** The resource ID of a constant that corresponds to the preference string value. */
    @get:StringRes
    val entryResId: Int
}
