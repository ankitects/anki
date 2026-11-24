// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.common.utils.ext

/**
 * The index of the first newline at or after [fromIndex], or null if there is none.
 */
fun ByteArray.indexOfNewlineAtOrAfter(fromIndex: Int): Int? {
    val newline = '\n'.code.toByte()
    for (index in fromIndex.coerceAtLeast(0) until size) {
        if (this[index] == newline) return index
    }
    return null
}
