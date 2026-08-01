/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences

import android.content.Context
import androidx.annotation.VisibleForTesting
import anki.config.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.services.BootService.Companion.scheduleNotification
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.preferences.NumberRangePreferenceCompat
import com.ichi2.preferences.SliderPreference
import com.ichi2.widget.DayRolloverAlarm
import timber.log.Timber
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class ReviewingSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_reviewing
    override val analyticsScreenNameConstant: String
        get() = "prefs.reviewing"

    override fun initSubscreen() {
        // Learn ahead limit
        // Represents the collections pref "collapseTime": i.e.
        // if there are no card to review now, but there are learning cards remaining for today, we show those learning cards if they are due before LEARN_CUTOFF minutes
        // Note that "collapseTime" is in second while LEARN_CUTOFF is in minute.
        @NeedsTest("#16645, changing the learn ahead limit shows expected cards in review queue")
        requirePreference<NumberRangePreferenceCompat>(R.string.learn_cutoff_preference).apply {
            launchCatchingTask { setValue(CollectionPreferences.getLearnAheadLimit().toInt(DurationUnit.MINUTES)) }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setLearnAheadLimit(newValue.toDuration(DurationUnit.MINUTES)) }
            }
        }
        // Timebox time limit
        // Represents in Android preferences the collections configuration "timeLim": i.e.
        // the duration of a review timebox in minute. Each TIME_LIMIT minutes, a message appear suggesting to halt and giving the number of card reviewed
        // Note that "timeLim" is in seconds while TIME_LIMIT is in minutes.
        requirePreference<NumberRangePreferenceCompat>(R.string.time_limit_preference).apply {
            launchCatchingTask { setValue(CollectionPreferences.getTimeboxTimeLimit().toInt(DurationUnit.MINUTES)) }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { CollectionPreferences.setTimeboxTimeLimit(newValue.toDuration(DurationUnit.MINUTES)) }
            }
        }
        // Start of next day
        // Represents the collection pref "rollover"
        // in sched v2, and crt in sched v1. I.e. at which time of the day does the scheduler reset
        requirePreference<SliderPreference>(R.string.day_offset_preference).apply {
            launchCatchingTask { value = CollectionPreferences.getDayOffset() }
            setOnPreferenceChangeListener { newValue ->
                launchCatchingTask { setDayOffset(requireContext(), newValue) }
            }
        }
    }
}

/** Sets the hour that the collection rolls over to the next day  */
@VisibleForTesting
@NeedsTest("ensure Start of Next Day is handled by the scheduler")
suspend fun setDayOffset(
    context: Context,
    hours: Int,
) {
    val prefs = withCol { getPreferences() }
    val newPrefs = prefs.copy { scheduling = prefs.scheduling.copy { rollover = hours } }

    undoableOp {
        setPreferences(newPrefs)
    }
    if (!Prefs.newReviewRemindersEnabled) scheduleNotification(TimeManager.time, context)
    DayRolloverAlarm.scheduleNext(context)
    Timber.i("set day offset: '%d'", hours)
}
