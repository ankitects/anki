// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.os.Parcelable
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.libanki.sched.CurrentQueueState
import kotlinx.parcelize.Parcelize

/**
 * Parcelable wrapper around [Counts] to be used in the study screen.
 */
@Parcelize
data class StudyCounts(
    private val newCount: Int = 0,
    private val learnCount: Int = 0,
    private val reviewCount: Int = 0,
    val activeQueue: Counts.Queue = Counts.Queue.NEW,
) : Parcelable {
    constructor(state: CurrentQueueState) : this(
        newCount = state.counts.new,
        learnCount = state.counts.lrn,
        reviewCount = state.counts.rev,
        activeQueue = state.countsIndex,
    )

    val new: String get() = newCount.toString()
    val learn: String get() = learnCount.toString()
    val review: String get() = reviewCount.toString()
}
