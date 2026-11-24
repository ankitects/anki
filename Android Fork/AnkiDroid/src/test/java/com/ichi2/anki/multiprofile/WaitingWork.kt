// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

class NeverRuns(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result = Result.success()
}

/** Queues work that waits on a constraint, as a sync does while offline. */
fun Context.enqueueWaiting(uniqueWorkName: String) {
    WorkManager
        .getInstance(this)
        .enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NeverRuns>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .build(),
        )
}

fun Context.workState(uniqueWorkName: String): WorkInfo.State? =
    WorkManager
        .getInstance(this)
        .getWorkInfosForUniqueWork(uniqueWorkName)
        .get()
        .singleOrNull()
        ?.state
