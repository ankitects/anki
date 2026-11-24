// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.worker

import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber

/**
 * Runs [block] holding this lock. If a profile switch holds it, the work is dropped
 * rather than rescheduled, since it carries the AnkiWeb key of the profile being left.
 */
internal suspend fun Mutex.withLockUnlessSwitching(block: suspend () -> Result): Result {
    if (!tryLock()) {
        Timber.i("Background work dropped: a profile switch is restarting the app")
        return Result.failure()
    }
    try {
        return block()
    } finally {
        unlock()
    }
}
