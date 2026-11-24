// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.annotation.SuppressLint
import android.content.Context

internal class SharedPreferencesAnrReportState(
    private val context: Context,
) {
    private val preferences get() = context.getSharedPreferences("historical_anrs", Context.MODE_PRIVATE)

    val lastProcessedTimestamp: Long
        get() = preferences.getLong(PREF_TIMESTAMP, 0L)

    @SuppressLint("UseKtx") // The commit result decides whether it is safe to report this exit.
    fun markProcessed(timestamp: Long): Boolean = preferences.edit().putLong(PREF_TIMESTAMP, timestamp).commit()
}

private const val PREF_TIMESTAMP = "checked_through"
