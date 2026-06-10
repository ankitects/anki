// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogMessageHandler
import com.ichi2.anki.dialogs.SyncErrorDialog.Type
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncErrorDialogTest {
    @Test
    fun `SyncErrorDialogMessageHandler serialization`() {
        val handler = SyncErrorDialogMessageHandler(Type.DIALOG_MEDIA_SYNC_ERROR, "message")
        val message = handler.toMessage()
        val deserialized = SyncErrorDialogMessageHandler.fromMessage(message)
        assertThat(deserialized.dialogType, equalTo(Type.DIALOG_MEDIA_SYNC_ERROR))
        assertThat(deserialized.dialogMessage, equalTo("message"))
    }
}
