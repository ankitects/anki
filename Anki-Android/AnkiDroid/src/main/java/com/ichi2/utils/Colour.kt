/*
 * Copyright (c) 2023 Arthur Milchior <arthur@milchior.fr>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import android.graphics.Color

/**
 * [this] is `@ColorInt`
 * Return RRGGBB in hexadecimal
 */
fun Int.toRGBHex() = String.format("#%06X", 0xFFFFFF and this)

/**
 * [this] is `@ColorInt`
 * Converts Android-style ARGB to Web-style RGBA
 */
fun Int.toRGBAHex(): String = String.format("#%02X%02X%02X%02X", Color.red(this), Color.green(this), Color.blue(this), Color.alpha(this))
