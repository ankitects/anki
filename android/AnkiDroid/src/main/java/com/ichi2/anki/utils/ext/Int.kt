/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
 *
 *  This file incorporates code under the following license
 *
 *   Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *   Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 *
 *   https://github.com/JetBrains/kotlin/blob/6ea9f879abd61ba9578b322eb07ff6a8450de11f/libraries/stdlib/common/src/generated/_Ranges.kt#L1447
 */

package com.ichi2.anki.utils.ext

/**
 * Ensures the number is within the provided range.
 *
 * If too small, [minimumValue] is returned
 * If too large, [maximumValue] is returned
 *
 * Similar to [coerceIn], but does NOT validate that min < max for performance reasons
 */
fun Int.clamp(
    minimumValue: Int,
    maximumValue: Int,
): Int {
    if (this < minimumValue) return minimumValue
    if (this > maximumValue) return maximumValue
    return this
}
