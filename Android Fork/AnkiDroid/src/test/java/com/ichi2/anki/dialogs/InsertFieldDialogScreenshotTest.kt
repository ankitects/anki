// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.dialogs.InsertFieldDialog.SelectSpecialFieldFragment
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Tab
import com.ichi2.anki.withCardTemplateEditor
import com.ichi2.testutils.scrollToEnd
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.testutils.windowInsetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class InsertFieldDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun portrait() =
        captureInsertField(
            "portrait",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp) },
        )

    @Test
    fun gestureNavigation() =
        captureInsertField(
            "gesture_navigation",
            with(targetContext) { windowInsetsOf(navBarBottom = 24.dp) },
        )

    @Test
    fun portraitCutout() =
        captureInsertField(
            "portrait_cutout",
            with(targetContext) { windowInsetsOf(navBarBottom = 48.dp, cutoutTop = 40.dp) },
        )

    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        captureInsertField(
            "landscape",
            with(targetContext) { windowInsetsOf(navBarRight = 48.dp, cutoutLeft = 32.dp) },
        )
    }

    private fun captureInsertField(
        name: String,
        insets: WindowInsetsCompat,
    ) = withCardTemplateEditor {
        currentFragment!!.showInsertFieldDialog()
        advanceRobolectricLooper()
        val dialog = supportFragmentManager.fragments.filterIsInstance<InsertFieldDialog>().single()
        try {
            val window = dialog.requireDialog().window!!
            window.simulateSystemBars(insets)
            captureScreen(name)

            dialog.requireView().findViewById<ViewPager2>(R.id.view_pager).setCurrentItem(Tab.SPECIAL.position, false)
            advanceRobolectricLooper()
            ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)
            dialog.childFragmentManager.fragments
                .filterIsInstance<SelectSpecialFieldFragment>()
                .single()
                .binding.root
                .scrollToEnd()
            captureScreen("${name}_special_scrolled_to_bottom")
        } finally {
            dialog.dismissNow()
        }
    }
}
