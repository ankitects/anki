/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
