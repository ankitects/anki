// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.scheduler.CongratsInfoResponse
import com.ichi2.anki.dialogs.DeckPickerContextMenu
import com.ichi2.anki.libanki.DeckId
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowPopupMenu
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class DeckPickerContextMenuReadErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `long press menu can be reopened after a read fails`() =
        assertMenuRecovers(
            requestMenu = { deckId -> viewModel.requestContextMenu(deckId) },
            isMenuShowing = {
                supportFragmentManager.fragments.filterIsInstance<DeckPickerContextMenu>().any { it.dialog?.isShowing == true }
            },
        )

    @Test
    fun `right click menu can be reopened after a read fails`() =
        assertMenuRecovers(
            requestMenu = { deckId -> viewModel.requestRightClickContextMenu(deckId, 10f, 20f) },
            isMenuShowing = { ShadowPopupMenu.getLatestPopupMenu()?.let { shadowOf(it).isShowing } == true },
        )

    private fun assertMenuRecovers(
        requestMenu: DeckPicker.(DeckId) -> Unit,
        isMenuShowing: DeckPicker.() -> Boolean,
    ) {
        val failure = BackendInvalidInputException(backendError { message = "Could not read buried cards" })
        val failNextRead = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun congratsInfo(): CongratsInfoResponse {
                    if (failNextRead.getAndSet(false)) throw failure
                    return super.congratsInfo()
                }
            }
        }
        val deckId = addDeck("Test deck")
        withDeckPicker(deckCount = 0) { picker ->
            picker.awaitStartup()
            throwOnShowError = false
            failNextRead.set(true)
            picker.requestMenu(deckId)
            advanceRobolectricLooperUntil { !failNextRead.get() && ShadowDialog.getLatestDialog()?.isShowing == true }

            assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
            assertFalse(picker.isMenuShowing())
            ShadowDialog.getLatestDialog().dismiss()

            picker.requestMenu(deckId)
            advanceRobolectricLooperUntil { picker.isMenuShowing() }
        }
    }
}
