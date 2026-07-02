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

package com.ichi2.anki.reviewreminders

import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase.StoredReviewReminderGroup
import kotlinx.serialization.Serializable

/**
 * Inline value class for review reminder schema versions.
 * @see [StoredReviewReminderGroup]
 * @see [ReviewReminder]
 */
@JvmInline
@Serializable
value class ReviewReminderSchemaVersion(
    val value: Int,
) {
    init {
        require(value >= 1) { "Review reminder schema version must be >= 1" }
        // We do not check that it is <= SCHEMA_VERSION here because then declaring SCHEMA_VERSION would be circular
    }
}

/**
 * When [ReviewReminder] is updated by a developer, implement this interface in a new data class which
 * has the same fields as the old version of [ReviewReminder], then implement the [migrate] method which
 * transforms old [ReviewReminder]s to new [ReviewReminder]s. Also ensure that the previous [ReviewReminderSchema]
 * in the migration version chain ([ReviewRemindersDatabase.oldReviewReminderSchemasForMigration]) has its [migrate] method
 * edited to return instances of the newly-created [ReviewReminderSchema]. Then, increment [ReviewRemindersDatabase.schemaVersion].
 *
 * Data classes implementing this interface should be marked as @Serializable. Any new types defined for ReviewReminderSchemas
 * should also be marked as @Serializable.
 *
 * @see [ReviewRemindersDatabase.performSchemaMigration].
 * @see [ReviewReminder]
 */
@Serializable
sealed interface ReviewReminderSchema {
    /**
     * All review reminders must have an identifying ID.
     * This is necessary to facilitate migrations. See the implementation of [ReviewRemindersDatabase.performSchemaMigration] for details.
     */
    val id: ReviewReminderId

    /**
     * Transforms this [ReviewReminderSchema] to the next version of the [ReviewReminderSchema].
     */
    fun migrate(): ReviewReminderSchema
}

/**
 * Version 1 of [ReviewReminderSchema]. Updated to Version 2 by adding [ReviewReminder.onlyNotifyIfNoReviews].
 */
@Serializable
data class ReviewReminderSchemaV1(
    override val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderScope,
    var enabled: Boolean,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean = false,
) : ReviewReminderSchema {
    override fun migrate(): ReviewReminderSchema =
        ReviewReminderSchemaV2(
            id = id,
            time = time,
            cardTriggerThreshold = cardTriggerThreshold,
            scope = scope,
            enabled = enabled,
            profileID = profileID,
            onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
        )
}

/**
 * Version 2 of [ReviewReminderSchema]. Updated to Version 3 by adding [ReviewReminder.latestNotifTime].
 */
@Serializable
data class ReviewReminderSchemaV2(
    override val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderScope,
    var enabled: Boolean,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean,
) : ReviewReminderSchema {
    override fun migrate(): ReviewReminderSchema =
        ReviewReminder.createReviewReminder(
            time = time,
            cardTriggerThreshold = cardTriggerThreshold,
            scope = scope,
            enabled = enabled,
            profileID = profileID,
            onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
        )
}

/**
 * Schema migration settings for testing purposes.
 * Consult this as an example of how to save old schemas and define their [ReviewReminderSchema.migrate] methods.
 */
object TestingReviewReminderMigrationSettings {
    /**
     * A sample old review reminder schema. Perhaps this was how the [ReviewReminder] data class was originally implemented.
     * We would like to test the code that checks if review reminders stored on the device adhere to an old, outdated schema.
     * In particular, does the code correctly migrate the serialized data class strings to the updated, current version of [ReviewReminder]?
     */
    @Serializable
    data class ReviewReminderTestSchemaVersionOne(
        override val id: ReviewReminderId,
        val hour: Int,
        val minute: Int,
        val cardTriggerThreshold: Int,
        val did: DeckId,
        val enabled: Boolean = true,
    ) : ReviewReminderSchema {
        override fun migrate(): ReviewReminderSchema =
            ReviewReminderTestSchemaVersionTwo(
                id = this.id,
                time = VersionTwoDataClasses.ReviewReminderTime(hour, minute),
                snoozeAmount = 1,
                cardTriggerThreshold = this.cardTriggerThreshold,
                did = this.did,
                enabled = enabled,
            )
    }

    /**
     * Here's an example of how you can handle renamed fields in a data class stored as part of a [ReviewReminder].
     * Otherwise, there's a namespace collision with [ReviewReminderTime].
     *
     * This class will be serialized into "ReviewReminderTime(timeHour=#, timeMinute=#)", which otherwise might conflict
     * with the updated definition of [ReviewReminderTime], which is serialized as "ReviewReminderTime(hour=#, minute=#)".
     * When we read the outdated schema from the disk, we need to tell the deserializer that it is reading a
     * [VersionTwoDataClasses.ReviewReminderTime] rather than a [ReviewReminderTime], even though the names are the same.
     *
     * @see ReviewReminderTestSchemaVersionTwo
     */
    object VersionTwoDataClasses {
        @Serializable
        data class ReviewReminderTime(
            val timeHour: Int,
            val timeMinute: Int,
        )
    }

    /**
     * Another example of an old review reminder schema. See [ReviewReminderTestSchemaVersionOne] for more details.
     */
    @Serializable
    data class ReviewReminderTestSchemaVersionTwo(
        override val id: ReviewReminderId,
        val time: VersionTwoDataClasses.ReviewReminderTime,
        val snoozeAmount: Int,
        val cardTriggerThreshold: Int,
        val did: DeckId,
        val enabled: Boolean = true,
    ) : ReviewReminderSchema {
        override fun migrate(): ReviewReminder =
            ReviewReminder.createReviewReminder(
                time = ReviewReminderTime(this.time.timeHour, this.time.timeMinute),
                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(this.cardTriggerThreshold),
                scope = if (this.did == -1L) ReviewReminderScope.Global else ReviewReminderScope.DeckSpecific(this.did),
                enabled = enabled,
            )
    }
}
