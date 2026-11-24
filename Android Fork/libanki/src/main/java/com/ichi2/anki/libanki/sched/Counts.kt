// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.libanki.sched

import androidx.annotation.CheckResult

/**
 * Represents the three counts shown in deck picker and reviewer. Semantically more meaningful than int[]
 */
class Counts(
    var new: Int = 0,
    var lrn: Int = 0,
    var rev: Int = 0,
) {
    enum class Queue {
        NEW,
        LRN,
        REV,
    }

    fun addNew(new: Int) {
        this.new += new
    }

    fun addLrn(lrn: Int) {
        this.lrn += lrn
    }

    fun addRev(rev: Int) {
        this.rev += rev
    }

    /**
     * @return the sum of the three counts
     */
    @CheckResult
    fun count(): Int = new + lrn + rev

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val counts = other as Counts
        return new == counts.new && rev == counts.rev && lrn == counts.lrn
    }

    override fun hashCode(): Int = listOf(new, rev, lrn).hashCode()

    override fun toString(): String = "[$new, $lrn, $rev]"
}
