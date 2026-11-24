// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import com.google.android.material.R.attr.colorSecondaryContainer
import com.google.android.material.chip.Chip

/**
 * Sets the 'filled/unfilled' background of a chip to show whether it's toggled on
 *
 * Like [Chip.isChecked], for a chip with [Chip.isCheckable] set to `false` so user taps do not
 * modify the visuals state
 *
 * The color is set to [colorSecondaryContainer]
 */
var Chip.hasCheckedBackground: Boolean
    get() = this.isChecked
    set(value) {
        this.isCheckable = true
        this.isChecked = value
        this.isCheckable = false
    }
