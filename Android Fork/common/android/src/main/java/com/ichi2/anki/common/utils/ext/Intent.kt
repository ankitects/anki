// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import android.content.Intent
import androidx.core.content.IntentCompat

fun Intent.getLongExtra(key: String): Long? {
    @Suppress("DEPRECATION") // get()
    val value = extras?.get(key) ?: return null
    return value as Long
}

/**
 * Identical behavior to [IntentCompat.getParcelableExtra] but simplifies call sites which are
 * verbose due to the formatter.
 *
 * @see IntentCompat.getParcelableExtra
 */
inline fun <reified T> Intent.getParcelableExtraCompat(key: String): T? = IntentCompat.getParcelableExtra(this, key, T::class.java)
