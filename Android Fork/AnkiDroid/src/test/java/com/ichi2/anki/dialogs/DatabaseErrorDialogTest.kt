// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DatabaseErrorDialog.ShowDatabaseErrorDialog
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseErrorDialogTest {
    @Test
    fun `ShowDatabaseErrorDialog serialization`() {
        // concerns with 'Bundle()' + '@Parcelize'
        val error = ShowDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_ERROR)
        val message = error.toMessage()
        val deserialized = ShowDatabaseErrorDialog.fromMessage(message)
        assertThat(deserialized.dialogType, equalTo(DatabaseErrorDialogType.DIALOG_DB_ERROR))
    }

    @Test
    fun `ShowDatabaseErrorDialog keeps the exception data`() {
        val error = ShowDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_ERROR_HANDLING, CustomExceptionData("stack trace"))
        val deserialized = ShowDatabaseErrorDialog.fromMessage(error.toMessage())
        assertThat(deserialized.exceptionData?.toHumanReadableString(), equalTo("stack trace"))
    }
}
