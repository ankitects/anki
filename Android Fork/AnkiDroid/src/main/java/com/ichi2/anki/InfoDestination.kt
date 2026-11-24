// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.ChangelogDestination

/** Builds the [Intent] that opens the changelog screen. */
fun ChangelogDestination.toIntent(context: Context): Intent = Intent(context, Info::class.java)
