// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.PreferencesDestination

/** Builds the [Intent] that opens settings at this destination. */
fun PreferencesDestination.toIntent(context: Context): Intent =
    when (this) {
        PreferencesDestination.Root -> PreferencesActivity.getIntent(context)
        PreferencesDestination.Advanced ->
            PreferencesActivity.getIntent(context, AdvancedSettingsFragment::class)
        PreferencesDestination.Accessibility ->
            PreferencesActivity.getIntent(context, AccessibilitySettingsFragment::class)
    }
