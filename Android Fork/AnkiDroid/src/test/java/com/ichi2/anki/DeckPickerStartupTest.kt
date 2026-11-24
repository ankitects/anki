// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AnkiDroidEnvironment
import com.ichi2.anki.testutils.SingleViewModelFactory
import com.ichi2.testutils.BackupManagerTestUtilities
import kotlinx.coroutines.Job
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "normal")
class DeckPickerStartupTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `startup finishing before the first resume loads the deck list once`() {
        setIntroductionSlidesShown(true)
        InitialActivity.setUpgradedToLatestVersion(getPreferences())
        getPreferences().edit { putBoolean("backupPromptDisabled", true) }
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        addBasicNote("startup", "ordering")

        val viewModel = spy(DeckPickerViewModel())
        var resumeRefresh: Job? = null
        doAnswer { invocation ->
            (invocation.callRealMethod() as Job).also { resumeRefresh = it }
        }.whenever(viewModel).updateDeckList()

        val controller = Robolectric.buildActivity(DeckPicker::class.java)
        saveControllerForCleanup(controller)
        ViewModelProvider(controller.get(), SingleViewModelFactory.create(viewModel))[DeckPickerViewModel::class.java]
        try {
            // Let startup finish while STARTED, before the first onResume requests a refresh.
            controller.create().start()
            advanceRobolectricLooperUntil {
                viewModel.startupJob?.isCompleted == true && viewModel.loadDeckCounts?.isCompleted == true
            }
            verify(viewModel, times(1)).reloadDeckCounts()

            controller.resume().visible()
            advanceRobolectricLooperUntil { resumeRefresh?.isCompleted == true }
            verify(viewModel, times(1)).reloadDeckCounts()
        } finally {
            BackupManagerTestUtilities.reset()
        }
    }

    @Test
    fun `decks load when startup opens the collection after the initial refresh`() =
        runTest {
            setIntroductionSlidesShown(true)
            InitialActivity.setUpgradedToLatestVersion(getPreferences())
            getPreferences().edit { putBoolean("backupPromptDisabled", true) }
            BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
            addBasicNote("cold start", "should appear")
            CollectionManager.closeCollectionBlocking()

            val releaseStartup = CountDownLatch(1)
            val viewModel = spy(DeckPickerViewModel())
            // Hold the real startup check before it opens the collection, without occupying
            // the collection queue: onResume must be able to observe the closed collection.
            doAnswer { invocation ->
                val environment = invocation.getArgument<AnkiDroidEnvironment>(0)
                doCallRealMethod().whenever(viewModel).handleStartup(any())
                viewModel.handleStartup(
                    object : AnkiDroidEnvironment by environment {
                        override val preferences: SharedPreferences
                            get() {
                                check(releaseStartup.await(10.seconds.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
                                    "The background startup check was not released"
                                }
                                return environment.preferences
                            }
                    },
                )
            }.whenever(viewModel).handleStartup(any())

            val controller = Robolectric.buildActivity(DeckPicker::class.java)
            saveControllerForCleanup(controller)
            val activity = controller.get()
            ViewModelProvider(activity, SingleViewModelFactory.create(viewModel))[DeckPickerViewModel::class.java]
            try {
                controller
                    .create()
                    .start()
                    .resume()
                    .visible()
                // Complete a refresh with the collection still closed, as can happen onResume.
                viewModel.updateDeckList().join()
                assertFalse(CollectionManager.isOpenUnsafe(), "Startup must not have opened the collection yet")

                releaseStartup.countDown()
                viewModel.startupJob!!.join()
                advanceRobolectricLooperUntil {
                    viewModel.flowOfStartupResponse.value == null
                }
                assertTrue(CollectionManager.isOpenUnsafe(), "Startup should have opened the collection")

                advanceRobolectricLooperUntil(
                    timeout = 5.seconds,
                    lazyMessage = {
                        "Successful startup must display the decks even if the initial refresh ran before the collection opened"
                    },
                ) {
                    activity.hasAtLeastOneDeckBeingDisplayed()
                }
            } finally {
                releaseStartup.countDown()
                viewModel.startupJob?.join()
                BackupManagerTestUtilities.reset()
            }
        }
}
