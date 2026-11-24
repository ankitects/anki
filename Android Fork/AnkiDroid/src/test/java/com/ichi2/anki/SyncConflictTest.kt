// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.syncCollectionResponse
import app.cash.turbine.test
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class SyncConflictTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var qualifiers: String? = null

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters
        @JvmStatic
        fun configurations(): Collection<String> = listOf("normal", "xlarge")
    }

    override fun setupTestDispatcher(dispatcher: TestDispatcher) {
        super.setupTestDispatcher(dispatcher)
        // undo the Main dispatcher override from `runTest`
        Dispatchers.resetMain()
    }

    @Before
    fun setUpConflict() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun syncCollection(
                    auth: SyncAuth,
                    syncMedia: Boolean,
                ): SyncCollectionResponse =
                    syncCollectionResponse {
                        required = SyncCollectionResponse.ChangesRequired.FULL_SYNC
                    }
            }
        }
    }

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `sync conflict remains visible after sync finishes and decks reload`() =
        deckPicker {
            syncWithConflict()

            val dialog = conflictDialog()
            assertTrue(dialog.isShowing)
            assertEquals(getString(CommonString.sync_conflict_message_new), dialog.message)
            assertEquals(getString(CommonString.sync_conflict_keep_local_new), dialog.positiveButton.text)
            assertEquals(getString(CommonString.sync_conflict_keep_remote_new), dialog.negativeButton.text)
        }

    @Test
    fun `sync conflict can be cancelled after deck refresh`() =
        deckPicker {
            syncWithConflict()
            val dialog = conflictDialog()
            reloadDecks()

            dialog.clickButton(DialogInterface.BUTTON_NEUTRAL)

            assertFalse(dialog.isShowing)
        }

    @Test
    fun `upload confirmation survives deck refresh`() =
        deckPicker {
            val confirmation = chooseSyncDirection(ConflictResolution.FULL_UPLOAD)

            reloadDecks()

            assertSame(confirmation, conflictDialog())
            assertTrue(confirmation.isShowing)
            assertEquals(getString(CommonString.sync_conflict_local_confirm_new), confirmation.message)
        }

    @Test
    fun `download confirmation survives deck refresh`() =
        deckPicker {
            val confirmation = chooseSyncDirection(ConflictResolution.FULL_DOWNLOAD)

            reloadDecks()

            assertSame(confirmation, conflictDialog())
            assertTrue(confirmation.isShowing)
            assertEquals(getString(CommonString.sync_conflict_remote_confirm_new), confirmation.message)
        }

    @Test
    fun `upload confirmation can be cancelled after deck refresh`() =
        deckPicker {
            val confirmation = chooseSyncDirection(ConflictResolution.FULL_UPLOAD)
            reloadDecks()

            confirmation.clickButton(DialogInterface.BUTTON_NEGATIVE)

            assertFalse(confirmation.isShowing)
        }

    @Test
    fun `download confirmation can be cancelled after deck refresh`() =
        deckPicker {
            val confirmation = chooseSyncDirection(ConflictResolution.FULL_DOWNLOAD)
            reloadDecks()

            confirmation.clickButton(DialogInterface.BUTTON_NEGATIVE)

            assertFalse(confirmation.isShowing)
        }

    private suspend fun DeckPicker.syncWithConflict() {
        Prefs.hkey = "test"
        Prefs.lastSyncTime = 0
        viewModel.flowOfDecksReloaded.test {
            // Consume the replayed startup event, then wait for sync's own reload.
            awaitItem()
            handleNewSync(conflict = null, syncMedia = false)
            advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }
            awaitItem()
            viewModel.loadDeckCounts?.join()
            advanceRobolectricLooper()
        }
    }

    private suspend fun DeckPicker.chooseSyncDirection(direction: ConflictResolution): AlertDialog {
        syncWithConflict()
        val button =
            when (direction) {
                ConflictResolution.FULL_UPLOAD -> DialogInterface.BUTTON_POSITIVE
                ConflictResolution.FULL_DOWNLOAD -> DialogInterface.BUTTON_NEGATIVE
            }
        conflictDialog().clickButton(button)
        return conflictDialog()
    }

    private suspend fun DeckPicker.reloadDecks() {
        viewModel.reloadDeckCounts().join()
        advanceRobolectricLooper()
    }

    private fun AlertDialog.clickButton(button: Int) {
        check(isShowing) { "The dialog must be visible before clicking a button" }
        getButton(button).performClick()
        advanceRobolectricLooper()
    }

    private fun DeckPicker.conflictDialog(): AlertDialog {
        val fragment = assertNotNull(supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? SyncErrorDialog)
        return assertNotNull(fragment.dialog as? AlertDialog)
    }
}
