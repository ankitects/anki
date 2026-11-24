// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Looper
import com.ichi2.anki.CommonString
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.ScreenshotTest.DeviceConfig.TABLET
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.withDeckPicker
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.ext.snackbarText
import org.junit.After
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ScreenshotTest.IncludeDevices(TABLET)
class CustomStudyScreenshotTest : ScreenshotTest() {
    @After
    fun resetBackups() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `hidden Default`() =
        runTest {
            val other = addDeck("Other")
            col.setDeck(listOf(addBasicNote().firstCard().id), other)
            assertNull(col.sched.deckTree(includeCounts = false).find(DEFAULT_DECK_ID))

            withDeckPicker(deckCount = 0) { activity ->
                activity.showDialogFragment(CustomStudyDialog.createInstance(DEFAULT_DECK_ID))
                // Complete the entrance animation without advancing through the snackbar timeout.
                shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
                assertEquals(activity.getString(CommonString.empty_deck), activity.snackbarText)
                activity.supportFragmentManager.executePendingTransactions()
                assertNull(activity.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

                captureScreen("unavailable_deck")
            }
        }
}
