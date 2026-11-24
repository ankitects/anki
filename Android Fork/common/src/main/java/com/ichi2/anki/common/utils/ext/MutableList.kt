// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

/**
 * Replaces the contents of the list with [items].
 *
 * @param items the new contents of the list
 */
fun <T> MutableList<T>.replaceWith(items: Collection<T>) {
    clear()
    addAll(items)
}
