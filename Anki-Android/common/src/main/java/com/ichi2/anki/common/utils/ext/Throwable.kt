// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

/**
 * Walks the cause chain and returns the deepest non-null cause.
 *
 * Returns the receiver itself if it has no cause, and is safe against
 * self-referential cause cycles.
 */
fun Throwable.getRootCause(): Throwable {
    var result = this
    while (result.cause != null && result.cause !== result) {
        result = result.cause!!
    }
    return result
}
