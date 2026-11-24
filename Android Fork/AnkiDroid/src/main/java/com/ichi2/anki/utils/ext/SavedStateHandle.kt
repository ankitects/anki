// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.lifecycle.SavedStateHandle

fun <T> SavedStateHandle.require(key: String): T =
    checkNotNull(get(key)) {
        "Wrong type or key '$key' not found"
    }

/**
 * Retrieves the [Long] value for the provided [key], returns null if not found.
 *
 * @param key the key to use
 * @return the long value, or null if not found
 */
fun SavedStateHandle.getLongOrNull(key: String): Long? =
    if (contains(key)) {
        get(key) as Long?
    } else {
        null
    }
