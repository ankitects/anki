// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.common.utils.ext

import android.content.BroadcastReceiver
import android.content.Context
import timber.log.Timber

/**
 * Unregisters a [BroadcastReceiver] from [Context] without throwing an [IllegalArgumentException]
 * if the receiver wasn't registered.
 *
 * @param receiver [BroadcastReceiver] to unregister
 * @see Context.unregisterReceiver
 */
fun Context.unregisterReceiverSilently(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
        Timber.d(e, "BroadcastReceiver was not previously registered")
    }
}
