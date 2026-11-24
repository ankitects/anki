// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
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

/**
 * Returns the system service of type [T], for services which are always available
 *
 * ```
 * val clipboardManager = context.requireSystemService<ClipboardManager>()
 * ```
 *
 * @throws IllegalArgumentException if the service is unavailable
 * @see ContextCompat.getSystemService
 */
inline fun <reified T : Any> Context.requireSystemService(): T =
    requireNotNull(ContextCompat.getSystemService(this, T::class.java)) {
        "system service unavailable: ${T::class.java.simpleName}"
    }

/** Loads a drawable using this context's theme and enables automatic mirroring in RTL layouts. */
fun Context.requireAutoMirroredDrawable(
    @DrawableRes id: Int,
): Drawable = requireNotNull(AppCompatResources.getDrawable(this, id)).apply { isAutoMirrored = true }

inline fun <reified T : Activity> Context.findActivity(): T? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<T>()
        .firstOrNull()
