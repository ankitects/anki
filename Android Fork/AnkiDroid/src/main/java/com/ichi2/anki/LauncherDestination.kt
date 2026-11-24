// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.LauncherDestination

/** Builds the [Intent] that opens AnkiDroid's launcher entry point. */
fun LauncherDestination.toIntent(context: Context): Intent =
    Intent(context, IntentHandler::class.java).apply {
        // matches the launcher <intent-filter>, so this behaves like tapping the app icon
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
