// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Shaan Narendran <shaannaren06@gmail.com>

package com.ichi2.anki.utils.ext

import kotlin.Double

fun Double.wholeAndFraction(): Pair<Long, Double> {
    val whole = this.toLong()
    val fraction = this - whole
    return Pair(whole, fraction)
}
