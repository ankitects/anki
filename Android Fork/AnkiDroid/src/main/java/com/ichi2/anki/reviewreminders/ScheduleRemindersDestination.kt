// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

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
