// SPDX-FileCopyrightText: Copyright (c) 2026 ColgateTotal77 <serega2005n@gmail.com>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

/*
 * [code] should be kept in sync with the [com.ichi2.anki.Flag] enum.
 *
 * param code The code of the flag.
 */
public enum class Flag(
    public val code: Int,
) {
    NONE(0),
    RED(1),
    ORANGE(2),
    GREEN(3),
    BLUE(4),
    PINK(5),
    TURQUOISE(6),
    PURPLE(7),
    ;

    public companion object {
        public val MAX_CODE: Int = entries.maxOf { it.code }
        public val MIN_CODE: Int = entries.minOf { it.code }
    }
}
