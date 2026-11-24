// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.settings.Prefs
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.sameInstance
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.time.Duration.Companion.days

/**
 * If tests in this file have failed, it may be because you have updated [ReviewReminder]!
 * Please read the documentation of [ReviewReminder] carefully and ensure you have implemented
 * a proper migration method to the new schema. See the past schema migrations for examples.
 */
@RunWith(AndroidJUnit4::class)
class ReviewRemindersDatabaseTest : RobolectricTest() {
    companion object {
        private val yesterday = MockTime(TimeManager.time.intTimeMS() - 1.days.inWholeMilliseconds)
        private val today = MockTime(TimeManager.time.intTimeMS())
    }

    private val did1 = 12345L
    private val did2 = 67890L
    private val scope1 = ReviewReminderScope.DeckSpecific(did1)
    private val scope2 = ReviewReminderScope.DeckSpecific(did2)
    private val appScope = ReviewReminderScope.Global

    private val emptyReminderGroup = ReviewReminderGroup()
    private val reviewReminderOne by lazy {
        ReviewReminder.createReviewReminder(
            ReviewReminderTime(9, 0),
            ReviewReminderCardTriggerThreshold(5),
            scope1,
            false,
        )
    }
    private val reviewReminderTwo by lazy {
        ReviewReminder.createReviewReminder(
            ReviewReminderTime(10, 30),
            ReviewReminderCardTriggerThreshold(10),
            scope1,
        )
    }
    private val reviewReminderThree by lazy {
        ReviewReminder.createReviewReminder(
            ReviewReminderTime(10, 30),
            ReviewReminderCardTriggerThreshold(10),
            scope2,
            true,
        )
    }
    private val reviewReminderFour by lazy {
        ReviewReminder.createReviewReminder(
            ReviewReminderTime(12, 30),
            ReviewReminderCardTriggerThreshold(20),
            scope2,
        )
    }
    private val reviewReminderFive by lazy {
        ReviewReminder.createReviewReminder(
            ReviewReminderTime(9, 0),
            ReviewReminderCardTriggerThreshold(5),
        )
    }
    private val reviewReminderSix by lazy {
        ReviewReminder.createReviewReminder(
            ReviewReminderTime(10, 30),
            ReviewReminderCardTriggerThreshold(10),
        )
    }
    private val allTestReminders by lazy {
        listOf(
            reviewReminderOne,
            reviewReminderTwo,
            reviewReminderThree,
            reviewReminderFour,
            reviewReminderFive,
            reviewReminderSix,
        )
    }

    @Before
    override fun setUp() {
        super.setUp()
        TimeManager.resetWith(today)
        clearRemindersState()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        TimeManager.reset()
        clearRemindersState()
    }

    private fun clearRemindersState() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        Prefs.reviewReminderDeserializationErrors = ""
    }

    @Test
    fun `getRemindersForScope should return empty group when no reminders exist`() =
        runTest {
            val deckSpecificReminders = ReviewRemindersDatabase.getRemindersForScope(scope1)
            val appWideReminders = ReviewRemindersDatabase.getRemindersForScope(appScope)
            assertThat(deckSpecificReminders, equalTo(emptyReminderGroup))
            assertThat(appWideReminders, equalTo(emptyReminderGroup))
        }

    @Test
    fun `getAllReminders should return empty group when no reminders exist`() =
        runTest {
            val reminders = ReviewRemindersDatabase.getAllReminders()
            assertThat(reminders, equalTo(emptyReminderGroup))
        }

    @Test
    fun `insertReminder and getRemindersForScope should read and write reminders correctly`() =
        runTest {
            allTestReminders.forEach { reminder -> ReviewRemindersDatabase.insertReminder(reminder) }
            val remindersForDeckOne = ReviewRemindersDatabase.getRemindersForScope(scope1)
            val remindersForDeckTwo = ReviewRemindersDatabase.getRemindersForScope(scope2)
            val appWideReminders = ReviewRemindersDatabase.getRemindersForScope(ReviewReminderScope.Global)
            assertThat(
                remindersForDeckOne.getRemindersList().toSet(),
                equalTo(setOf(reviewReminderOne, reviewReminderTwo)),
            )
            assertThat(
                remindersForDeckTwo.getRemindersList().toSet(),
                equalTo(setOf(reviewReminderThree, reviewReminderFour)),
            )
            assertThat(
                appWideReminders.getRemindersList().toSet(),
                equalTo(setOf(reviewReminderFive, reviewReminderSix)),
            )
        }

    @Test
    fun `insertReminder and getAllReminders should read and write reminders correctly`() =
        runTest {
            allTestReminders.forEach { reminder -> ReviewRemindersDatabase.insertReminder(reminder) }
            val allReminders = ReviewRemindersDatabase.getAllReminders()
            assertThat(
                allReminders.getRemindersList().toSet(),
                equalTo(allTestReminders.toSet()),
            )
        }

    @Test
    fun `toggleReminder should toggle the enabled state of the correct reminder`() =
        runTest {
            allTestReminders.forEach { reminder -> ReviewRemindersDatabase.insertReminder(reminder) }
            ReviewRemindersDatabase.toggleReminder(reviewReminderOne)
            ReviewRemindersDatabase.toggleReminder(reviewReminderFive)
            val allReminders = ReviewRemindersDatabase.getAllReminders()

            val expectedFlippedReminders = setOf(reviewReminderOne, reviewReminderFive)
            allTestReminders.forEach { reminderBefore ->
                val reminderAfter = allReminders[reminderBefore.id]!!
                val shouldFlip = reminderBefore in expectedFlippedReminders
                assertThat(
                    reminderAfter.enabled,
                    equalTo(if (shouldFlip) !reminderBefore.enabled else reminderBefore.enabled),
                )
            }
        }

    @Test
    fun `deleteReminder should delete the correct reminders`() =
        runTest {
            allTestReminders.forEach { reminder -> ReviewRemindersDatabase.insertReminder(reminder) }
            ReviewRemindersDatabase.deleteReminder(reviewReminderTwo)
            ReviewRemindersDatabase.deleteReminder(reviewReminderSix)
            val remindersForDeckOne = ReviewRemindersDatabase.getRemindersForScope(scope1)
            val remindersForDeckTwo = ReviewRemindersDatabase.getRemindersForScope(scope2)
            val allReminders = ReviewRemindersDatabase.getAllReminders()
            assertThat(
                remindersForDeckOne.getRemindersList().toSet(),
                equalTo(setOf(reviewReminderOne)),
            )
            assertThat(
                remindersForDeckTwo.getRemindersList().toSet(),
                equalTo(setOf(reviewReminderThree, reviewReminderFour)),
            )
            assertThat(
                allReminders.getRemindersList().toSet(),
                equalTo(setOf(reviewReminderOne, reviewReminderThree, reviewReminderFour, reviewReminderFive)),
            )
        }

    @Test
    fun `retrieveRefreshedReminder with latest notif not delivered should edit and return updated reminder`() =
        runTest {
            TimeManager.resetWith(yesterday)
            val reviewReminder =
                ReviewReminder.createReviewReminder(
                    time = ReviewReminderTime(9, 0),
                    scope = scope1,
                    cardTriggerThreshold = ReviewReminderCardTriggerThreshold(5),
                )
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            val returnedReminder = ReviewRemindersDatabase.retrieveRefreshedReminder(reviewReminder.id, reviewReminder.scope)

            assertThat(returnedReminder, notNullValue())
            assertThat(returnedReminder!!.latestNotifTime, not(equalTo(creationTime)))
            val storedReminder = ReviewRemindersDatabase.getRemindersForScope(scope1)[reviewReminder.id]!!
            assertThat(returnedReminder, equalTo(storedReminder))
            TimeManager.reset()
        }

    @Test
    fun `retrieveRefreshedReminder with latest notif delivered should return null and not edit reminder`() =
        runTest {
            val reviewReminder =
                ReviewReminder.createReviewReminder(
                    time = ReviewReminderTime(9, 0),
                    scope = scope1,
                    cardTriggerThreshold = ReviewReminderCardTriggerThreshold(5),
                )
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            val returnedReminder = ReviewRemindersDatabase.retrieveRefreshedReminder(reviewReminder.id, reviewReminder.scope)

            assertThat(returnedReminder, nullValue())
            val storedReminder = ReviewRemindersDatabase.getRemindersForScope(scope1)[reviewReminder.id]!!
            assertThat(storedReminder, equalTo(reviewReminder))
        }

    @Test
    fun `retrieveRefreshedReminder with review reminder not found in database should fail gracefully`() =
        runTest {
            val returnedReminder = ReviewRemindersDatabase.retrieveRefreshedReminder(reviewReminderOne.id, reviewReminderOne.scope)
            assertThat(returnedReminder, nullValue())
        }

    /**
     * Helper function to test how the database handles corrupted JSON strings.
     * It should delete only the accessed ones, not throw an exception, and return an empty reminder group.
     *
     * @param corruptedValue the corrupted JSON string to be inserted into SharedPreferences for did1, did2, and the app-wide key
     * @param expectedDeletedKeys the set of keys that should no longer be in SharedPreferences after the access
     * @param access a lambda that accesses either deck-specific or app-wide reminders, which should trigger the deletion of the corrupted keys
     */
    private suspend fun corruptedRemindersTest(
        corruptedValue: String,
        expectedDeletedKeys: Set<String>,
        inputKeys: Set<String> =
            setOf(
                ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did1,
                ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did2,
                ReviewRemindersDatabase.PREF_APP_WIDE_REMINDERS,
            ),
        access: suspend () -> ReviewReminderGroup,
    ) {
        ReviewRemindersDatabase.remindersSharedPrefs.edit {
            inputKeys.forEach { key ->
                putString(key, corruptedValue)
            }
        }
        val reminders = access()
        assertThat(reminders, equalTo(emptyReminderGroup))
        val remainingKeys = ReviewRemindersDatabase.remindersSharedPrefs.all.keys
        assertThat(remainingKeys + expectedDeletedKeys, equalTo(inputKeys))
    }

    @Test
    fun `getRemindersForScope should delete reminders if JSON string for StoredReviewReminderGroup is corrupted`() =
        runTest {
            corruptedRemindersTest(
                corruptedValue = "corrupted_and_invalid_json_string",
                expectedDeletedKeys = setOf(ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did1),
            ) {
                ReviewRemindersDatabase.getRemindersForScope(scope1)
            }
        }

    @Test
    fun `getRemindersForScope should delete reminders if JSON string is not a StoredReviewReminderGroup`() =
        runTest {
            corruptedRemindersTest(
                corruptedValue = Json.encodeToString(Pair("not a group of", "review reminders")),
                expectedDeletedKeys = setOf(ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did2),
            ) {
                ReviewRemindersDatabase.getRemindersForScope(scope2)
            }
        }

    @Test
    fun `getRemindersForScope should delete reminders if JSON string for review reminder is corrupted`() =
        runTest {
            corruptedRemindersTest(
                corruptedValue =
                    Json.encodeToString(
                        ReviewRemindersDatabase.StoredReviewReminderGroup(
                            ReviewRemindersDatabase.schemaVersion,
                            "corrupted_and_invalid_json_string",
                        ),
                    ),
                expectedDeletedKeys = setOf(ReviewRemindersDatabase.PREF_APP_WIDE_REMINDERS),
            ) {
                ReviewRemindersDatabase.getRemindersForScope(appScope)
            }
        }

    @Test
    fun `getAllReminders should delete reminders if JSON string for StoredReviewReminderGroup is corrupted`() =
        runTest {
            corruptedRemindersTest(
                corruptedValue = "corrupted_and_invalid_json_string",
                expectedDeletedKeys =
                    setOf(
                        ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did1,
                        ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did2,
                        ReviewRemindersDatabase.PREF_APP_WIDE_REMINDERS,
                    ),
            ) {
                ReviewRemindersDatabase.getAllReminders()
            }
        }

    @Test
    fun `getAllReminders should delete reminders if JSON string is not a StoredReviewReminderGroup`() =
        runTest {
            corruptedRemindersTest(
                corruptedValue = Json.encodeToString(Pair("not a group of", "review reminders")),
                expectedDeletedKeys =
                    setOf(
                        ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did1,
                        ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did2,
                        ReviewRemindersDatabase.PREF_APP_WIDE_REMINDERS,
                    ),
            ) {
                ReviewRemindersDatabase.getAllReminders()
            }
        }

    @Test
    fun `getAllReminders should delete reminders if JSON string for review reminder is corrupted`() =
        runTest {
            corruptedRemindersTest(
                corruptedValue =
                    Json.encodeToString(
                        ReviewRemindersDatabase.StoredReviewReminderGroup(
                            ReviewRemindersDatabase.schemaVersion,
                            "corrupted_and_invalid_json_string",
                        ),
                    ),
                expectedDeletedKeys =
                    setOf(
                        ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did1,
                        ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did2,
                        ReviewRemindersDatabase.PREF_APP_WIDE_REMINDERS,
                    ),
            ) {
                ReviewRemindersDatabase.getAllReminders()
            }
        }

    @Test
    fun `deleteReminder should delete SharedPreferences key if no reminders are returned`() =
        runTest {
            ReviewRemindersDatabase.insertReminder(reviewReminderOne)
            ReviewRemindersDatabase.insertReminder(reviewReminderTwo)
            ReviewRemindersDatabase.deleteReminder(reviewReminderOne)
            ReviewRemindersDatabase.deleteReminder(reviewReminderTwo)
            val attemptedRetrieval = ReviewRemindersDatabase.getRemindersForScope(scope1)
            assertThat(attemptedRetrieval, equalTo(emptyReminderGroup))
            assertThat(
                ReviewRemindersDatabase.remindersSharedPrefs.all.keys,
                not(hasItem(ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did1)),
            )
        }

    @Test
    fun `scope cachedDeckName is not persisted`() =
        runTest {
            val did = addDeck("Original")
            val scope = ReviewReminderScope.DeckSpecific(did)
            scope.getDeckName() // fill cache
            ReviewRemindersDatabase.insertReminder(
                ReviewReminder.createReviewReminder(
                    time = ReviewReminderTime(9, 0),
                    scope = scope,
                ),
            )

            // Preference should not contain cachedDeckName
            val rawPref =
                ReviewRemindersDatabase.remindersSharedPrefs.getString(
                    ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did,
                    null,
                )!!
            assertThat(rawPref, not(containsString("cachedDeckName")))

            // Should not persist after retrieval
            col.decks.rename(col.decks.get(did)!!, "Renamed")
            val retrievedReminder = ReviewRemindersDatabase.getRemindersForScope(scope)
            val retrievedScope = retrievedReminder.getRemindersList().single().scope as ReviewReminderScope.DeckSpecific
            assertThat(retrievedScope.getDeckName(), equalTo("Renamed"))
        }

    /**
     * If this test has failed, please ensure the review reminder schema version and old schemas in the review reminder
     * migration chain are set correctly. If you've written a new migration, please also write a new test in this file
     * to prove your migration works!
     *
     * This test is designed to fail and be updated every time the schema is changed
     * to ensure developers know what they are doing and to remind them to write migration tests.
     */
    @Test
    fun `current schema version points to ReviewReminder`() {
        assertThat(ReviewRemindersDatabase.schemaVersion.value, equalTo(5))
        assertThat(
            ReviewRemindersDatabase
                .oldReviewReminderSchemasForMigration
                .keys
                .last()
                .value,
            equalTo(5),
        )
        assertThat(
            ReviewRemindersDatabase
                .oldReviewReminderSchemasForMigration
                .values
                .last(),
            equalTo(ReviewReminder::class),
        )
    }

    /**
     * If this test has failed, a [ReviewReminderSchema.migrate] method does not return the next schema
     * in [ReviewRemindersDatabase.oldReviewReminderSchemasForMigration]. Each schema must migrate to exactly
     * the next version, and must declare that version as the narrowed return type of its [ReviewReminderSchema.migrate].
     * When adding a new schema, remember to repoint the previous schema's [ReviewReminderSchema.migrate] to it.
     */
    @Test
    fun `each schema migrates to the next schema in the chain`() {
        val chain = ReviewRemindersDatabase.oldReviewReminderSchemasForMigration
        assertThat(
            chain.keys.map { it.value },
            equalTo((1..ReviewRemindersDatabase.schemaVersion.value).toList()),
        )
        chain.values.zipWithNext().forEach { (schema, nextSchema) ->
            val migrateReturnType =
                schema.declaredMemberFunctions
                    .single { it.name == "migrate" }
                    .returnType
                    .classifier
            assertThat(
                "${schema.simpleName}.migrate() should return ${nextSchema.simpleName}",
                migrateReturnType,
                equalTo(nextSchema),
            )
        }
    }

    /**
     * [ReviewReminder] is the latest schema, so its [ReviewReminder.migrate] should declare [ReviewReminder]
     * as its return type and return the same instance unchanged.
     */
    @Test
    fun `ReviewReminder migrates to itself`() {
        val migrateReturnType =
            ReviewReminder::class
                .declaredMemberFunctions
                .single { it.name == "migrate" }
                .returnType
                .classifier
        assertThat(migrateReturnType, equalTo(ReviewReminder::class))
        assertThat(reviewReminderOne.migrate(), sameInstance(reviewReminderOne))
    }

    /**
     * If this test has failed, you have likely updated [ReviewReminder] without writing a migration!
     * Please write a migration (see [ReviewReminder] and the tests in this file for more information),
     * update the latest schema version (see [ReviewRemindersDatabase]), add a new test in this file
     * to prove your migration works, and update this test to the new latest schema version.
     *
     * To get a raw string, add a log to [ReviewRemindersDatabase.decodeJson] to print out its input string.
     *
     * This test is designed to fail and be updated every time the schema is changed
     * to ensure developers know what they are doing and to remind them to write migration tests.
     */
    @Test
    fun `raw ReviewReminder string can be deserialized without throwing`() {
        @Language("JSON")
        val remindersMapJson =
            """
            {
              "24": {
                "id": 24,
                "time": { "hour": 23, "minute": 38 },
                "cardTriggerThreshold": 1,
                "scope": {
                  "type": "com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific",
                  "did": 1788806653166
                },
                "enabled": false,
                "latestNotifTime": 1790927620897,
                "profileID": "",
                "onlyNotifyIfNoReviews": false,
                "thresholdFilter": {}
              }
            }
            """.trimIndent()

        val rawString =
            buildJsonObject {
                put("version", 5)
                put("remindersMapJson", remindersMapJson)
            }.toString()

        val storedReviewReminderGroup = Json.decodeFromString<ReviewRemindersDatabase.StoredReviewReminderGroup>(rawString)
        assertThat(storedReviewReminderGroup.version, equalTo(ReviewRemindersDatabase.schemaVersion))
        val mapSerializer = MapSerializer(ReviewReminderId.serializer(), ReviewReminder.serializer())
        Json.decodeFromString(mapSerializer, storedReviewReminderGroup.remindersMapJson)
    }

    /**
     * If this test has failed, you have likely updated [ReviewReminder]'s schema! Please ensure you've written
     * a migration for this schema change (see [ReviewReminder]) and write a test in this file to prove your migration works.
     *
     * This test is designed to fail and be updated every time the schema is changed
     * to ensure developers know what they are doing and to remind them to write migration tests.
     */
    @Test
    fun `ReviewReminder properties and types are the expected values`() {
        val expectedPropertiesWithTypes =
            mapOf(
                "id" to ReviewReminderId::class,
                "time" to ReviewReminderTime::class,
                "cardTriggerThreshold" to ReviewReminderCardTriggerThreshold::class,
                "scope" to ReviewReminderScope::class,
                "enabled" to Boolean::class,
                "latestNotifTime" to EpochMilliseconds::class,
                "profileID" to String::class,
                "onlyNotifyIfNoReviews" to Boolean::class,
                "thresholdFilter" to ReviewReminderThresholdFilter::class,
            )

        val actualPropertiesWithTypes =
            ReviewReminder::class
                .memberProperties
                .associate { it.name to it.returnType.classifier }

        assertThat(actualPropertiesWithTypes, equalTo(expectedPropertiesWithTypes))
    }

    /**
     * A single test case for migration testing.
     *
     * @param inputVersion the version of the schema that the input JSON string represents.
     * @param inputJson the JSON string representing a [ReviewReminder] in the exact format that it
     * was persisted in SharedPreferences for the given [inputVersion].
     * @param expectedOutput the expected [ReviewReminder] object after migration to the latest schema version.
     *
     * @see assertMigrationsWork
     */
    private data class MigrationTestCase(
        val inputVersion: ReviewReminderSchemaVersion,
        val inputJson: String,
        val expectedOutput: ReviewReminder,
    )

    private suspend fun assertMigrationsWork(vararg testCases: MigrationTestCase) {
        // Group
        val groupedByScope =
            testCases.groupBy {
                when (val scope = it.expectedOutput.scope) {
                    is ReviewReminderScope.DeckSpecific -> scope.did
                    is ReviewReminderScope.Global -> null
                }
            }

        // Write
        groupedByScope.forEach { (did, casesInScope) ->
            // Reading and writing is done per scope, so all test cases in a scope will have the same input version
            if (casesInScope.map { it.inputVersion }.toSet().size != 1) {
                throw IllegalArgumentException("All test cases in a scope must have the same input version")
            }
            val version = casesInScope.first().inputVersion

            val inputJsonForScope =
                buildString {
                    append("{")
                    casesInScope.forEachIndexed { index, testCase ->
                        val inputId =
                            Json
                                .parseToJsonElement(testCase.inputJson)
                                .jsonObject
                                .getValue("id")
                                .jsonPrimitive.content
                        append("\"$inputId\":${testCase.inputJson}")
                        if (index < casesInScope.size - 1) {
                            append(",")
                        }
                    }
                    append("}")
                }
            val packagedInput =
                ReviewRemindersDatabase.StoredReviewReminderGroup(
                    version,
                    remindersMapJson = inputJsonForScope,
                )

            val key =
                if (did != null) {
                    ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did
                } else {
                    ReviewRemindersDatabase.PREF_APP_WIDE_REMINDERS
                }
            ReviewRemindersDatabase.remindersSharedPrefs.edit(commit = true) {
                putString(key, Json.encodeToString(packagedInput))
            }
        }

        // Read and assert
        groupedByScope.forEach { (did, casesInScope) ->
            val retrievedReminders =
                if (did != null) {
                    ReviewRemindersDatabase.getRemindersForScope(ReviewReminderScope.DeckSpecific(did))
                } else {
                    ReviewRemindersDatabase.getRemindersForScope(ReviewReminderScope.Global)
                }

            retrievedReminders.forEach { (id, reminder) ->
                assertThat(id, equalTo(reminder.id))
            }
            assertThat(
                retrievedReminders.getRemindersList().toSet(),
                equalTo(
                    casesInScope.map { it.expectedOutput }.toSet(),
                ),
            )
        }

        // Shared Preferences should not contain any random corrupted keys after or due to the migration process
        assertThat(
            ReviewRemindersDatabase.remindersSharedPrefs.all.size,
            equalTo(groupedByScope.size),
        )
        assertThat(Prefs.reviewReminderDeserializationErrors, equalTo(""))
    }

    @Test
    fun `review reminder v1 to v2 migration works`() =
        runTest {
            assertMigrationsWork(
                MigrationTestCase(
                    inputVersion = ReviewReminderSchemaVersion(1),
                    inputJson =
                        """
                        {"id":0,"time":{"hour":9,"minute":0},"cardTriggerThreshold":5,"scope":{"type":"com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific","did":$did1},"enabled":true,"profileID":""}
                        """.trimIndent(),
                    expectedOutput =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(9, 0),
                            cardTriggerThreshold = ReviewReminderCardTriggerThreshold(5),
                            scope = scope1,
                            enabled = true,
                            profileID = "",
                            onlyNotifyIfNoReviews = false,
                        ),
                ),
                MigrationTestCase(
                    inputVersion = ReviewReminderSchemaVersion(1),
                    inputJson =
                        """
                        {"id":1,"time":{"hour":10,"minute":30},"cardTriggerThreshold":10,"scope":{"type":"com.ichi2.anki.reviewreminders.ReviewReminderScope.Global"},"enabled":false,"profileID":"","onlyNotifyIfNoReviews":true}
                        """.trimIndent(),
                    expectedOutput =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(10, 30),
                            cardTriggerThreshold = ReviewReminderCardTriggerThreshold(10),
                            scope = ReviewReminderScope.Global,
                            enabled = false,
                            profileID = "",
                            onlyNotifyIfNoReviews = true,
                        ),
                ),
            )
        }

    @Test
    fun `review reminder v2 to v3 migration works`() =
        runTest {
            assertMigrationsWork(
                MigrationTestCase(
                    inputVersion = ReviewReminderSchemaVersion(2),
                    inputJson =
                        """
                        {"id":0,"time":{"hour":10,"minute":30},"cardTriggerThreshold":10,"scope":{"type":"com.ichi2.anki.reviewreminders.ReviewReminderScope.Global"},"enabled":true,"profileID":"","onlyNotifyIfNoReviews":false}
                        """.trimIndent(),
                    expectedOutput =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(10, 30),
                            cardTriggerThreshold = ReviewReminderCardTriggerThreshold(10),
                            scope = ReviewReminderScope.Global,
                            enabled = true,
                            profileID = "",
                            onlyNotifyIfNoReviews = false,
                        ),
                ),
                MigrationTestCase(
                    inputVersion = ReviewReminderSchemaVersion(2),
                    inputJson =
                        """
                        {"id":1,"time":{"hour":12,"minute":0},"cardTriggerThreshold":20,"scope":{"type":"com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific","did":$did2},"enabled":false,"profileID":"","onlyNotifyIfNoReviews":true}
                        """.trimIndent(),
                    expectedOutput =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(12, 0),
                            cardTriggerThreshold = ReviewReminderCardTriggerThreshold(20),
                            scope = scope2,
                            enabled = false,
                            profileID = "",
                            onlyNotifyIfNoReviews = true,
                        ),
                ),
            )
        }

    @Test
    fun `review reminder v3 to v4 migration works`() =
        runTest {
            assertMigrationsWork(
                MigrationTestCase(
                    inputVersion = ReviewReminderSchemaVersion(3),
                    inputJson =
                        """
                        {"id":0,"time":{"hour":9,"minute":15},"cardTriggerThreshold":3,"scope":{"type":"com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific","did":$did1,"cachedDeckName":"Old Name"},"enabled":true,"latestNotifTime":1771193761002,"profileID":"","onlyNotifyIfNoReviews":true}
                        """.trimIndent(),
                    expectedOutput =
                        ReviewReminder
                            .createReviewReminder(
                                time = ReviewReminderTime(9, 15),
                                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(3),
                                scope = scope1,
                                enabled = true,
                                profileID = "",
                                onlyNotifyIfNoReviews = true,
                            ).apply { latestNotifTime = 1771193761002 },
                ),
                MigrationTestCase(
                    inputVersion = ReviewReminderSchemaVersion(3),
                    inputJson =
                        """
                        {"id":1,"time":{"hour":21,"minute":0},"cardTriggerThreshold":0,"scope":{"type":"com.ichi2.anki.reviewreminders.ReviewReminderScope.Global"},"enabled":false,"latestNotifTime":1771193762000,"profileID":"","onlyNotifyIfNoReviews":false}
                        """.trimIndent(),
                    expectedOutput =
                        ReviewReminder
                            .createReviewReminder(
                                time = ReviewReminderTime(21, 0),
                                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(0),
                                scope = ReviewReminderScope.Global,
                                enabled = false,
                                profileID = "",
                                onlyNotifyIfNoReviews = false,
                            ).apply { latestNotifTime = 1771193762000 },
                ),
            )

            // Ensure the private cached deck name is gone
            val rewrittenDeckSpecific =
                Json.decodeFromString<ReviewRemindersDatabase.StoredReviewReminderGroup>(
                    ReviewRemindersDatabase.remindersSharedPrefs.getString(
                        ReviewRemindersDatabase.PREF_DECK_SPECIFIC_REMINDERS + did1,
                        null,
                    )!!,
                )
            assertThat(rewrittenDeckSpecific.version, equalTo(ReviewRemindersDatabase.schemaVersion))
            assertThat(rewrittenDeckSpecific.remindersMapJson, not(containsString("cachedDeckName")))
        }

    @Test
    fun `review reminder v4 to v5 migration works`() =
        runTest {
            assertMigrationsWork(
                MigrationTestCase(
                    inputVersion = ReviewReminderSchemaVersion(4),
                    inputJson =
                        """
                        {"id":0,"time":{"hour":9,"minute":15},"cardTriggerThreshold":3,"scope":{"type":"com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific","did":$did1},"enabled":true,"latestNotifTime":1771193761002,"profileID":"","onlyNotifyIfNoReviews":true}
                        """.trimIndent(),
                    expectedOutput =
                        ReviewReminder
                            .createReviewReminder(
                                time = ReviewReminderTime(9, 15),
                                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(3),
                                scope = scope1,
                                enabled = true,
                                profileID = "",
                                onlyNotifyIfNoReviews = true,
                                thresholdFilter =
                                    ReviewReminderThresholdFilter(
                                        countNew = true,
                                        countLrn = true,
                                        countRev = true,
                                    ),
                            ).apply { latestNotifTime = 1771193761002 },
                ),
                MigrationTestCase(
                    inputVersion = ReviewReminderSchemaVersion(4),
                    inputJson =
                        """
                        {"id":1,"time":{"hour":21,"minute":0},"cardTriggerThreshold":0,"scope":{"type":"com.ichi2.anki.reviewreminders.ReviewReminderScope.Global"},"enabled":false,"latestNotifTime":1771193762000,"profileID":"","onlyNotifyIfNoReviews":false}
                        """.trimIndent(),
                    expectedOutput =
                        ReviewReminder
                            .createReviewReminder(
                                time = ReviewReminderTime(21, 0),
                                cardTriggerThreshold = ReviewReminderCardTriggerThreshold(0),
                                scope = ReviewReminderScope.Global,
                                enabled = false,
                                profileID = "",
                                onlyNotifyIfNoReviews = false,
                                thresholdFilter =
                                    ReviewReminderThresholdFilter(
                                        countNew = true,
                                        countLrn = true,
                                        countRev = true,
                                    ),
                            ).apply { latestNotifTime = 1771193762000 },
                ),
            )
        }
}
