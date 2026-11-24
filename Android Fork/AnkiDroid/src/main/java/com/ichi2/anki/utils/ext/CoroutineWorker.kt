// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import timber.log.Timber

/**
 * try-catch a [CoroutineWorker.setForeground] call, which may throw if the app isn't able
 * to run in the foreground at the point
 */
suspend fun CoroutineWorker.trySetForeground(foregroundInfo: ForegroundInfo) =
    try {
        setForeground(foregroundInfo)
    } catch (error: Throwable) {
        Timber.w(error)
    }
