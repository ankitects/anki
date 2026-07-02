/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
