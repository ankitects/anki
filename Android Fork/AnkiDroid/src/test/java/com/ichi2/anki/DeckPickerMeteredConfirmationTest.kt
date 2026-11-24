// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.sync.MeteredSyncPermission
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.getCheckBoxPrompt
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerMeteredConfirmationTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var qualifiers: String = ""

    private var metered = true
    private val attempts = mutableListOf<SyncAttempt>()

    @Before
    fun prepareSync() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        setIntroductionSlidesShown(true)
        addBasicNote()
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        getPreferences().edit { putBoolean("backupPromptDisabled", true) }
        Prefs.hkey = "test"
        Prefs.allowSyncOnMeteredConnections = false
        Prefs.shouldFetchMedia = ShouldFetchMedia.ALWAYS
        mockkObject(NetworkUtils)
        every { NetworkUtils.isActiveNetworkMetered() } answers { metered }
        mockkStatic("com.ichi2.anki.SyncKt")
        every { any<DeckPicker>().handleNewSync(any(), any(), any()) } answers {
            attempts.add(SyncAttempt(firstArg(), secondArg(), thirdArg(), arg(3)))
        }
    }

    @After
    fun resetOverrides() {
        unmockkStatic("com.ichi2.anki.SyncKt")
        unmockkObject(NetworkUtils)
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `warning survives repeated recreation and continues once with the original request`() {
        val controller = launch()
        controller.get().sync()
        val original = warning(controller.get())
        // Changing preferences while the decision is pending must not change the accepted request.
        Prefs.shouldFetchMedia = ShouldFetchMedia.NEVER

        repeat(2) {
            recreate(controller)
            assertNotSame(original, warning(controller.get()))
            assertTrue(attempts.isEmpty())
        }
        warning(controller.get()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        assertEquals(
            listOf(SyncAttempt(controller.get(), null, true, MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME)),
            attempts,
        )
        assertFalse(Prefs.allowSyncOnMeteredConnections)
        recreate(controller)
        assertNoWarning()
        assertEquals(1, attempts.size)
    }

    @Test
    fun `Cancel after recreation consumes the decision without syncing`() = cancelRestoredWarning(button = true)

    @Test
    fun `dismissal after recreation consumes the decision without syncing`() = cancelRestoredWarning(button = false)

    private fun cancelRestoredWarning(button: Boolean) {
        val controller = launch()
        controller.get().sync()
        warning(controller.get())
        recreate(controller)
        val dialog = warning(controller.get())
        if (button) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        } else {
            dialog.cancel()
        }
        advanceRobolectricLooper()
        recreate(controller)

        assertNoWarning()
        assertTrue(attempts.isEmpty())
    }

    @Test
    fun `checkbox choice survives recreation and still updates the preference`() {
        val controller = launch()
        controller.get().sync()
        warning(controller.get()).getCheckBoxPrompt().isChecked = true
        assertTrue(Prefs.allowSyncOnMeteredConnections)

        recreate(controller)

        val restored = warning(controller.get())
        assertTrue(restored.getCheckBoxPrompt().isChecked)
        assertTrue(attempts.isEmpty())
        restored.getCheckBoxPrompt().isChecked = false
        assertFalse(Prefs.allowSyncOnMeteredConnections)
        recreate(controller)
        assertFalse(warning(controller.get()).getCheckBoxPrompt().isChecked)
        assertTrue(attempts.isEmpty())
    }

    @Test
    fun `unmetered sync does not show a warning`() {
        metered = false
        val activity = launch().get()

        activity.sync()

        assertNoWarning()
        assertEquals(listOf(SyncAttempt(activity, null, true, MeteredSyncPermission.USE_PREFERENCES)), attempts)
    }

    @Test
    fun `allowed metered sync does not show a warning`() {
        Prefs.allowSyncOnMeteredConnections = true
        val activity = launch().get()

        activity.sync()

        assertNoWarning()
        assertEquals(listOf(SyncAttempt(activity, null, true, MeteredSyncPermission.USE_PREFERENCES)), attempts)
    }

    @Test
    fun `conflict retries skip the warning`() {
        val activity = launch().get()

        for (conflict in ConflictResolution.entries) {
            activity.sync(conflict)
            assertEquals(SyncAttempt(activity, conflict, true, MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME), attempts.last())
        }

        assertNoWarning()
        assertEquals(ConflictResolution.entries.size, attempts.size)
    }

    @Test
    fun `recreation without a pending decision does not sync`() {
        val controller = launch()

        recreate(controller)

        assertNoWarning()
        assertTrue(attempts.isEmpty())
    }

    @Test
    fun `pending confirmation blocks auto sync after checking do not show again`() =
        checkPendingConfirmationBlocksAutoSync(checkAlwaysAllow = true)

    @Test
    fun `pending confirmation blocks auto sync after changing to an unmetered network`() =
        checkPendingConfirmationBlocksAutoSync(checkAlwaysAllow = false)

    @Test
    fun `pending confirmation blocks repeated requests after checking do not show again`() =
        checkPendingConfirmationBlocksRepeatedRequests(checkAlwaysAllow = true)

    @Test
    fun `pending confirmation blocks repeated requests after changing to an unmetered network`() =
        checkPendingConfirmationBlocksRepeatedRequests(checkAlwaysAllow = false)

    private fun checkPendingConfirmationBlocksRepeatedRequests(checkAlwaysAllow: Boolean) {
        val controller = launch()
        val activity = controller.get()
        activity.sync()
        val dialog = warning(activity)
        if (checkAlwaysAllow) {
            dialog.getCheckBoxPrompt().isChecked = true
        } else {
            metered = false
        }
        Prefs.shouldFetchMedia = ShouldFetchMedia.NEVER

        activity.sync()

        assertSame(dialog, warning(activity))
        assertTrue(attempts.isEmpty())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        assertEquals(
            listOf(SyncAttempt(activity, null, true, MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME)),
            attempts,
        )
    }

    private fun checkPendingConfirmationBlocksAutoSync(checkAlwaysAllow: Boolean) {
        getPreferences().edit {
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), true)
            putBoolean(getResourceString(R.string.sync_status_badge_key), false)
        }
        Prefs.lastSyncTime = 0
        every { NetworkUtils.isOnline } returns true
        val backend = CollectionManager.getBackend()
        mockkObject(backend)
        every { backend.syncStatus(any<SyncAuth>()) } returns
            SyncStatusResponse.newBuilder().setRequired(SyncStatusResponse.Required.NORMAL_SYNC).build()

        // Startup's auto-sync uses Dispatchers.IO. Wait for the real activity jobs to
        // finish, including when the pending confirmation correctly prevents syncing.
        val activityTasks = mutableListOf<Job>()
        mockkStatic("com.ichi2.anki.CoroutineHelpersKt")
        every { any<DeckPicker>().launchCatchingTask(any(), any(), any()) } answers {
            callOriginal().also(activityTasks::add)
        }
        try {
            val controller = launch()
            advanceRobolectricLooperUntil { activityTasks.all { it.isCompleted } }
            controller.get().sync()
            val dialog = warning(controller.get())
            if (checkAlwaysAllow) {
                dialog.getCheckBoxPrompt().isChecked = true
            } else {
                metered = false
            }
            assertTrue(attempts.isEmpty())

            recreate(controller)
            advanceRobolectricLooperUntil { activityTasks.all { it.isCompleted } }

            assertEquals(0, attempts.size, "Recreation must not sync while the confirmation is pending")
            verify(exactly = 0) { backend.syncStatus(any<SyncAuth>()) }
            warning(controller.get()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()
            assertEquals(
                listOf(SyncAttempt(controller.get(), null, true, MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME)),
                attempts,
            )
        } finally {
            unmockkStatic("com.ichi2.anki.CoroutineHelpersKt")
            unmockkObject(backend)
        }
    }

    @Test
    fun `confirmation opened during the automatic status query blocks media only completion`() {
        getPreferences().edit {
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), true)
            putBoolean(getResourceString(R.string.sync_status_badge_key), false)
        }
        Prefs.lastSyncTime = 0
        Prefs.shouldFetchMedia = ShouldFetchMedia.NEVER
        metered = false
        every { NetworkUtils.isOnline } returns true
        val backend = CollectionManager.getBackend()
        mockkObject(backend)
        val queryEntered = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)
        every { backend.syncStatus(any<SyncAuth>()) } answers {
            queryEntered.countDown()
            check(releaseQuery.await(30, TimeUnit.SECONDS))
            SyncStatusResponse.newBuilder().setRequired(SyncStatusResponse.Required.NO_CHANGES).build()
        }
        val activityTasks = mutableListOf<Job>()
        mockkStatic("com.ichi2.anki.CoroutineHelpersKt")
        every { any<DeckPicker>().launchCatchingTask(any(), any(), any()) } answers {
            callOriginal().also(activityTasks::add)
        }
        try {
            val controller = launch()
            advanceRobolectricLooperUntil { queryEntered.count == 0L }
            metered = true
            controller.get().sync()
            warning(controller.get())
            releaseQuery.countDown()
            advanceRobolectricLooperUntil { activityTasks.all { it.isCompleted } }

            assertTrue(attempts.isEmpty())
            assertEquals(0L, Prefs.lastSyncTime, "Pending confirmation must stop automatic sync before recording completion")
        } finally {
            releaseQuery.countDown()
            unmockkStatic("com.ichi2.anki.CoroutineHelpersKt")
            unmockkObject(backend)
        }
    }

    @Test
    fun `late automatic sync waits until resumed before opening a metered confirmation`() {
        getPreferences().edit {
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), true)
            putBoolean(getResourceString(R.string.sync_status_badge_key), false)
        }
        Prefs.lastSyncTime = 0
        metered = false
        every { NetworkUtils.isOnline } returns true
        val backend = CollectionManager.getBackend()
        mockkObject(backend)
        val queryEntered = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)
        every { backend.syncStatus(any<SyncAuth>()) } answers {
            queryEntered.countDown()
            check(releaseQuery.await(30, TimeUnit.SECONDS))
            SyncStatusResponse.newBuilder().setRequired(SyncStatusResponse.Required.NORMAL_SYNC).build()
        }
        val activityTasks = mutableListOf<Job>()
        mockkStatic("com.ichi2.anki.CoroutineHelpersKt")
        every { any<DeckPicker>().launchCatchingTask(any(), any(), any()) } answers {
            callOriginal().also(activityTasks::add)
        }
        try {
            val controller = launch()
            advanceRobolectricLooperUntil { queryEntered.count == 0L }
            controller.pause().saveInstanceState(Bundle()).stop()
            assertTrue(controller.get().supportFragmentManager.isStateSaved)
            metered = true
            releaseQuery.countDown()
            advanceRobolectricLooperUntil { activityTasks.all { it.isCompleted } }
            assertTrue(attempts.isEmpty())
            controller.start().resume().visible()
            val dialog = warning(controller.get())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()
            assertEquals(1, attempts.size)
        } finally {
            releaseQuery.countDown()
            unmockkStatic("com.ichi2.anki.CoroutineHelpersKt")
            unmockkObject(backend)
        }
    }

    @Test
    fun `deferred confirmation retains media sync across recreation`() = checkDeferredConfirmation(syncMedia = true)

    @Test
    fun `deferred confirmation retains collection only sync across recreation`() = checkDeferredConfirmation(syncMedia = false)

    private fun checkDeferredConfirmation(syncMedia: Boolean) {
        val controller = launch()
        controller.pause().saveInstanceState(Bundle()).stop()
        Prefs.shouldFetchMedia = if (syncMedia) ShouldFetchMedia.ALWAYS else ShouldFetchMedia.NEVER

        controller.get().sync()

        assertNoWarning()
        assertTrue(attempts.isEmpty())
        // A second request must neither bypass the deferred warning nor replace its media choice.
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.shouldFetchMedia = if (syncMedia) ShouldFetchMedia.NEVER else ShouldFetchMedia.ALWAYS
        controller.get().sync()
        assertTrue(attempts.isEmpty())

        recreate(controller)
        controller.start().resume().visible()
        controller.get().awaitStartup()
        val dialog = warning(controller.get())
        assertTrue(attempts.isEmpty())
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        assertEquals(
            listOf(SyncAttempt(controller.get(), null, syncMedia, MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME)),
            attempts,
        )
        controller
            .pause()
            .stop()
            .start()
            .resume()
            .visible()
        assertNoWarning()
        assertEquals(1, attempts.size)
    }

    private fun launch(): ActivityController<DeckPicker> =
        startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())

    private fun recreate(controller: ActivityController<DeckPicker>) {
        val original = controller.get()
        controller.recreate()
        val recreated = controller.get()
        recreated.awaitStartup()
        advanceRobolectricLooper()
        assertNotSame(original, recreated)
    }

    private fun warning(activity: DeckPicker): AlertDialog {
        advanceRobolectricLooper()
        // Resuming a stopped activity can show the same dialog again; the shadow records each show.
        val dialogs =
            ShadowDialog
                .getShownDialogs()
                .filterIsInstance<AlertDialog>()
                .filter { it.isShowing }
                .distinct()
        assertEquals(1, dialogs.size)
        return dialogs.single().also {
            assertSame(activity, it.context.findAnkiActivity())
            assertTrue(it.isShowing)
        }
    }

    private fun assertNoWarning() {
        assertFalse(ShadowDialog.getShownDialogs().any { it.isShowing })
    }

    private data class SyncAttempt(
        val activity: DeckPicker,
        val conflict: ConflictResolution?,
        val syncMedia: Boolean,
        val permission: MeteredSyncPermission,
    )

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        @JvmStatic
        fun configurations() = listOf("normal", "xlarge")
    }
}
