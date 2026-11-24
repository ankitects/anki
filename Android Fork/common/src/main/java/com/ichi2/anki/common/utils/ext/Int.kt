// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

inline fun Int.ifNotZero(block: (value: Int) -> Unit) {
    if (this == 0) return
    block(this)
}

/** Sets or clears the bits in [flag], preserving all other bits. */
fun Int.setBitFlag(
    flag: Int,
    enabled: Boolean,
): Int = if (enabled) this or flag else this and flag.inv()
