/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
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
