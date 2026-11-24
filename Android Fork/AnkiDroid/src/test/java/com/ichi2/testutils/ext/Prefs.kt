// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.testutils.ext

import androidx.core.content.edit
import com.ichi2.anki.settings.Prefs

fun Prefs.clear() {
    sharedPrefs.edit { clear() }
}
