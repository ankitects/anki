// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase.StoredReviewReminderGroup
import kotlinx.serialization.SerialName
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
 * edited to return instances of the newly-created schema. Then, increment [ReviewRemindersDatabase.schemaVersion].
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
     * Transforms this [ReviewReminderSchema] to the next version of the [ReviewReminderSchema]. For example, if an old schema used `did`
     * to define its scope, but the new schema uses a scope data class, the field might have an override like the following:
     *
     * ```kotlin
     * scope = if (this.did == -1L) ReviewReminderScope.Global else ReviewReminderScope.DeckSpecific(this.did)
     * ```
     */
    fun migrate(): ReviewReminderSchema
}

/**
 * The Version 3 -> Version 4 migration fixed a bug where the cachedDeckName field of [ReviewReminderScope.DeckSpecific]
 * was being serialized and persisted to SharedPreferences. However, since this involves a change to an existing data class,
 * we must still maintain a namespaced version of the old data class so that the serializer can distinguish between the old and new data classes.
 * To ensure these classes interact with serialization like they did in old versions, the up-to-date SerialName is specified.
 */
object ReviewReminderPreV4Classes {
    @Serializable
    sealed class ReviewReminderScope {
        @Serializable
        @SerialName("com.ichi2.anki.reviewreminders.ReviewReminderScope.Global")
        data object Global : ReviewReminderScope()

        /**
         * This class will be serialized into "DeckSpecific(did=#, cachedDeckName=#)", and is nested because it will otherwise conflict
         * with the updated definition of [ReviewReminderScope.DeckSpecific], which is serialized as "DeckSpecific(did=#)".
         * When we read the outdated schema from the disk, we need to tell the deserializer that it is reading a
         * ReviewReminderPreV4Classes.DeckSpecific rather than a [ReviewReminderScope.DeckSpecific], even though the class names are the same.
         */
        @Serializable
        @SerialName("com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific")
        data class DeckSpecific(
            val did: DeckId,
        ) : ReviewReminderScope() {
            @Suppress("unused") // Only used for migration serialization.
            private var cachedDeckName: String? = null
        }
    }
}

/**
 * Version 1 of [ReviewReminderSchema]. Updated to Version 2 by adding [ReviewReminder.onlyNotifyIfNoReviews].
 */
@Serializable
data class ReviewReminderSchemaV1(
    override val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderPreV4Classes.ReviewReminderScope,
    var enabled: Boolean,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean = false,
) : ReviewReminderSchema {
    override fun migrate(): ReviewReminderSchemaV2 =
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
    val scope: ReviewReminderPreV4Classes.ReviewReminderScope,
    var enabled: Boolean,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean,
) : ReviewReminderSchema {
    override fun migrate(): ReviewReminderSchemaV3 =
        ReviewReminderSchemaV3(
            id = id,
            time = time,
            cardTriggerThreshold = cardTriggerThreshold,
            scope = scope,
            enabled = enabled,
            latestNotifTime = TimeManager.time.calendar().timeInMillis,
            profileID = profileID,
            onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
        )
}

/**
 * Version 3 of [ReviewReminderSchema]. Updated to Version 4 by fixing a bug where the [ReviewReminderScope.DeckSpecific.cachedDeckName]
 * field was persisted to SharedPreferences, which is not necessary and can cause issues if the deck name changes.
 */
@Serializable
data class ReviewReminderSchemaV3(
    override val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderPreV4Classes.ReviewReminderScope,
    var enabled: Boolean,
    var latestNotifTime: EpochMilliseconds,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean,
) : ReviewReminderSchema {
    override fun migrate(): ReviewReminderSchemaV4 =
        ReviewReminderSchemaV4(
            id = id,
            time = time,
            cardTriggerThreshold = cardTriggerThreshold,
            scope =
                when (scope) {
                    is ReviewReminderPreV4Classes.ReviewReminderScope.Global -> ReviewReminderScope.Global
                    is ReviewReminderPreV4Classes.ReviewReminderScope.DeckSpecific -> ReviewReminderScope.DeckSpecific(scope.did)
                },
            enabled = enabled,
            latestNotifTime = latestNotifTime,
            profileID = profileID,
            onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
        )
}

/**
 * Version 4 of [ReviewReminderSchema]. Updated to Version 5 by adding [ReviewReminder.thresholdFilter].
 */
@Serializable
data class ReviewReminderSchemaV4(
    override val id: ReviewReminderId,
    val time: ReviewReminderTime,
    val cardTriggerThreshold: ReviewReminderCardTriggerThreshold,
    val scope: ReviewReminderScope,
    var enabled: Boolean,
    var latestNotifTime: EpochMilliseconds,
    val profileID: String,
    val onlyNotifyIfNoReviews: Boolean,
) : ReviewReminderSchema {
    override fun migrate(): ReviewReminder = ReviewReminder.createViaMigration(this)
}
