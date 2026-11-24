// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.testutils.BackendEmulatingOpenConflict
import com.ichi2.testutils.FragmentCoroutineTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class CoroutineHelpersRobolectricTest : RobolectricTest() {
    private lateinit var originalCrashReporter: CrashReporter
    private val crashReporter = mock<CrashReporter>()

    @Before
    fun captureCrashReports() {
        originalCrashReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(crashReporter)
    }

    @After
    fun restoreCrashReporter() {
        CrashReportService.setReporter(originalCrashReporter)
    }

    @Test
    fun `attached fragment runs catching task`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        saveControllerForCleanup(controller)
        val fragment = Fragment()
        controller.get().supportFragmentManager.commitNow { add(fragment, "task") }
        var ran = false
        val job = fragment.launchCatchingTask { ran = true }
        advanceRobolectricLooper()
        assertTrue(job.isCompleted)
        assertTrue(ran)
        verify(crashReporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
    }

    /** Issue 22348/22065: removal can precede lifecycle scope registration and the queued task. */
    @Test
    fun `fragment removed before catching task starts skips the task`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        saveControllerForCleanup(controller)
        val activity = controller.get()
        val manager = activity.supportFragmentManager
        FragmentCoroutineTracker(manager).use { tracker ->
            var ran = false
            lateinit var task: Job
            val job =
                activity.lifecycleScope.launch {
                    val fragment = Fragment()
                    manager.commitNow { add(fragment, "task") }
                    task = fragment.launchCatchingTask { ran = true }
                    manager.commitNow { remove(fragment) }
                }
            advanceRobolectricLooper()
            assertTrue(job.isCompleted)
            assertTrue(task.isCompleted)
            assertFalse(ran)
            tracker.assertDestroyedLifecyclesAreIdle()
        }
        verify(crashReporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `readded fragment only runs tasks for its new lifecycle`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        saveControllerForCleanup(controller)
        val activity = controller.get()
        val manager = activity.supportFragmentManager
        FragmentCoroutineTracker(manager).use { tracker ->
            val tasksRun = mutableListOf<String>()
            val job =
                activity.lifecycleScope.launch {
                    val fragment = Fragment()
                    manager.commitNow { add(fragment, "task") }
                    val oldLifecycle = fragment.lifecycle
                    fragment.launchCatchingTask { tasksRun.add("old") }
                    manager.commitNow { remove(fragment) }
                    manager.commitNow { add(fragment, "task") }
                    assertNotSame(oldLifecycle, fragment.lifecycle)
                    fragment.launchCatchingTask { tasksRun.add("new") }
                }
            advanceRobolectricLooper()
            assertTrue(job.isCompleted)
            assertEquals(listOf("new"), tasksRun)
            tracker.assertDestroyedLifecyclesAreIdle()
        }
    }

    @Test
    fun `unattached fragment task fails under Robolectric`() =
        assertLifecycleFailure("not attached to an activity") {
            Fragment().launchCatchingTask { error("Unattached fragment task must not run") }
            advanceRobolectricLooper()
        }

    @Test
    fun `finishing activity fails fragment task under Robolectric`() =
        assertLifecycleFailure("activity is finishing") {
            val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
            saveControllerForCleanup(controller)
            val activity = controller.get()
            val fragment = Fragment()
            activity.supportFragmentManager.commitNow { add(fragment, "task") }
            var ran = false
            val job =
                activity.lifecycleScope.launch {
                    fragment.launchCatchingTask { ran = true }
                    activity.finish()
                }
            advanceRobolectricLooper()
            assertTrue(job.isCompleted)
            assertTrue(activity.isFinishing)
            assertNotNull(fragment.activity)
            assertFalse(ran)
        }

    @Test
    fun `invalid fragment task skips without reporting or throwing outside Robolectric`() {
        val originalFingerprint = Build.FINGERPRINT
        try {
            ShadowBuild.setFingerprint("production")
            runTest {
                val job = Fragment().launchCatchingTask { error("Invalid fragment task must not run") }
                advanceRobolectricLooper()
                assertTrue(job.isCompleted)
                assertFalse(job.isCancelled)
            }
        } finally {
            ShadowBuild.setFingerprint(originalFingerprint)
        }
        verify(crashReporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
    }

    private fun assertLifecycleFailure(
        message: String,
        block: () -> Unit,
    ) {
        val failure = assertFailsWith<IllegalStateException> { runTest { block() } }
        assertThat(failure.message, containsString(message))
        verify(crashReporter, never()).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
    }

    @Test
    fun `view task is cancelled when the view is destroyed but the fragment remains attached`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        saveControllerForCleanup(controller)
        val activity = controller.get()
        val fragment = Fragment(android.R.layout.simple_list_item_1)
        activity.supportFragmentManager.commitNow { add(android.R.id.content, fragment) }

        var cancelled = false
        val job =
            assertNotNull(
                fragment.launchCatchingViewTask {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                },
            )
        assertTrue(job.isActive)

        activity.supportFragmentManager.commitNow { detach(fragment) }
        advanceRobolectricLooper()

        assertNull(fragment.view)
        assertSame(activity, fragment.activity)
        assertTrue(job.isCancelled)
        assertTrue(cancelled)
    }

    @Test
    fun `finishing activity fails view task under Robolectric`() =
        assertLifecycleFailure("activity is finishing") {
            val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
            saveControllerForCleanup(controller)
            val activity = controller.get()
            val fragment = Fragment(android.R.layout.simple_list_item_1)
            activity.supportFragmentManager.commitNow { add(android.R.id.content, fragment) }
            var ran = false

            activity.lifecycleScope.launch {
                fragment.launchCatchingViewTask { ran = true }
                activity.finish()
            }
            advanceRobolectricLooper()

            assertTrue(activity.isFinishing)
            assertFalse(ran)
        }

    /**
     * A [StorageNotConfiguredException] escaping a coroutine means an activity raced or
     * outlived its [ensureStorageIsReady][com.ichi2.anki.startup.ensureStorageIsReady] check:
     * the user should be sent to the main entry point, which handles storage setup.
     */
    @Test
    fun `launchCatchingTask redirects to main entry point when storage is not configured`() {
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            val activity = controller.create().get()

            activity.launchCatchingTask { throw StorageNotConfiguredException() }
            advanceRobolectricLooper()

            assertTrue(activity.isFinishing, "activity should finish")
            val redirect = shadowOf(activity).nextStartedActivity
            assertNotNull(redirect, "the main entry point should be opened")
            assertEquals(IntentHandler::class.qualifiedName, redirect.component?.className)
        }
    }

    /**
     * #21051: the collection lock is normally held by a second AnkiDroid install sharing the
     * AnkiDroid folder. The backend's 'Anki already open' text doesn't explain this on Android,
     * where the other app is invisible: [CollectionLockedException] carries guidance naming the
     * likely cause instead.
     */
    @Test
    fun `launchCatchingTask explains a locked collection`() =
        withLockedCollection {
            throwOnShowError = false
            val controller = Robolectric.buildActivity(FragmentActivity::class.java).also(::saveControllerForCleanup)
            val activity = controller.create().get()
            activity.setTheme(CommonR.style.Theme_AnkiDroid)

            activity.launchCatchingTask { withCol { } }
            advanceRobolectricLooper()

            assertThat(getAlertDialogText(true), containsString("Advanced settings"))
        }

    /** See `launchCatchingTask explains a locked collection`: the ViewModel error funnel */
    @Test
    fun `launchCatching explains a locked collection`() =
        withLockedCollection {
            runTest {
                var message: String? = null

                launchCatching(errorMessageHandler = { message = it }) { withCol { } }.join()

                assertThat(message, containsString("Advanced settings"))
            }
        }

    @Test
    fun `launchCatching can end with undoableOp`() =
        runTest {
            var message: String? = null

            launchCatching(errorMessageHandler = { message = it }) {
                undoableOp { sched.unburyDeck(Consts.DEFAULT_DECK_ID) }
            }.join()

            assertNull(message)
        }

    /**
     * The locked-collection dialog offers Settings, opened at the Advanced screen, where
     * changing the 'AnkiDroid directory' resolves the conflict
     */
    @Test
    fun `a locked collection error links to Advanced settings`() =
        withLockedCollection {
            throwOnShowError = false
            val controller = Robolectric.buildActivity(FragmentActivity::class.java).also(::saveControllerForCleanup)
            val activity = controller.create().get()
            activity.setTheme(CommonR.style.Theme_AnkiDroid)

            activity.launchCatchingTask { withCol { } }
            advanceRobolectricLooper()

            val helpButton = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL)
            assertEquals("Settings", helpButton.text.toString())

            helpButton.performClick()
            advanceRobolectricLooper()

            val settings = shadowOf(activity).nextStartedActivity
            assertNotNull(settings, "Advanced settings should be opened")
            assertEquals(PreferencesActivity::class.qualifiedName, settings.component?.className)
        }

    /** Emulates #21051: another AnkiDroid install holds the collection lock */
    private fun withLockedCollection(block: () -> Unit) {
        BackendEmulatingOpenConflict.enable()
        try {
            block()
        } finally {
            BackendEmulatingOpenConflict.disable()
        }
    }
}
