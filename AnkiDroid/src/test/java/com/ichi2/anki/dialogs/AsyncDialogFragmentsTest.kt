/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
