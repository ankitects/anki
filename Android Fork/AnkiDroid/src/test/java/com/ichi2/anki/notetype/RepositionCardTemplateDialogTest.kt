// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.getInputField
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class RepositionCardTemplateDialogTest : RobolectricTest() {
    @Test
    fun `only positions in the displayed range are accepted`() {
        var selectedPosition: Int? = null
        RepositionCardTemplateDialog.showInstance(startRegularActivity<AnkiActivity>(), 3) { selectedPosition = it }
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertFalse(dialog.positiveButton.isEnabled)
        for (input in listOf("0", "4", "2147483648", "")) {
            dialog.getInputField().setText(input)
            assertFalse("Invalid input: $input", dialog.positiveButton.isEnabled)
        }
        for (input in listOf("1", "3")) {
            dialog.getInputField().setText(input)
            assertTrue(dialog.positiveButton.isEnabled)
        }
        dialog.positiveButton.performClick()
        advanceRobolectricLooper()
        assertEquals(3, selectedPosition)
    }

    @Test
    fun `cancel does not reposition the template`() {
        RepositionCardTemplateDialog.showInstance(startRegularActivity<AnkiActivity>(), 3) {
            error("Cancel must not reposition the template")
        }
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getInputField().setText("2")
        dialog.negativeButton.performClick()
        advanceRobolectricLooper()
        assertFalse(dialog.isShowing)
    }
}
