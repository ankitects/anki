// SPDX-License-Identifier: GPL-3.0-or-later

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
