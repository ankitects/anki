/*
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import anki.collection.Progress
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol

fun DeckPicker.handleDatabaseCheck() {
    fun Progress.DatabaseCheck.toAmount() =
        if (stageTotal > 0) {
            ProgressContext.Amount(stageCurrent.toLong(), stageTotal.toLong())
        } else {
            null
        }

    launchCatchingTask {
        val problems =
            withProgress(
                extractProgress = {
                    if (progress.hasDatabaseCheck()) {
                        progress.databaseCheck.let {
                            text = it.stage
                            amount = it.toAmount()
                        }
                    }
                },
                onCancel = null,
            ) {
                withCol {
                    fixIntegrity()
                }
            }
        val message =
            if (problems.isNotEmpty()) {
                problems.joinToString("\n")
            } else {
                TR.databaseCheckRebuilt()
            }
        showSimpleMessageDialog(message)
    }
}
