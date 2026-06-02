// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.common.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** shorthand method to get the default [SharedPreferences] instance */
fun Context.sharedPrefs(): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
