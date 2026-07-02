/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.reviewreminders

import android.content.Context
import android.content.Intent
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.Destination

class ScheduleRemindersDestination(
    private val did: DeckId,
) : Destination {
    override fun toIntent(context: Context): Intent =
        ScheduleRemindersFragment.getIntent(
            context,
            ReviewReminderScope.DeckSpecific(did),
        )
}
