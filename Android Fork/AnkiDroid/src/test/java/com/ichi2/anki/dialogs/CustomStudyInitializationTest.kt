// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Looper
import androidx.fragment.app.DialogFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.testutils.EmptyAnkiActivity
import com.ichi2.testutils.uninitializeField
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CustomStudyInitializationTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        uninitializeField<CustomStudyDialog>("deferredInitialization")
    }

    @Test
    fun `native cancellation prevents late deck selection`() =
        assertNoLateSelection { _, fragment ->
            fragment.requireDialog().cancel()
            assertFalse(fragment.requireDialog().isShowing)
        }

    @Test
    fun `fragment dismissal prevents late deck selection`() =
        assertNoLateSelection { _, fragment ->
            val dialog = fragment.requireDialog()
            fragment.dismiss()
            assertFalse(dialog.isShowing)
        }

    @Test
    fun `finishing before pause prevents late deck selection`() =
        assertNoLateSelection { activity, _ ->
            activity.finish()
            assertTrue(activity.isFinishing)
        }

    @Test
    fun `replacing the loading menu cancels initialization`() =
        assertNoLateSelection(expectResumed = false) { activity, fragment ->
            activity.showDialogFragment(DialogFragment())
            assertFalse(fragment.isAdded)
        }

    @Test
    fun `native cancellation followed by teardown prevents late deck selection`() =
        assertNoLateSelection(expectResumed = false) { activity, fragment ->
            fragment.requireDialog().cancel()
            shadowOf(Looper.getMainLooper()).idle()
            activity.supportFragmentManager.executePendingTransactions()
            assertFalse(fragment.isAdded)
        }

    private fun assertNoLateSelection(
        expectResumed: Boolean = true,
        close: (EmptyAnkiActivity, CustomStudyDialog) -> Unit,
    ) = runTest {
        val target = addDeck("Target")
        col.setDeck(listOf(addBasicNote().firstCard().id), target)
        val undoStatus = col.undoStatus()
        assertEquals(DEFAULT_DECK_ID, col.decks.selected())
        val previousIo = ioDispatcher
        ioDispatcher = StandardTestDispatcher(testScheduler)
        try {
            Robolectric.buildActivity(EmptyAnkiActivity::class.java).setup().use { controller ->
                val activity = controller.get()
                val fragment = CustomStudyDialog.createInstance(target)
                activity.showDialogFragment(fragment)
                assertEquals(DEFAULT_DECK_ID, col.decks.selected())

                close(activity, fragment)
                assertEquals(expectResumed, fragment.isResumed)
                runCurrent()

                assertEquals(DEFAULT_DECK_ID, col.decks.selected(), "A closed Custom Study must not change deck selection")
                assertEquals(undoStatus, col.undoStatus())
            }
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            ioDispatcher = previousIo
        }
    }
}
