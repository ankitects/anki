/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.android.withAppLocale

/**
 * A base class for all [BroadcastReceiver] instances in the app
 *
 * @see BroadcastReceiver
 */
abstract class AnkiBroadcastReceiver : BroadcastReceiver() {
    // #19048: On API < 33, BroadcastReceiver context uses the system locale.
    final override fun onReceive(
        rawContext: Context,
        intent: Intent,
    ) {
        val context = rawContext.withAppLocale()
        onReceiveBroadcast(context, intent)
    }

    /** @see onReceive */
    abstract fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    )
}
