// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.disableIntroductionSlide
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.utils.ImportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerImportTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    private val preferences get() = testContext.sharedPrefs()
    private val hadBackupPromptPreference = preferences.contains("backupPromptDisabled")
    private val backupPromptDisabled = preferences.getBoolean("backupPromptDisabled", false)

    @Test
    fun importConfirmationSurvivesDelayedTabletStartup() {
        DialogHandler.storeMessage(ImportUtils.CollectionImportAdd("deck.apkg").toMessage())

        withDelayedStartup { scenario, resumeStartup ->
            assertImportConfirmationVisible()
            scenario.assertStartupWaiting()

            resumeStartup()
            scenario.awaitStartup()

            assertImportConfirmationVisible()
        }
    }

    @Before
    fun prepareTablet() {
        // The study options side panel is selected by layout-sw600dp.
        assumeTrue("Requires the tablet layout", testContext.resources.configuration.smallestScreenWidthDp >= 600)
        disableIntroductionSlide()
        InitialActivity.setUpgradedToLatestVersion(preferences)
        preferences.edit { putBoolean("backupPromptDisabled", true) }
        addNoteUsingBasicNoteType()
    }

    @After
    fun restorePreferencesAndPendingDialogs() {
        DialogHandler.storeMessage(null)
        preferences.edit {
            if (hadBackupPromptPreference) {
                putBoolean("backupPromptDisabled", backupPromptDisabled)
            } else {
                remove("backupPromptDisabled")
            }
        }
    }

    private fun withDelayedStartup(test: suspend (scenario: ActivityScenario<DeckPicker>, resumeStartup: suspend () -> Unit) -> Unit) =
        runBlocking {
            val collectionQueueHeld = CountDownLatch(1)
            val releaseStartup = CountDownLatch(1)
            val pendingCollectionOperation =
                launch(Dispatchers.IO) {
                    withCol {
                        collectionQueueHeld.countDown()
                        check(releaseStartup.await(30, TimeUnit.SECONDS)) { "Startup was not released" }
                    }
                }
            var scenario: ActivityScenario<DeckPicker>? = null
            try {
                assertTrue(collectionQueueHeld.await(10, TimeUnit.SECONDS), "The collection queue must be held before launching")
                val activityScenario = ActivityScenario.launch(DeckPicker::class.java)
                scenario = activityScenario
                test(activityScenario) {
                    releaseStartup.countDown()
                    pendingCollectionOperation.join()
                }
            } finally {
                releaseStartup.countDown()
                withContext(NonCancellable) {
                    pendingCollectionOperation.join()
                    scenario?.close()
                }
            }
        }

    private fun ActivityScenario<DeckPicker>.assertStartupWaiting() {
        onActivity { activity ->
            assertTrue(activity.fragmented, "The tablet side panel must be present")
            assertNull(activity.fragment, "Study options must not have been initialized yet")
            assertTrue(activity.viewModel.startupJob?.isActive == true, "Startup must still be waiting")
        }
    }

    private fun ActivityScenario<DeckPicker>.awaitStartup() {
        waitUntil(message = { "Startup did not finish initializing study options" }) {
            var finished = false
            onActivity { activity ->
                finished =
                    activity.viewModel.startupJob?.isCompleted == true &&
                    activity.viewModel.loadDeckCounts?.isCompleted == true &&
                    activity.fragment != null
            }
            finished
        }
    }

    private fun assertImportConfirmationVisible() {
        onView(withText(testContext.getString(CommonString.import_dialog_message_add, "deck.apkg")))
            .check(matches(isDisplayed()))
        onView(withText(CommonString.import_message_add))
            .check(matches(isDisplayed()))
    }
}
