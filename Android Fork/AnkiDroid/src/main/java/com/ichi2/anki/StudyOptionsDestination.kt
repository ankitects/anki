// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.StudyOptionsDestination

/** Builds the [Intent] that opens the study options screen. */
fun StudyOptionsDestination.toIntent(context: Context): Intent = Intent(context, StudyOptionsActivity::class.java)
