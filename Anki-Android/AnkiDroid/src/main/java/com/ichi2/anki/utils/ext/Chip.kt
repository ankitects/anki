/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
