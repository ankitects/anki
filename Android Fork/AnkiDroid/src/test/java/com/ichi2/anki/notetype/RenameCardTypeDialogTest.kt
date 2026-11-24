// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.getInputField
import com.ichi2.utils.positiveButton
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RenameCardTypeDialogTest : RobolectricTest() {
    @Test
    fun `whitespace-only name is rejected`() =
        withRenameDialog {
            getInputField().setText("   ")
            assertFalse(positiveButton.isEnabled, "Rename should be disabled for a blank name")
        }

    @Test
    fun `quotes-only name is rejected`() =
        withRenameDialog {
            getInputField().setText("\"\"")
            assertFalse(positiveButton.isEnabled, "Rename should be disabled for a quotes-only name")

            getInputField().setText(" \" \" ")
            assertFalse(positiveButton.isEnabled, "Rename should be disabled for quotes and whitespace")
        }

    @Test
    fun `valid name is accepted`() {
        var renamedTo: CardTypeName? = null
        withRenameDialog(onRename = { renamedTo = it }) {
            getInputField().setText("  Reverse  ")
            assertTrue(positiveButton.isEnabled, "Rename should be enabled for a valid name")

            positiveButton.performClick()
            assertThat(renamedTo?.value, equalTo("Reverse"))
        }
    }

    private fun withRenameDialog(
        onRename: (CardTypeName) -> Unit = {},
        block: AlertDialog.() -> Unit,
    ) {
        val currentName = CardTypeName.fromString("Card 1")
        RenameCardTypeDialog.showInstance(
            startRegularActivity<AnkiActivity>(),
            prefill = currentName.value,
            currentName = currentName,
            existingNames = listOf(currentName),
            block = onRename,
        )
        block(ShadowDialog.getLatestDialog() as AlertDialog)
    }
}
