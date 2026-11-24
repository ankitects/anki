// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Build
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.FragmentLifecycleFixture.Transition
import com.ichi2.anki.FragmentLifecycleFixture.Transition.DESTROY_VIEW
import com.ichi2.anki.FragmentLifecycleFixture.Transition.RECREATE_VIEW
import com.ichi2.anki.FragmentLifecycleFixture.Transition.REMOVE
import com.ichi2.testutils.FragmentCoroutineTracker
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowBuild
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Startup lifecycle stress tests for Issue 22065; not navigation reproductions for every screen. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class FragmentCoroutineLifecycleTest(
    private val fixture: FragmentLifecycleFixture,
) : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    /**
     * Validates a normal startup path, and that there are no active coroutines after onDestroy().
     */
    @Test
    fun `normal startup and removal`() =
        withHost { activity, fragment ->
            add(activity, fragment)
            advanceRobolectricLooper()
            assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
            activity.supportFragmentManager.commitNow { remove(fragment) }
            advanceRobolectricLooper()
            assertNull(fragment.activity)
        }

    /**
     * Removes the fragment while startup coroutines are still queued (#22065).
     * Delayed work should neither crash nor outlive the destroyed lifecycle.
     */
    @Test
    fun `removed before startup coroutines execute`() =
        withQueuedStartup(REMOVE) { activity, fragment ->
            add(activity, fragment)
            val lifecycle = fragment.lifecycle
            activity.supportFragmentManager.commitNow { remove(fragment) }
            assertEquals(Lifecycle.State.DESTROYED, lifecycle.currentState)
            assertNull(fragment.activity)
        }

    /**
     * Destroys the view before startup work runs, while keeping the fragment attached to its activity.
     *
     * - No crashes if a view is missing.
     * - No coroutines outlive the destroyed view lifecycle.
     */
    @Test
    fun `view destroyed before startup coroutines execute`() =
        withQueuedStartup(DESTROY_VIEW) { activity, fragment ->
            add(activity, fragment)
            assertNotNull(fragment.view)
            val owner = fragment.viewLifecycleOwner
            activity.supportFragmentManager.commitNow { detach(fragment) }
            assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
            assertNull(fragment.view)
            assertSame(activity, fragment.activity)
            assertEquals(Lifecycle.State.CREATED, fragment.lifecycle.currentState)
        }

    /**
     * Detaches and reattaches the fragment before startup work runs, exercising view recreation.
     *
     * - Startup survives the transition.
     * - No coroutines remain in the old view lifecycle scope.
     */
    @Test
    fun `view recreated before startup coroutines execute`() =
        withQueuedStartup(RECREATE_VIEW) { activity, fragment ->
            add(activity, fragment)
            val oldView = assertNotNull(fragment.view)
            val oldOwner = fragment.viewLifecycleOwner
            activity.supportFragmentManager.commitNow { detach(fragment) }
            assertEquals(Lifecycle.State.DESTROYED, oldOwner.lifecycle.currentState)
            activity.supportFragmentManager.commitNow { attach(fragment) }
            assertNotSame(oldView, assertNotNull(fragment.view))
            assertNotSame(oldOwner, fragment.viewLifecycleOwner)
            assertEquals(Lifecycle.State.RESUMED, fragment.viewLifecycleOwner.lifecycle.currentState)
            assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
        }

    private fun withQueuedStartup(
        transition: Transition,
        block: (AnkiActivity, Fragment) -> Unit,
    ) {
        if (transition != REMOVE) {
            assumeTrue("$fixture has no fragment view", fixture.hasFragmentView)
        }
        withHost { activity, fragment ->
            // Main.immediate queues nested launches until this outer coroutine finishes.
            // This lets the test destroy the fragment/view before its startup work runs.
            // Using runTest would replace Main and hide that ordering.
            val job = activity.lifecycleScope.launch { block(activity, fragment) }
            advanceRobolectricLooper()
            assertTrue(job.isCompleted, "The lifecycle transition must execute")
        }
    }

    private fun withHost(block: (AnkiActivity, Fragment) -> Unit) {
        // Use a real open collection for settings reads and view model initialization.
        col
        val controller = Robolectric.buildActivity(fixture.host).setup()
        saveControllerForCleanup(controller)
        advanceRobolectricLooper()
        val activity = controller.get()
        withCoroutineScheduling {
            FragmentCoroutineTracker(activity.supportFragmentManager).use { tracker ->
                block(activity, fixture.create(this))
                tracker.assertDestroyedLifecyclesAreIdle()
            }
        }
    }

    private fun withCoroutineScheduling(block: () -> Unit) {
        val originalFingerprint = Build.FINGERPRINT
        // Complete collection work deterministically without replacing Main.immediate.
        val originalDispatcher = CollectionManager.setTestDispatcher(UnconfinedTestDispatcher())
        // Flow.launchCollectionInLifecycleScope otherwise posts to a test-only Handler,
        // which adds a lifecycle check and hides failures in the production collector.
        ShadowBuild.setFingerprint("fragment-lifecycle-test")
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(originalDispatcher)
            ShadowBuild.setFingerprint(originalFingerprint)
        }
    }

    private fun add(
        activity: AnkiActivity,
        fragment: Fragment,
    ) {
        if (fragment is DialogFragment) {
            fragment.showNow(activity.supportFragmentManager, "fragment-under-test")
        } else {
            activity.supportFragmentManager.commitNow { add(android.R.id.content, fragment) }
            assertNotNull(fragment.view)
        }
        assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun fixtures(): List<FragmentLifecycleFixture> = fragmentLifecycleFixtures()
    }
}
