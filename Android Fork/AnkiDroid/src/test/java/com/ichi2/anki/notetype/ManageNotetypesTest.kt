// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Chaitanya Medidar <2023.chaitanya.medidar@ves.ac.in>

package com.ichi2.anki.notetype

import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.LoadingDialogFragment
import com.ichi2.anki.progress.ViewModelProgress
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class ManageNotetypesTest : RobolectricTest() {
    @Test
    fun `rename note type - whitespace only name keeps Rename button disabled`() =
        runTest {
            ensureCollectionLoadIsSynchronous()
            addStandardNoteType(TEST_NOTE_TYPE_NAME, arrayOf("front", "back"), "", "")

            val activity = startRegularActivity<ManageNotetypes>()
            val firstNoteType =
                activity.viewModel.state.value.noteTypes
                    .first { it.name == TEST_NOTE_TYPE_NAME }
            activity.renameNotetype(firstNoteType)

            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val nameInput = dialog.findViewById<EditText>(CommonR.id.dialog_text_input)!!
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            // whitespace-only should disable the Rename button
            nameInput.setText("   ")
            assertThat("Rename button disabled for whitespace-only name", positiveButton.isEnabled, equalTo(false))

            // a valid new name should enable the button
            nameInput.setText("Renamed Note Type")
            assertThat("Rename button enabled for a valid name", positiveButton.isEnabled, equalTo(true))

            // empty string should disable it again
            nameInput.setText("")
            assertThat("Rename button disabled for empty name", positiveButton.isEnabled, equalTo(false))
        }

    @Test
    fun `deleting a note type blocks touches until it finishes`() =
        runTest {
            ensureCollectionLoadIsSynchronous()
            addStandardNoteType(TEST_NOTE_TYPE_NAME, arrayOf("front", "back"), "", "")
            val activity = startRegularActivity<ManageNotetypes>()
            val noteType =
                activity.viewModel.state.value.noteTypes
                    .first { it.name == TEST_NOTE_TYPE_NAME }

            withQueuedCollectionAccess {
                activity.viewModel.delete(noteType.id)
                assertTrue("touches blocked while deleting", activity.touchesBlocked())
            }
            advanceUntilIdle()

            assertFalse("touches restored after deleting", activity.touchesBlocked())
            assertTrue(
                "note type deleted",
                activity.viewModel.state.value.noteTypes
                    .none { it.id == noteType.id },
            )
        }

    @Test
    fun `the Add dialog loads its note types under the screen's progress`() =
        runTest {
            ensureCollectionLoadIsSynchronous()
            val activity = startRegularActivity<ManageNotetypes>()

            withQueuedCollectionAccess {
                activity.binding.floatingActionButton.performClick()
                assertTrue("loading the note types", activity.viewModel.progressManager.progress.value is ViewModelProgress.Active)
            }
            advanceUntilIdle()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            assertTrue("loaded", activity.viewModel.progressManager.progress.value is ViewModelProgress.Idle)
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertTrue("the Add dialog is shown", dialog.isShowing)
            assertTrue("the Add dialog has note types", dialog.newNoteTypeBinding.notetypeNewType.count > 0)
        }

    @Test
    fun `back during the progress delay does not finish an activity deleting note types`() =
        runTest {
            ensureCollectionLoadIsSynchronous()
            addStandardNoteType(TEST_NOTE_TYPE_NAME, arrayOf("front", "back"), "", "")
            val activity = startRegularActivity<ManageNotetypes>()
            val noteType =
                activity.viewModel.state.value.noteTypes
                    .first { it.name == TEST_NOTE_TYPE_NAME }
            activity.viewModel.onItemLongClick(noteType)

            try {
                withQueuedCollectionAccess {
                    activity.viewModel.deleteSelectedNoteTypes()
                    assertTrue("deletion is still in progress", activity.touchesBlocked())
                    assertFalse(
                        "the progress dialog has not appeared yet",
                        activity.supportFragmentManager.fragments.any { it is LoadingDialogFragment },
                    )

                    activity.onBackPressedDispatcher.onBackPressed()

                    assertFalse("Back must not finish the activity during deletion", activity.isFinishing)
                }
            } finally {
                advanceUntilIdle()
            }
        }

    private suspend fun <T> TestScope.withQueuedCollectionAccess(block: suspend () -> T): T {
        val previousQueue = CollectionManager.setTestDispatcher(StandardTestDispatcher(testScheduler), useReentrantLock = false)
        try {
            return block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    private fun ManageNotetypes.touchesBlocked() = window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0

    companion object {
        private const val TEST_NOTE_TYPE_NAME = "BasicTestNoteType1"
    }
}
