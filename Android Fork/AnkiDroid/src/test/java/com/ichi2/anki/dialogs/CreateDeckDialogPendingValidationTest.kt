// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.CreateDeckDialog.DeckDialogType
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.withDeckPicker
import com.ichi2.utils.getInputField
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CreateDeckDialogPendingValidationTest : RobolectricTest() {
    @Test
    fun `confirming before name validation finishes must not select an existing deck`() {
        addDeck("Existing")
        withDeckPicker(deckCount = 0) { picker ->
            advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
            var selectedDeck: DeckId? = null
            val creator = CreateDeckDialog(picker, "Create deck", DeckDialogType.DECK, null)
            creator.onNewDeckCreated = { selectedDeck = it }
            val dialog = creator.showDialog()
            val queue = StandardTestDispatcher()
            val previousQueue = CollectionManager.setTestDispatcher(queue, useReentrantLock = false)
            try {
                dialog.input = "Existing"
                // A quick keyboard confirmation can arrive while collection access is queued.
                dialog.getInputField().onEditorAction(EditorInfo.IME_ACTION_DONE)
                queue.scheduler.advanceUntilIdle()

                assertNull(selectedDeck, "A pending duplicate-name check must not allow selecting the existing deck")
            } finally {
                queue.scheduler.advanceUntilIdle()
                CollectionManager.setTestDispatcher(previousQueue)
                dialog.dismiss()
            }
        }
    }
}
