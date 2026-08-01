// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

/**
 * Returns the index of the first occurrence of the specified element in the list, or `null` if the
 * specified element is not contained in the list
 */
fun <T> List<T>.indexOfOrNull(element: T): Int? {
    val index = this.indexOf(element)
    return if (index >= 0) index else null
}

/**
 * Returns the index of the first occurrence of the matching element in the list, or `null` if the
 * specified element is not contained in the list
 */
fun <T> List<T>.indexOfOrNull(block: (T) -> Boolean): Int? {
    val index = this.indexOfFirst(block)
    return if (index >= 0) index else null
}
