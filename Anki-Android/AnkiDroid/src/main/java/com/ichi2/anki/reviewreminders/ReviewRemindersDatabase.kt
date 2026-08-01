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
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.ellipsize
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.showError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import timber.log.Timber
import kotlin.reflect.KClass

/**
 * Manages the storage and retrieval of [ReviewReminder]s in SharedPreferences.
 *
 * [ReviewReminder]s can either be tied to a specific deck and trigger based on the number of cards
 * due in that deck, or they can be app-wide reminders that trigger based on the total number
 * of cards due across all decks. See [ReviewReminderScope].
 */
object ReviewRemindersDatabase {
    /**
     * Profile ID for the review reminder SharedPreferences file key. Each profile for using AnkiDroid will have its own review reminders stored
     * in its own SharedPreferences file. This ID is appended onto the end of [SHARED_PREFS_FILE_KEY] to create a unique file name for each profile.
     *
     * Currently, this is hard-coded as 0. When multi-profile functionality is added to AnkiDroid, make sure this value is dynamically set
     * to the current profile ID. Also ensure that the entire review reminders system is updated to work with the multi-profile system.
     * For example, scheduled notifications may need to be cancelled and rescheduled when the user toggles between profiles.
     */
    private const val PROFILE_ID: Int = 0

    /**
     * SharedPreferences file name key for review reminders. We store the review reminders separately from the default SharedPreferences.
     */
    private const val SHARED_PREFS_FILE_KEY = "com.ichi2.anki.REVIEW_REMINDERS_SHARED_PREFS_$PROFILE_ID"

    /**
     * SharedPreferences file for review reminders. We store the review reminders separately from the default SharedPreferences.
     */
    @VisibleForTesting
    val remindersSharedPrefs: SharedPreferences =
        appContext.getSharedPreferences(
            SHARED_PREFS_FILE_KEY,
            Context.MODE_PRIVATE,
        )

    /**
     * Key in SharedPreferences for retrieving deck-specific reminders.
     * Should have deck ID appended to its end, ex. "deck_12345".
     * Its value is a [ReviewReminderGroup] serialized as a JSON String.
     */
    @VisibleForTesting
    const val DECK_SPECIFIC_KEY = "deck_"

    /**
     * Key in SharedPreferences for retrieving app-wide reminders.
     * Its value is a [ReviewReminderGroup] serialized as a JSON String.
     */
    @VisibleForTesting
    const val APP_WIDE_KEY = "app_wide"

    /**
     * The form in which [ReviewReminderGroup] are actually written to SharedPreferences.
     * This allows us to check the version of [ReviewReminder] stored before trying to deserialize the JSON string,
     * allowing us to carefully handle schema migration. Otherwise, if an older version of [ReviewReminder] is encoded
     * and we try to decode it into a newer form of [ReviewReminder], an error will be thrown.
     *
     * We assume that the version is accurate; e.x. if the version is 3, then the [ReviewReminder] stored is indeed
     * of schema version 3. This should be safe to assume since writing this data class to SharedPreferences is an
     * atomic operation.
     *
     * @param version The version of the [remindersMapJson] which is stored.
     * @param remindersMapJson The stored [ReviewReminderGroup]'s underlying hashmap as a serialized JSON string.
     */
    @Serializable
    @VisibleForTesting
    data class StoredReviewReminderGroup(
        val version: ReviewReminderSchemaVersion,
        val remindersMapJson: String,
    )

    /**
     * Current [ReviewReminder] schema version. Whenever [ReviewReminder] is modified, this integer MUST be incremented.
     *
     * - Version 1: [ReviewReminderSchemaV1]: 3 August 2025 -  Initial version
     * - Version 2: [ReviewReminderSchemaV2]: 25 January 2026 - Added [ReviewReminder.onlyNotifyIfNoReviews]
     * - Version 3: [ReviewReminder]: 8 February 2026 - Added [ReviewReminder.latestNotifTime]
     *
     * @see [oldReviewReminderSchemasForMigration]
     * @see [ReviewReminder]
     */
    @VisibleForTesting
    var schemaVersion = ReviewReminderSchemaVersion(3)

    /**
     * A map of all old [ReviewReminderSchema]s that [ReviewRemindersDatabase.performSchemaMigration] will attempt to migrate old
     * review reminders in SharedPreferences from. Migration occurs from version 1 to version 2, from version 2 to version 3, etc.
     *
     * When [ReviewReminder] is updated, you MUST add a new migration version and keep the old schema
     * as a class that implements [ReviewReminderSchema]. Ensure the latest schema version in this map
     * always maps to [ReviewReminder].
     *
     * @see [schemaVersion]
     * @see [ReviewReminderSchema]
     * @see [ReviewReminder]
     */
    @VisibleForTesting
    var oldReviewReminderSchemasForMigration: Map<ReviewReminderSchemaVersion, KClass<out ReviewReminderSchema>> =
        mapOf(
            ReviewReminderSchemaVersion(1) to ReviewReminderSchemaV1::class,
            ReviewReminderSchemaVersion(2) to ReviewReminderSchemaV2::class,
            ReviewReminderSchemaVersion(3) to ReviewReminder::class, // Most up to date version
        )

    /**
     * Mutex to ensure reads and writes do not cause race conditions.
     * Should gate all public read and write interface functions in this class.
     */
    private val mutex: Mutex = Mutex()

    /**
     * Schema update method for migrating old review reminders to new ones.
     * This is run when [ReviewReminder] is updated and existing users who already have review reminders set up on their devices
     * need to have their data ported to the new schema.
     * Versions are declared in [oldReviewReminderSchemasForMigration].
     *
     * We need to opt into an experimental serialization API feature because we are determining classes to deserialize
     * dynamically via [oldReviewReminderSchemasForMigration] rather than at compile-time.
     * The possible schemas to deserialize from are inputted dynamically so that unit tests are possible.
     *
     * @param encodedReviewReminderKey The key with which the [encodedReviewReminderGroup] is stored in SharedPreferences,
     * used for writing the migrated map back into SharedPreferences.
     * @param encodedReviewReminderGroup The encoded review reminders map to migrate.
     * @param fromVersion The schema version of [encodedReviewReminderGroup].
     * @param toVersion The schema version of the new review reminders map.
     *
     * @throws SerializationException If the [fromVersion] is less than 1 or greater than [schemaVersion], or if the
     * [encodedReviewReminderGroup] is not a valid JSON string, or if the final result of migration is somehow not a [ReviewReminder].
     * @throws IllegalArgumentException If the [encodedReviewReminderGroup] is not actually of version [fromVersion],
     * or if the [fromVersion] is not in [oldReviewReminderSchemasForMigration].
     *
     * @see [ReviewReminder]
     */
    @OptIn(InternalSerializationApi::class)
    private fun performSchemaMigration(
        encodedReviewReminderKey: String,
        encodedReviewReminderGroup: String,
        fromVersion: ReviewReminderSchemaVersion,
        toVersion: ReviewReminderSchemaVersion = schemaVersion,
    ): ReviewReminderGroup {
        Timber.i("Beginning migration from $fromVersion to $toVersion")
        if (fromVersion.value < 1 ||
            fromVersion.value > toVersion.value
        ) {
            throw SerializationException("Invalid review reminder schema version: $fromVersion")
        }

        // Deserialize from old schema
        val oldSchema =
            oldReviewReminderSchemasForMigration[fromVersion]
                ?: throw IllegalArgumentException("Review reminder schema version not found: $fromVersion")
        val mapDeserializer = MapSerializer(ReviewReminderId.serializer(), oldSchema.serializer())
        val mapDecoded = Json.decodeFromString(mapDeserializer, encodedReviewReminderGroup)

        // Migrate step by step
        var currentMap = mapDecoded
        var currentVersion = fromVersion.value
        while (currentVersion < toVersion.value) {
            Timber.i("Migrating from schema version $currentVersion to ${currentVersion + 1}")
            currentMap =
                currentMap
                    .map { (_, value) ->
                        val newValue: ReviewReminderSchema = value.migrate()
                        newValue.id to newValue
                    }.toMap()
            currentVersion++
        }

        // Write to SharedPreferences, then return deserialized map
        val finalGroup =
            ReviewReminderGroup(
                currentMap.mapValues { (_, value) ->
                    value as? ReviewReminder ?: throw SerializationException("Expected ReviewReminder, got ${value::class.qualifiedName}")
                },
            )
        remindersSharedPrefs.edit {
            putString(encodedReviewReminderKey, encodeJson(finalGroup))
        }
        return finalGroup
    }

    /**
     * Decode an encoded [ReviewReminderGroup] JSON string which has been stored as a [StoredReviewReminderGroup].
     * Deletes the stored review reminders and returns an empty [ReviewReminderGroup] if deserialization fails
     * and no valid schema migrations exist.
     *
     * @see Json.decodeFromString
     */
    private fun decodeJson(
        jsonString: String,
        jsonStringKey: String,
    ): ReviewReminderGroup {
        Timber.v("Decoding review reminders JSON string: $jsonString")
        try {
            val storedReviewReminderGroup = Json.decodeFromString<StoredReviewReminderGroup>(jsonString)
            return if (storedReviewReminderGroup.version.value != schemaVersion.value) {
                performSchemaMigration(
                    jsonStringKey,
                    storedReviewReminderGroup.remindersMapJson,
                    storedReviewReminderGroup.version,
                    schemaVersion,
                )
            } else {
                ReviewReminderGroup(storedReviewReminderGroup.remindersMapJson)
            }
        } catch (e: Exception) {
            when (e) {
                is SerializationException,
                is IllegalArgumentException,
                -> {
                    // Log error, it will be displayed to the user either immediately if the app is open or when they next open the app if not
                    val errorString = "Encountered (${e.message}) while parsing $jsonString"
                    Prefs.reviewReminderDeserializationErrors = Prefs.reviewReminderDeserializationErrors.orEmpty() + "[$errorString]"
                    Timber.e(e, errorString)
                    CrashReportService.sendExceptionReport(
                        e,
                        origin = "ReviewRemindersDatabase:decodeJson",
                        additionalInfo = jsonString,
                    )

                    // Delete corrupted value, then return an empty group
                    remindersSharedPrefs.edit { remove(jsonStringKey) }
                    return ReviewReminderGroup()
                }
                else -> throw e
            }
        }
    }

    /**
     * Encode a [ReviewReminderGroup] as a [StoredReviewReminderGroup] JSON string.
     *
     * @see Json.encodeToString
     */
    private fun encodeJson(reminders: ReviewReminderGroup): String =
        Json.encodeToString(StoredReviewReminderGroup.serializer(), StoredReviewReminderGroup(schemaVersion, reminders.serializeToString()))

    /**
     * Get the [ReviewReminder]s for a specific key.
     * Deletes the stored review reminders and returns an empty [ReviewReminderGroup] if deserialization fails
     * and no valid schema migrations exist.
     *
     * @see decodeJson
     */
    private fun getRemindersForKey(key: String): ReviewReminderGroup {
        val jsonString = remindersSharedPrefs.getString(key, null) ?: return ReviewReminderGroup()
        return decodeJson(jsonString, jsonStringKey = key)
    }

    /**
     * Get the [ReviewReminder]s for a specific [ReviewReminderScope].
     * Deletes the stored review reminders and returns an empty [ReviewReminderGroup] if deserialization fails
     * and no valid schema migrations exist.
     */
    suspend fun getRemindersForScope(scope: ReviewReminderScope): ReviewReminderGroup =
        mutex.withLock {
            when (scope) {
                is ReviewReminderScope.DeckSpecific -> getRemindersForKey(DECK_SPECIFIC_KEY + scope.did)
                is ReviewReminderScope.Global -> getRemindersForKey(APP_WIDE_KEY)
            }
        }

    /**
     * Get all [ReviewReminder]s, including both [ReviewReminderScope.DeckSpecific] ones and [ReviewReminderScope.Global] ones.
     * If the stored review reminders for any [ReviewReminderScope] fail to deserialize and no valid schema migrations exist,
     * those reminders are deleted and not included in the resulting [ReviewReminderGroup].
     */
    suspend fun getAllReminders(): ReviewReminderGroup =
        mutex.withLock {
            remindersSharedPrefs
                .all
                .map { (key, value) -> decodeJson(value.toString(), jsonStringKey = key) }
                .mergeAll()
        }

    /**
     * Edit the [ReviewReminder]s for a specific key.
     * Deletes the [ReviewReminderGroup] for this key if, after the editing operation, no review reminders remain.
     * If the stored review reminders for any specific scope fail to deserialize and no valid schema migrations exist,
     * those reminders are deleted and not included in the resulting [ReviewReminderGroup].
     *
     * @param key
     * @param editor A lambda that takes the current [ReviewReminderGroup] and returns the updated [ReviewReminderGroup].
     *
     * @see getRemindersForKey
     * @see ReviewReminderGroupEditor
     */
    private fun editRemindersForKey(
        key: String,
        editor: ReviewReminderGroupEditor,
    ) {
        val existingReminders = getRemindersForKey(key)
        val updatedReminders = editor(existingReminders)
        remindersSharedPrefs.edit {
            if (updatedReminders.isEmpty()) {
                remove(key)
            } else {
                putString(key, encodeJson(updatedReminders))
            }
        }
    }

    /**
     * Edit the [ReviewReminder]s in a [ReviewReminderGroup] at a specific [ReviewReminderScope] using a [ReviewReminderGroupEditor].
     * Deletes the [ReviewReminderGroup] for this scope if, after the editing operation, no review reminders remain.
     * If the stored review reminders for any specific scope fail to deserialize and no valid schema migrations exist,
     * those reminders are deleted and not included in the resulting [ReviewReminderGroup].
     *
     * @param scope
     * @param editor A lambda which defines how reminders are retrieved from the [ReviewReminderGroup] they are stored in and how they are modified.
     *
     * @see ReviewReminderGroupEditor
     */
    private fun editRemindersForScope(
        scope: ReviewReminderScope,
        editor: ReviewReminderGroupEditor,
    ) {
        when (scope) {
            is ReviewReminderScope.DeckSpecific -> editRemindersForKey(DECK_SPECIFIC_KEY + scope.did, editor)
            is ReviewReminderScope.Global -> editRemindersForKey(APP_WIDE_KEY, editor)
        }
    }

    /**
     * Toggles whether a [ReviewReminder] is enabled.
     */
    suspend fun toggleReminder(reminder: ReviewReminder) =
        mutex.withLock {
            editRemindersForScope(reminder.scope) { reminders: ReviewReminderGroup ->
                reminders.apply { toggleEnabled(reminder.id) }
            }
        }

    /**
     * Inserts the given [ReviewReminder] into the reminder's [ReviewReminderScope].
     *
     * Important: Do not use this method for editing review reminders, and in particular
     * do not use this method for changing the [ReviewReminderScope] of a reminder, as review reminders of
     * different scopes are stored separately and cannot be cleanly updated in a single operation. The old
     * review reminder must be deleted first, or else a duplicate [ReviewReminderId] will be introduced.
     * In general, when you want to edit a [ReviewReminder], use [deleteReminder] first, then [insertReminder].
     * This method is intended to be lightweight and hence will not go out of its way to validate that an
     * update has not been performed.
     */
    suspend fun insertReminder(reminder: ReviewReminder) =
        mutex.withLock {
            editRemindersForScope(reminder.scope) { reminders: ReviewReminderGroup ->
                reminders.apply { this[reminder.id] = reminder }
            }
        }

    /**
     * Deletes a [ReviewReminder].
     */
    suspend fun deleteReminder(reminder: ReviewReminder) =
        mutex.withLock {
            editRemindersForScope(reminder.scope) { reminders: ReviewReminderGroup ->
                reminders.apply { remove(reminder.id) }
            }
        }

    /**
     * Given the ID of a [ReviewReminder] which is about to fire a recurring notification, atomically retrieves the latest up-to-date version
     * of that reminder from the database and performs validation and bookkeeping on it before returning it to the caller.
     * This is done in the database's scope so that we can re-use the [mutex] and hence be safe from
     * race conditions during the consecutive read and write.
     *
     * @param id The ID of the reminder which is about to fire a notification.
     * @param scope The scope that the review reminder ID is stored within.
     *
     * @return The retrieved reminder with its [ReviewReminder.latestNotifTime] updated, or null
     * if the notification has already been delivered or does not exist in the database.
     */
    suspend fun retrieveRefreshedReminder(
        id: ReviewReminderId,
        scope: ReviewReminderScope,
    ): ReviewReminder? =
        mutex.withLock {
            var reminderToReturn: ReviewReminder? = null
            editRemindersForScope(scope) { reminders: ReviewReminderGroup ->
                reminders.apply {
                    val storedReminder = this[id]
                    if (storedReminder == null) {
                        // The reminder should always be present as recurring notification alarms are unscheduled when
                        // a reminder is deleted, so this should never happen, but we fail gracefully just in case
                        Timber.e(
                            "Returning null for retrieveRefreshedReminder for reminder $id because it was not found in the database.",
                        )
                        return@apply
                    }

                    if (storedReminder.latestNotifDelivered()) {
                        // Do not proceed if the notification has already been delivered
                        Timber.i(
                            "Returning null for retrieveRefreshedReminder for reminder $id: " +
                                "Latest already delivered at ${storedReminder.latestNotifTime}",
                        )
                        return@apply
                    }

                    // Update and save this latest routine notification-firing attempt's timestamp
                    storedReminder.updateLatestNotifTime()
                    this[id] = storedReminder
                    reminderToReturn = storedReminder
                }
            }
            return reminderToReturn
        }

    /**
     * Shows an error message dialog if a review reminder deserialization error has recently happened.
     * Checks if a deserialization error has recently occurred by checking if anything is present in
     * [Prefs.reviewReminderDeserializationErrors], emptying the preference after reading it.
     *
     * @param context A valid themed context (ie. not applicationContext) to display the error dialog in.
     */
    fun checkDeserializationErrors(context: Context) {
        Prefs.reviewReminderDeserializationErrors?.let { errorString ->
            if (errorString.isEmpty()) return

            context.showError(
                message =
                    "An error occurred while loading your review reminders, corrupted reminders have been deleted. " +
                        "Details:\n\n${errorString.ellipsize(1000)}",
                crashReportData = null, // Crash reports are sent when the error is first encountered
            )
            Prefs.reviewReminderDeserializationErrors = ""
        }
    }
}
