// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckSelectionLifecycleTest(
    private val owner: Owner,
) : RobolectricTest() {
    @Test
    fun `deck loading completed while stopped opens the selector on restart`() {
        col
        val controller = Robolectric.buildActivity(AnkiActivity::class.java).setup()
        saveControllerForCleanup(controller)
        val activity = controller.get()
        val fragment = Fragment()
        activity.supportFragmentManager.commitNow { add(fragment, "owner") }
        val asChild = owner == Owner.CHILD_FRAGMENT
        val manager = if (asChild) fragment.childFragmentManager else fragment.parentFragmentManager
        val dispatcher = StandardTestDispatcher()
        val previousDispatcher = CollectionManager.setTestDispatcher(dispatcher, useReentrantLock = false)
        try {
            if (owner == Owner.ACTIVITY) {
                activity.startDeckSelection()
            } else {
                fragment.startDeckSelection(asChild = asChild)
            }
            advanceRobolectricLooper()
            controller.pause().stop()
            assertEquals(Lifecycle.State.CREATED, fragment.lifecycle.currentState)

            dispatcher.scheduler.advanceUntilIdle()
            advanceRobolectricLooper()
            assertNull(manager.findFragmentByTag(DeckSelectionDialog.TAG))

            controller.start().resume().visible()
            advanceRobolectricLooper()
            val dialog = assertNotNull(manager.findFragmentByTag(DeckSelectionDialog.TAG)) as DeckSelectionDialog
            assertTrue(dialog.requireDialog().isShowing)
            dispatcher.scheduler.advanceUntilIdle()
            advanceRobolectricLooper()
        } finally {
            CollectionManager.setTestDispatcher(previousDispatcher)
        }
    }

    enum class Owner {
        FRAGMENT,
        CHILD_FRAGMENT,
        ACTIVITY,
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "owner={0}")
        fun parameters(): List<Owner> = Owner.entries
    }
}
