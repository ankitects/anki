// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import android.content.Context
import android.content.SharedPreferences

private const val UI_CONFIG_PREFERENCES_NAME = "ui-config"

/**
 * The [SharedPreferences] used for UI configuration such as resizable layouts.
 *
 * This is a separate file from the default preferences read by [Prefs].
 */
fun Context.uiConfigPrefs(): SharedPreferences = getSharedPreferences(UI_CONFIG_PREFERENCES_NAME, Context.MODE_PRIVATE)
