// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity

// TODO: move to testFixtures once RobolectricTest is moved

/** Launches [ScheduleRemindersFragment] in its standalone activity */
context(test: RobolectricTest)
fun withStandaloneScheduleReminders(block: (ConfigAwareSingleFragmentActivity) -> Unit) {
    val intent = ScheduleRemindersFragment.getIntent(test.targetContext, ReviewReminderScope.Global)
    ActivityScenario.launch<ConfigAwareSingleFragmentActivity>(intent).use { scenario ->
        advanceRobolectricLooper()
        scenario.onActivity { activity -> block(activity) }
    }
}

/** Launches [ScheduleRemindersFragment] in its own activity, exposing the fragment */
context(test: RobolectricTest)
fun withScheduleRemindersFragment(block: (ScheduleRemindersFragment) -> Unit) =
    withStandaloneScheduleReminders { activity -> block(activity.fragment as ScheduleRemindersFragment) }
