// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
