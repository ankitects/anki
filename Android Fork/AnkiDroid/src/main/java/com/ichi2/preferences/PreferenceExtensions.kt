// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.SharedPreferences
import androidx.annotation.CheckResult
import androidx.core.content.edit
import java.util.function.Supplier

/**
 * Returns the string value specified by the key, or sets key to the result of the lambda and returns it.
 *
 * This is not designed to be used when bulk editing preferences.
 *
 * Defect #5828 - This is potentially not thread safe and could cause another preference commit to fail.
 */
@CheckResult // A "set" API should be used if the result is not required.
fun SharedPreferences.getOrSetString(
    key: String,
    supplier: Supplier<String>,
): String {
    if (contains(key)) {
        // the default Is never returned. The value might be able be optimised, but the Android API should be better.
        return getString(key, "")!!
    }
    val supplied = supplier.get()
    this.edit { putString(key, supplied) }
    return supplied
}

@CheckResult // A "set" API should be used if the result is not required.
fun SharedPreferences.getOrSetLong(
    key: String,
    supplier: Supplier<Long>,
): Long {
    if (contains(key)) {
        // the default is never returned
        return getLong(key, -1337L)
    }
    val supplied = supplier.get()
    edit { putLong(key, supplied) }
    return supplied
}
