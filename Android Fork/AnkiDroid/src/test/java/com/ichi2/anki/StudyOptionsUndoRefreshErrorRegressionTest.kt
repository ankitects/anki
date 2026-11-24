// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.collection.OpChanges
import anki.collection.UndoStatus
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class StudyOptionsUndoRefreshErrorRegressionTest : RobolectricTest() {
    @After
    fun resetBackend() = BackendFactory.setOverride(null)

    @Test
    fun `phone pane handles an undo menu read failure after emptying a filtered deck`() {
        val armFailure = AtomicBoolean(false)
        val failUndoRead = AtomicBoolean(false)
        val failure = BackendIoException(backendError { message = "Cannot refresh the study options undo menu" })
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun emptyFilteredDeck(did: Long): OpChanges =
                    super.emptyFilteredDeck(did).also { if (armFailure.get()) failUndoRead.set(true) }

                override fun getUndoStatus(): UndoStatus = if (failUndoRead.getAndSet(false)) throw failure else super.getUndoStatus()
            }
        }
        addBasicNote()
        val filtered = addDynamicDeck("Filtered")
        ensureCollectionLoadIsSynchronous()
        val activity = startActivityNormallyOpenCollectionWithIntent(StudyOptionsActivity::class.java, Intent())
        val pane = assertIs<StudyOptionsFragment>(activity.supportFragmentManager.findFragmentById(R.id.studyoptions_frame))
        advanceRobolectricLooperUntil { pane.viewModel.selectedDeckId == filtered }
        advanceRobolectricLooper()
        throwOnShowError = false
        armFailure.set(true)
        pane.onMenuItemSelected(MenuBuilder(activity).add(0, R.id.action_empty, 0, "Empty"))
        advanceRobolectricLooper()
        assertEquals(0, col.decks.cardCount(filtered), "The write succeeds before the menu read fails")
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
        // RobolectricTest's exception rule also rejects an uncaught coroutine failure.
    }
}
