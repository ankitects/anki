// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import kotlin.reflect.KMutableProperty0

/**
 * Sets [pref] to [value] while [action] runs, then restores the previous value.
 *
 * ```kotlin
 * withPref(Prefs::devBottomNavEnabled, true) { ... }
 * ```
 */
fun <T> withPref(
    pref: KMutableProperty0<T>,
    value: T,
    action: () -> Unit,
) {
    val previousValue = pref.get()
    pref.set(value)
    try {
        action()
    } finally {
        pref.set(previousValue)
    }
}
