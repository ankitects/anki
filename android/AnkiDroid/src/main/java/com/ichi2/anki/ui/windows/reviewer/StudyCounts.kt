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
