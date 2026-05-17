// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.dialogs

import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity

class CardSideSelectionDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun testCardSideSelectionDialogAppearance() {
        val activity = buildActivity(FragmentActivity::class.java).setup().get()
        CardSideSelectionDialog.displayInstance(activity) {}
        captureScreen("dialog")
    }
}
