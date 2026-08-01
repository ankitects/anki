// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith

/** Test for any [AsyncDialogFragment] instances */
@RunWith(AndroidJUnit4::class)
class AsyncDialogFragmentsTest {
    @Test
    fun `SyncErrorDialog does not require context`() {
        for (dialogType in SyncErrorDialog.Type.entries) {
            val instance = SyncErrorDialog.newInstance(dialogType, dialogMessage = null)

            assertDoesNotThrow("$dialogType message required a context") { instance.notificationMessage }
            assertDoesNotThrow("$dialogType title required a context") { instance.notificationTitle }
        }
    }

    @Test
    fun `DatabaseErrorDialog does not require context`() {
        for (dialogType in DatabaseErrorDialog.DatabaseErrorDialogType.entries) {
            val instance = DatabaseErrorDialog.newInstance(dialogType)
            assertDoesNotThrow("$dialogType message required a context") { instance.notificationMessage }
            assertDoesNotThrow("$dialogType title required a context") { instance.notificationTitle }
        }
    }

    @Test
    fun `ImportDialog does not require context`() {
        for (dialogType in ImportDialog.Type.entries) {
            val instance = ImportDialog.newInstance(dialogType, "path")
            assertDoesNotThrow("$dialogType message required a context") { instance.notificationMessage }
            assertDoesNotThrow("$dialogType title required a context") { instance.notificationTitle }
        }
    }
}
