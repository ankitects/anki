// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import anki.config.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DayRolloverHandler
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.widget.DayRolloverAlarm
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Calendar

/**
 * Prevents day rollover from resetting the reviewer.
 *
 * Fixed by temporarily setting the rollover time to currentTime + 12 hours.
 *
 * ```kotlin
 * // This must have a lower `order` than `ActivityScenarioRule`
 * @get:Rule(order = 0)
 * val avoidDayRollover = AvoidDayRolloverRule()
 *
 * @get:Rule(order = 1)
 * val activityScenarioRule = ActivityScenarioRule(IntroductionActivity::class.java)
 * ```
 */
class AvoidDayRolloverRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val originalRollover = runBlocking { withCol { getPreferences().scheduling.rollover } }
                try {
                    val currentHour = TimeManager.time.calendar()[Calendar.HOUR_OF_DAY]
                    setRollover((currentHour + 12) % 24)
                    base.evaluate()
                } finally {
                    // The collection is shared with other tests, including after a failure.
                    setRollover(originalRollover)
                }
            }
        }

    private fun setRollover(hour: Int) =
        runBlocking {
            withCol {
                setPreferences(getPreferences().copy { scheduling = scheduling.copy { rollover = hour } })
            }
            // Consume the cutoff change before an activity can react to the next time event.
            DayRolloverHandler.handleTimeChange()
            // Finish replacing the alarm before entering the test or restoring normal operation.
            DayRolloverAlarm.scheduleNextInternal(appContext)
        }
}
