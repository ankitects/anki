// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Snowiee <xenonnn4w@gmail.com>

package com.ichi2.anki.utils

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import com.ichi2.anki.common.crashreporting.runCatchingWithReport
import timber.log.Timber

/**
 * Wrapper around [ShortcutManagerCompat] to handle platform-specific issues.
 */
object ShortcutUtils {
    /**
     * Batch size for shortcut operations to avoid [android.os.TransactionTooLargeException].
     * Android's Binder IPC has a 1MB transaction limit.
     */
    private const val BATCH_SIZE = 100

    /**
     * Disables shortcuts by their IDs, handling large lists by batching to avoid
     * [android.os.TransactionTooLargeException].
     *
     * @param context The context to use
     * @param shortcutIds List of shortcut IDs to disable
     * @param disabledMessage Message shown when user tries to use a disabled shortcut
     */
    fun disableShortcuts(
        context: Context,
        shortcutIds: List<String>,
        disabledMessage: CharSequence,
    ) {
        if (shortcutIds.isEmpty()) return

        shortcutIds.chunked(BATCH_SIZE).forEach { batch ->
            runCatchingWithReport(
                "Failed to disable shortcuts batch",
            ) { ShortcutManagerCompat.disableShortcuts(context, batch, disabledMessage) }
                .onFailure { e -> Timber.w(e, "Failed to disable shortcuts batch") }
        }
    }
}
