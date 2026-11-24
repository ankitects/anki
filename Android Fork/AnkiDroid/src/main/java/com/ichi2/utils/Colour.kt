// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 Arthur Milchior <arthur@milchior.fr>

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
