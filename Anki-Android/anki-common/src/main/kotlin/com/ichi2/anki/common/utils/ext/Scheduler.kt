// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.common.utils.ext

import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.libanki.sched.Scheduler

/**
 * @return Number of new, rev and lrn card to review in all decks.
 */
fun Scheduler.allDecksCounts(): Counts {
    val total = Counts()
    // Only count the top-level decks in the total
    val nodes = deckDueTree().children
    for (node in nodes) {
        total.addNew(node.newCount)
        total.addLrn(node.lrnCount)
        total.addRev(node.revCount)
    }
    return total
}
