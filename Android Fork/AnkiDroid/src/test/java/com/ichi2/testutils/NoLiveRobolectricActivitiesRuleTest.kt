// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.ichi2.testutils.Robolectric as RobolectricActivities

@RunWith(AndroidJUnit4::class)
class NoLiveRobolectricActivitiesRuleTest {
    @Test
    fun `preserves the original failure when no activity leaks`() {
        val original = AssertionError("original test failure")

        val failure = assertFailsWith<AssertionError> { evaluate { throw original } }

        assertSame(original, failure)
    }

    @Test
    fun `reports both the original failure and the activity leak`() {
        val original = AssertionError("original test failure")

        val failure =
            assertFailsWith<MultipleFailureException> {
                evaluate {
                    Robolectric.buildActivity(Activity::class.java).setup()
                    throw original
                }
            }

        assertEquals(2, failure.failures.size)
        assertSame(original, failure.failures[0])
        val leak = assertIs<AssertionError>(failure.failures[1])
        assertContains(leak.message!!, "leftover activities")
        assertTrue(RobolectricActivities.liveActivities().isEmpty())
    }

    @Test
    fun `detects an unowned leak after cleaning up owned controllers`() {
        // Exercise the real @Before, @After and rules inside the current Robolectric sandbox.
        val result = JUnitCore().run(BlockJUnit4ClassRunner(MixedOwnershipTest::class.java))

        assertEquals(1, result.runCount)
        assertEquals(1, result.failureCount, result.failures.toString())
        val failure = assertIs<AssertionError>(result.failures.single().exception)
        assertContains(failure.message!!, "leftover activities")
        assertTrue(RobolectricActivities.liveActivities().isEmpty())
    }

    @Test
    fun `checks for leaks after other rules finish cleanup`() {
        val result = JUnitCore().run(BlockJUnit4ClassRunner(RuleManagedActivityTest::class.java))

        assertEquals(1, result.runCount)
        assertEquals(0, result.failureCount, result.failures.toString())
        assertTrue(RobolectricActivities.liveActivities().isEmpty())
    }

    @Test
    fun `reports failed owned destruction after cleanup and prevents recreation`() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val result = JUnitCore().run(BlockJUnit4ClassRunner(FailedOwnedCleanupTest::class.java))
        val controller = checkNotNull(FailedOwnedCleanupTest.controller)
        val activity = controller.get()
        try {
            assertEquals(1, result.runCount)
            assertEquals(1, result.failureCount, result.failures.toString())
            assertSame(activity.destroyFailure, result.failures.single().exception)
            assertTrue(RobolectricActivities.liveActivities().isEmpty())
            assertFalse(CollectionManager.isOpenUnsafe())
            assertTrue(FailedOwnedCleanupTest.subclassCleanupRan)
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, activity.recreationCount)
        } finally {
            FailedOwnedCleanupTest.controller = null
            cleanupThrowingActivity(controller)
        }
    }

    @Test
    fun `reports the leak and its destruction failure without leaving an active delegate`() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val controller = Robolectric.buildActivity(ThrowingDestroyActivity::class.java).setup()
        val activity = controller.get()
        try {
            val failure = assertFailsWith<MultipleFailureException> { evaluate {} }

            assertEquals(2, failure.failures.size)
            assertContains(assertIs<AssertionError>(failure.failures[0]).message!!, "leftover activities")
            assertSame(activity.destroyFailure, failure.failures[1])
            assertTrue(RobolectricActivities.liveActivities().isEmpty())
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, activity.recreationCount)
        } finally {
            cleanupThrowingActivity(controller)
        }
    }

    private fun cleanupThrowingActivity(controller: ActivityController<ThrowingDestroyActivity>) {
        // Keep the regression tests isolated even when the cleanup being tested is broken.
        try {
            controller.get().delegate.onDestroy()
        } finally {
            RobolectricActivities.forceRemoveActivity(controller.get())
        }
    }

    private fun evaluate(block: () -> Unit) {
        val statement =
            object : Statement() {
                override fun evaluate() = block()
            }
        NoLiveRobolectricActivitiesRule()
            .apply(statement, Description.createTestDescription(javaClass, "test"))
            .evaluate()
    }

    @Ignore("Run explicitly by the enclosing test inside its Robolectric sandbox")
    class MixedOwnershipTest : RobolectricTest() {
        @Test
        fun `leaks an activity alongside an owned controller`() {
            saveControllerForCleanup(Robolectric.buildActivity(Activity::class.java).setup())
            Robolectric.buildActivity(Activity::class.java).setup()
        }
    }

    @Ignore("Run explicitly by the enclosing test inside its Robolectric sandbox")
    class FailedOwnedCleanupTest : RobolectricTest() {
        @Test
        fun `owns an activity whose destruction fails`() {
            subclassCleanupRan = false
            col
            val activityController = Robolectric.buildActivity(ThrowingDestroyActivity::class.java).setup()
            controller = activityController
            saveControllerForCleanup(activityController)
            // Cleanup must continue to the next controller and the collection after the failure.
            saveControllerForCleanup(Robolectric.buildActivity(Activity::class.java).setup())
        }

        override fun tearDown() {
            super.tearDown()
            subclassCleanupRan = true
        }

        companion object {
            var controller: ActivityController<ThrowingDestroyActivity>? = null
            var subclassCleanupRan = false
        }
    }

    class ThrowingDestroyActivity : AppCompatActivity() {
        val destroyFailure = IllegalStateException("activity destruction failed")
        var recreationCount = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat)
            super.onCreate(savedInstanceState)
        }

        // Intentionally omit super.onDestroy() so cleanup paths that fail mid-destroy can be tested.
        @SuppressLint("MissingSuperCall")
        override fun onDestroy(): Unit = throw destroyFailure

        override fun recreate() {
            recreationCount++
            super.recreate()
        }
    }

    @Ignore("Run explicitly by the enclosing test inside its Robolectric sandbox")
    class RuleManagedActivityTest : RobolectricTest() {
        private lateinit var controller: ActivityController<Activity>

        @get:Rule
        val activity =
            object : ExternalResource() {
                override fun before() {
                    controller = Robolectric.buildActivity(Activity::class.java).setup()
                }

                override fun after() {
                    controller.close()
                }
            }

        @Test
        fun `activity is closed by its rule`() {
            assertSame(controller.get(), RobolectricActivities.liveActivities().single())
        }
    }
}
