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
import android.os.Parcelable
import android.text.format.DateFormat
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.settings.Prefs
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.util.Calendar
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@JvmInline
@Serializable
@Parcelize
value class ReviewReminderId(
    val value: Int,
) : Parcelable {
    companion object {
        /**
         * Get and return the next free reminder ID which can be associated with a new review reminder.
         * Also increment the next free reminder ID stored in SharedPreferences.
         * @return The next free reminder ID.
         */
        fun getAndIncrementNextFreeReminderId(): ReviewReminderId {
            val nextFreeId = Prefs.reviewReminderNextFreeId
            Prefs.reviewReminderNextFreeId = nextFreeId + 1
            Timber.d("Generated next free review reminder ID: %s", nextFreeId)
            return ReviewReminderId(nextFreeId)
        }
    }
}

/**
 * The time of day at which reminders will send a notification.
 */
@Serializable
@Parcelize
data class ReviewReminderTime(
    val hour: Int,
    val minute: Int,
) : Parcelable {
    init {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
    }

    /**
     * Formats the time as a string in the user's locale and 12/24-hour preference.
     */
    fun toFormattedString(context: Context): String {
        val calendarInstance =
            TimeManager.time.calendar().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
        return DateFormat.getTimeFormat(context).format(calendarInstance.time)
    }

    fun toSecondsFromMidnight(): Long = (hour.hours + minute.minutes).inWholeSeconds

    companion object {
        /**
         * Returns the current time as a [ReviewReminderTime].
         * Used as the default displayed time when creating a review reminder.
         */
        fun getCurrentTime(): ReviewReminderTime {
            val calendarInstance = TimeManager.time.calendar()
            val currentHour = calendarInstance.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendarInstance.get(Calendar.MINUTE)
            return ReviewReminderTime(currentHour, currentMinute)
        }
    }
}

/**
 * If, at the time of the reminder, less than this many cards are due, the notification is not triggered.
 */
@JvmInline
@Serializable
@Parcelize
value class ReviewReminderCardTriggerThreshold(
    val threshold: Int,
) : Parcelable {
    init {
        require(threshold >= 0) { "Card trigger threshold must be >= 0" }
    }
}

/**
 * An indicator of whether a review reminders feature is associated with every deck in the user's
 * collection or if it is associated with a single deck. For example, the [ScheduleRemindersFragment] fragment
 * can be triggered in either global or deck-specific editing mode. A [ReviewReminder] can be associated
 * with either all decks or a specific deck.
 *
 * This class is marked with @Parcelize so that it can be passed into [ScheduleRemindersFragment.getIntent].
 * This class is marked with @Serializable so that it can be a field of [ReviewReminder]s, which are stored as JSON strings.
 */
@Serializable
@Parcelize
sealed class ReviewReminderScope : Parcelable {
    /**
     * This represents all decks in the user's collection.
     */
    @Serializable
    data object Global : ReviewReminderScope()

    /**
     * This represents a specific deck in the user's collection.
     */
    @Serializable
    data class DeckSpecific(
        val did: DeckId,
    ) : ReviewReminderScope() {
        @IgnoredOnParcel
        private var cachedDeckName: String? = null

        /**
         * Gets the deck name associated with this [DeckSpecific] review reminder's [did] from the collection.
         * Caches the resultant deck name to minimize calls to the collection.
         * Should not be called if [did] is no longer a valid deck ID. If [did] is invalid, this method will return "[no deck]".
         */
        suspend fun getDeckName(): String {
            cachedDeckName?.let { return it }
            val retrievedDeckName = withCol { decks.name(did) }
            Timber.d("Retrieved deck name for review reminder: %s", retrievedDeckName)
            cachedDeckName = retrievedDeckName
            return retrievedDeckName
        }
    }
}

/**
 * A "review reminder" is a recurring scheduled notification that reminds the user
 * to review their Anki cards. Individual instances of a review reminder firing and showing up
 * on the user's phone are called "notifications".
 *
 * Below, a public way of creating review reminders is exposed via a companion object so that
 * reminders with invalid IDs are never created. This class is annotated
 * with @ConsistentCopyVisibility to ensure copy() is private too and does not leak the constructor.
 *
 * About the old schema migration process:
 *
 * To any developer who changes this class in the future, note that these review reminders are stored
 * by [ReviewRemindersDatabase] inside SharedPreferences. Modifying this schema means that existing
 * stored review reminders on user devices will no longer be able to be read, as decoding them to the new
 * [ReviewReminder] schema will cause a serialization exception.
 * You must specify a schema migration mapping for users who already have review reminders set on their devices
 * so that [ReviewRemindersDatabase.performSchemaMigration] can migrate their reminders to the new schema.
 * Use a [ReviewReminderSchema] to store the old schema and to define a method for migrating to the new schema.
 * Your method will be called from [ReviewRemindersDatabase.performSchemaMigration]. To inform [ReviewRemindersDatabase.performSchemaMigration]
 * that some users may have review reminders in the form of your old schema, add your [ReviewReminderSchema]
 * to [ReviewRemindersDatabase.oldReviewReminderSchemasForMigration] and update [ReviewRemindersDatabase.schemaVersion].
 * [ReviewRemindersDatabase.oldReviewReminderSchemasForMigration] should contain a chain of versions, from 1 -> 2 -> 3 -> ...,
 * and when a migration begins, it will happen step by step via the [ReviewReminderSchema.migrate] method, going from version 1 to version 2,
 * from version 2 to version 3, and so on, until [ReviewRemindersDatabase.schemaVersion] is reached.
 * Preferably, also add some unit tests to ensure your migration works properly on all user devices once your update is rolled out.
 * See ReviewRemindersDatabaseTest for examples on how to do this.
 *
 * @param id Unique, auto-incremented ID of the review reminder.
 * @param time See [ReviewReminderTime].
 * @param cardTriggerThreshold See [ReviewReminderCardTriggerThreshold].
 * @param scope See [ReviewReminderScope].
 * @param enabled Whether the review reminder's notifications are active or disabled.
 * @param profileID ID representing the profile which created this review reminder, as review reminders for
 * multiple profiles might be active simultaneously.
 * @param latestNotifTime The time at which this review reminder last attempted to fire a routine daily (non-snooze)
 * notification, in epoch milliseconds, or the time at which it was created if no notification has ever been fired.
 * See [shouldImmediatelyFire].
 * @param onlyNotifyIfNoReviews If true, only notify the user if this scope has not been reviewed today yet.
 */
@Serializable
@Parcelize
@ConsistentCopyVisibility
data class ReviewReminder private constructor(
    override val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderScope,
    var enabled: Boolean,
    var latestNotifTime: EpochMilliseconds,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean,
) : Parcelable,
    ReviewReminderSchema {
    companion object {
        /**
         * Create a new review reminder. This will allocate a new ID for the reminder.
         * @return A new [ReviewReminder] object.
         * @see [ReviewReminder]
         */
        fun createReviewReminder(
            time: ReviewReminderTime,
            cardTriggerThreshold: ReviewReminderCardTriggerThreshold = ReviewReminderCardTriggerThreshold(0),
            scope: ReviewReminderScope = ReviewReminderScope.Global,
            enabled: Boolean = true,
            profileID: String = "",
            onlyNotifyIfNoReviews: Boolean = false,
        ) = ReviewReminder(
            id = ReviewReminderId.getAndIncrementNextFreeReminderId(),
            time,
            cardTriggerThreshold,
            scope,
            enabled,
            latestNotifTime = TimeManager.time.calendar().timeInMillis,
            profileID,
            onlyNotifyIfNoReviews,
        )
    }

    /**
     * Updates [latestNotifTime] to the current time.
     * This should be called whenever this review reminder attempts to fire a routine daily (non-snooze) notification.
     */
    fun updateLatestNotifTime() {
        latestNotifTime = TimeManager.time.calendar().timeInMillis
    }

    /**
     * Checks if this review reminder has tried to fire a routine daily (non-snooze) notification in the time between
     * its latest scheduled firing time and now. If not, this method returns true, indicating that a notification
     * should be immediately fired for this review reminder.
     */
    fun shouldImmediatelyFire(): Boolean {
        val (hour, minute) = this.time

        val currentTimestamp = TimeManager.time.calendar()
        val latestScheduledTimestamp = currentTimestamp.clone() as Calendar
        latestScheduledTimestamp.apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (after(currentTimestamp)) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }

        return latestNotifTime < latestScheduledTimestamp.timeInMillis
    }

    /**
     * This is the up-to-date schema, we cannot migrate to a newer version.
     */
    override fun migrate(): ReviewReminder = this
}
