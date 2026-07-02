/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils

import android.graphics.drawable.Drawable
import android.view.MenuItem

/**
 * Applies [Drawable.setAlpha] without affecting other drawables loaded from the same resource
 * Set-only property
 * @see Drawable.mutate
 */
@get:Deprecated("set-only property")
var MenuItem.iconAlpha: Int
    get() = this.icon?.alpha!!
    set(value) {
        this.icon?.let { it.mutate().alpha = value }
    }
