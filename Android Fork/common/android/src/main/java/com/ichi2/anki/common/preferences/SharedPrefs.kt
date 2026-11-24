// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager

/** shorthand method to get the default [SharedPreferences] instance */
fun Context.sharedPrefs(): SharedPreferences =
    SharedPreferencesTestHook.override
        ?: PreferenceManager.getDefaultSharedPreferences(this)

/**
 * Optional override for unit tests that need isolated default preferences.
 *
 * Robolectric tests share one application [Context]; parallel tests can otherwise mutate the same
 * preference file. Tests set [override] and clear it in `@After`.
 */
object SharedPreferencesTestHook {
    @set:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var override: SharedPreferences? = null
}
