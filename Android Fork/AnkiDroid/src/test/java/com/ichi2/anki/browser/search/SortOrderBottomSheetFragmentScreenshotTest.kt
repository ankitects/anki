// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.os.Bundle
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.ScreenshotTest.DeviceConfig.TABLET
import com.ichi2.anki.ScreenshotTest.IncludeDevices
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.createCardBrowserViewModel
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.Companion.ARG_CURRENT_SORT_TYPE
import com.ichi2.anki.model.SortType
import com.ichi2.anki.testutils.SingleViewModelFactory
import com.ichi2.testutils.launchFragment
import com.ichi2.testutils.layoutForFullHeightScreenshot
import com.ichi2.testutils.scrollToEnd
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshots of the [display order dialog][SortOrderBottomSheetFragment]
 */
@IncludeDevices(TABLET)
class SortOrderBottomSheetFragmentScreenshotTest : ScreenshotTest() {
    @Test
    fun `display order`(
        @TestParameter("port", "land") orientation: String,
    ) {
        RuntimeEnvironment.setQualifiers("+$orientation")
        val viewModelFactory = SingleViewModelFactory.create(createCardBrowserViewModel())
        val args =
            Bundle().apply {
                putParcelable(ARG_CURRENT_SORT_TYPE, SortType.CollectionOrdering(BrowserColumnKey("noteFld"), reverse = false))
            }
        launchFragment(args) { SortOrderBottomSheetFragment(viewModelFactory) }.use { scenario ->
            scenario.onFragment { fragment ->
                advanceRobolectricLooper()
                val name = if (orientation == "land") "landscape" else "portrait"
                captureScreen(name)

                if (device == DeviceConfig.PHONE) {
                    fragment.binding.list.scrollToEnd()
                    captureScreen("${name}_scrolled_to_bottom")

                    fragment.binding.list.scrollToPosition(0)
                    advanceRobolectricLooper()
                    captureView("${name}_scrollshot", fragment.layoutForFullHeightScreenshot(fragment.binding.list))
                }
            }
        }
    }
}
