// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.dialogs

import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.ScreenshotTest
import org.junit.Test
import org.robolectric.Robolectric.buildActivity

class CardSideSelectionDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun testCardSideSelectionDialogAppearance() {
        buildActivity(FragmentActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            CardSideSelectionDialog.displayInstance(activity) {}
            captureScreen("dialog")
        }
    }
}
