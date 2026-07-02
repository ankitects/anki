// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.utils.ext

import java.util.Collections

/**
 * Moves an item within a MutableList by swapping adjacent elements. This is particularly
 * optimized for [androidx.recyclerview.widget.ItemTouchHelper] drag-and-drop operations.
 */
fun <T> MutableList<T>.swapPositions(
    fromPosition: Int,
    toPosition: Int,
) {
    if (fromPosition < toPosition) {
        for (i in fromPosition until toPosition) {
            Collections.swap(this, i, i + 1)
        }
    } else {
        for (i in fromPosition downTo toPosition + 1) {
            Collections.swap(this, i, i - 1)
        }
    }
}
