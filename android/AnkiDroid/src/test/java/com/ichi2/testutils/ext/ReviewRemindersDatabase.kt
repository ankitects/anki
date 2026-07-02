/*
 *  Copyright (c) 2026 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.testutils.ext

import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderGroup
import com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific
import com.ichi2.anki.reviewreminders.ReviewReminderScope.Global
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase

fun ReviewRemindersDatabase.storeReminders(vararg reminders: ReviewReminder) {
    reminders.forEach { reminder ->
        when (reminder.scope) {
            is DeckSpecific -> {
                editRemindersForDeck(reminder.scope.did) { reminders ->
                    reminders + ReviewReminderGroup(reminder.id to reminder)
                }
            }
            is Global -> {
                editAllAppWideReminders { reminders ->
                    reminders + ReviewReminderGroup(reminder.id to reminder)
                }
            }
        }
    }
}
