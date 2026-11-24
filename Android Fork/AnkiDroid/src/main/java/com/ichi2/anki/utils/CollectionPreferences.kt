// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import androidx.annotation.VisibleForTesting
import anki.config.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.observability.undoableOp
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Helper methods related to preferences saved in the collection.
 *
 * @see [anki.config.Preferences]
 */
object CollectionPreferences {
    // region Reviewing
    suspend fun getShowRemainingDueCounts(): Boolean = withCol { getPreferences() }.reviewing.showRemainingDueCounts

    suspend fun setShowRemainingDueCounts(value: Boolean) {
        val prefs = withCol { getPreferences() }
        val newPrefs =
            prefs.copy {
                reviewing = reviewing.copy { showRemainingDueCounts = value }
            }
        undoableOp { setPreferences(newPrefs) }
        Timber.i("Set showRemainingDueCounts to %b", value)
    }

    suspend fun getHidePlayAudioButtons(): Boolean = withCol { getPreferences() }.reviewing.hideAudioPlayButtons

    suspend fun setHideAudioPlayButtons(value: Boolean) {
        val prefs = withCol { getPreferences() }
        val newPrefs =
            prefs.copy {
                reviewing = reviewing.copy { hideAudioPlayButtons = value }
            }
        undoableOp { setPreferences(newPrefs) }
        Timber.i("Set hideAudioPlayButtons to %b", value)
    }

    suspend fun getShowIntervalOnButtons(): Boolean = withCol { getPreferences() }.reviewing.showIntervalsOnButtons

    suspend fun setShowIntervalsOnButtons(value: Boolean) {
        val prefs = withCol { getPreferences() }
        val newPrefs =
            prefs.copy {
                reviewing = reviewing.copy { showIntervalsOnButtons = value }
            }
        undoableOp { setPreferences(newPrefs) }
        Timber.i("Set showIntervalsOnButtons to %b", value)
    }

    suspend fun getTimeboxTimeLimit(): Duration = withCol { getPreferences() }.reviewing.timeLimitSecs.toDuration(DurationUnit.SECONDS)

    suspend fun setTimeboxTimeLimit(limit: Duration) {
        val prefs = withCol { getPreferences() }
        val newPrefs =
            prefs.copy {
                reviewing = prefs.reviewing.copy { timeLimitSecs = limit.toInt(DurationUnit.SECONDS) }
            }
        undoableOp { setPreferences(newPrefs) }
        Timber.i("Set timeLimitSecs to %d", limit.toInt(DurationUnit.SECONDS))
    }
    //endregion

    //region Scheduling
    suspend fun getLearnAheadLimit(): Duration = withCol { getPreferences() }.scheduling.learnAheadSecs.toDuration(DurationUnit.SECONDS)

    suspend fun setLearnAheadLimit(limit: Duration) {
        val prefs = withCol { getPreferences() }
        val newPrefs =
            prefs.copy {
                scheduling = prefs.scheduling.copy { learnAheadSecs = limit.toInt(DurationUnit.SECONDS) }
            }

        undoableOp { setPreferences(newPrefs) }
        Timber.i("set learn ahead limit: '%d'", limit.toInt(DurationUnit.SECONDS))
    }

    @VisibleForTesting
    suspend fun getDayOffset(): Int = withCol { getPreferences() }.scheduling.rollover
    //endregion
}
