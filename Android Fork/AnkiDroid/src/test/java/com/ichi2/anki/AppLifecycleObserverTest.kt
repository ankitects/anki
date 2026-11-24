// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.widget.WidgetStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class AppLifecycleObserverTest : RobolectricTest() {
    private val owner = TestLifecycleOwner()

    @Before
    fun mockWidgetUpdates() {
        mockkObject(WidgetStatus)
        every { WidgetStatus.updateInBackground(any()) } just runs
    }

    @After
    fun cleanUp() {
        owner.registry.currentState = Lifecycle.State.DESTROYED
        unmockkObject(CollectionManager, WidgetStatus)
    }

    /** Issue 19749: blocking on a sync here prevents WorkManager's service timeout callback. */
    @Test
    fun `backgrounding during sync does not wait for the collection queue`() =
        runTest {
            CollectionManager.ensureOpen()
            val syncFinished = CompletableDeferred<Unit>()
            mockkObject(CollectionManager)
            coEvery { CollectionManager.withOpenColOrNull<Boolean>(any()) } coAnswers {
                syncFinished.await()
                true
            }

            AppLifecycleObserver(targetContext).onStop(owner)
            runCurrent()
            verify(exactly = 0) { WidgetStatus.updateInBackground(any()) }

            syncFinished.complete(Unit)
            advanceUntilIdle()
            verify(exactly = 1) { WidgetStatus.updateInBackground(targetContext) }
        }

    @Test
    fun `backgrounding with a closed collection does not open it or update widgets`() =
        runTest {
            CollectionManager.ensureClosed()
            AppLifecycleObserver(targetContext).onStop(owner)
            advanceUntilIdle()

            verify(exactly = 0) { WidgetStatus.updateInBackground(any()) }
            assertFalse(CollectionManager.isOpenUnsafe())
        }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.STARTED }

        override val lifecycle: Lifecycle get() = registry
    }
}
