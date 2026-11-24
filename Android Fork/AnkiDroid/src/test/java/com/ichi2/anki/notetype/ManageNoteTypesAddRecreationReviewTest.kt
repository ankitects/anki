// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ManageNoteTypesAddRecreationReviewTest : RobolectricTest() {
    @Test
    fun `night mode change after stock add preserves the pending list refresh`() = checkRecreation(clone = false)

    @Test
    fun `night mode change after clone preserves the pending list refresh`() = checkRecreation(clone = true)

    private fun checkRecreation(clone: Boolean) =
        runTest {
            val collection = col
            val sourceName = "Rotation source"
            if (clone) addStandardNoteType(sourceName, arrayOf("front", "back"), "{{front}}", "{{back}}")
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(ManageNotetypes::class.java, Intent())
            val activity = controller.get()
            val viewModel = activity.viewModel
            activity.lifecycleScope.launch { AddNewNotesType(activity).showAddNewNotetypeDialog() }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            if (clone) {
                val selector = dialog.newNoteTypeBinding.notetypeNewType
                val index = (0 until selector.count).single { selector.getItemAtPosition(it).toString().contains(sourceName) }
                selector.setSelection(index)
                ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            }
            val newName = if (clone) "Created clone" else "Created stock"
            dialog.newNoteTypeBinding.notetypeNewName.setText(newName)
            val queue = StepDispatcher()
            val previousQueue = CollectionManager.setTestDispatcher(queue, useReentrantLock = false)
            try {
                assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick())
                advanceRobolectricLooper()
                queue.runNext() // Persist the note type; leave the list read queued.
                advanceRobolectricLooper()
                assertNotNull(collection.notetypes.byName(newName), "creation must finish before the configuration change")
                assertTrue(queue.hasTasks, "the list reload must still be pending")
                assertTrue(
                    viewModel.state.value.noteTypes
                        .none { it.name == newName },
                )

                // Synchronous activity startup may inspect CollectionManager. Keep only the already
                // queued list read delayed while recreating the activity with its retained ViewModel.
                CollectionManager.setTestDispatcher(queue)
                val changedConfiguration =
                    Configuration(activity.resources.configuration).apply {
                        val nightMode =
                            if (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                                Configuration.UI_MODE_NIGHT_NO
                            } else {
                                Configuration.UI_MODE_NIGHT_YES
                            }
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
                    }
                controller.configurationChange(changedConfiguration)
                advanceRobolectricLooper()
                assertNotSame(activity, controller.get(), "night mode must recreate the activity")
                assertSame(viewModel, controller.get().viewModel, "configuration changes must retain the ViewModel")
                queue.runAll()
                advanceRobolectricLooper()

                assertTrue(
                    viewModel.state.value.noteTypes
                        .any { it.name == newName },
                    "the newly created note type must reach the retained list after the configuration change",
                )
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
                queue.runAll()
            }
        }

    private class StepDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        val hasTasks: Boolean get() = tasks.isNotEmpty()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            tasks.addLast(block)
        }

        fun runNext() {
            check(tasks.isNotEmpty())
            tasks.removeFirst().run()
        }

        fun runAll() {
            while (tasks.isNotEmpty()) runNext()
        }
    }
}
