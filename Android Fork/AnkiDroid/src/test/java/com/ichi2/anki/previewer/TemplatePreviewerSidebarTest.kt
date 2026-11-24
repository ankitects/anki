// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.toIntent
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TemplatePreviewerSidebarTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "sw600dp-w1000dp-h800dp-land")
    fun `editing sparse clozes keeps the live card selected in sidebar`() =
        runTest {
            val note =
                addClozeNote("{{c1::first}} {{c3::third}}")
                    .update { setItem("Text", "first {{c3::third}}") }
            val activity =
                startRegularActivity<NoteEditorActivity>(
                    NoteEditorDestination.EditNoteFromPreviewer(note.cards(col).single { it.ord == 2 }.id).toIntent(),
                )
            assertTrue(activity.isPreviewerVisible)
            activity.loadNoteEditorPreviewer(forceReplace = true)
            activity.supportFragmentManager.executePendingTransactions()
            advanceRobolectricLooper()
            advanceUntilIdle()

            fun preview() = activity.supportFragmentManager.findFragmentById(R.id.previewer_frame) as TemplatePreviewerFragment
            val initial = preview()
            assertEquals(2, initial.viewModel.ordFlow.value, "initial live card")

            activity.noteEditorFragment.setFieldValueFromUi(0, "first {{c3::third edited}}")
            advanceUntilIdle()
            activity.supportFragmentManager.executePendingTransactions()
            advanceRobolectricLooper()
            advanceUntilIdle()
            val refreshed = preview()
            assertNotSame(initial, refreshed, "text change must reload the preview")
            assertEquals(2, refreshed.viewModel.ordFlow.value, "editing must not jump to the empty card 1")
        }
}
