// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.appcompat.widget.Toolbar
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.DialogScreenshotTest
import com.ichi2.anki.R
import org.junit.Test

class LocaleSelectionDialogScreenshotTest : DialogScreenshotTest() {
    @Test
    fun `locale selection and search`(
        @TestParameter landscape: Boolean,
    ) {
        val name = setOrientation(landscape)
        withDialog(LocaleSelectionDialog()) { fragment ->
            captureScreen(name)

            val dialog = fragment.requireDialog()
            val toolbar = dialog.findViewById<Toolbar>(R.id.locale_dialog_selection_toolbar)
            captureSearchResults(
                name,
                list = dialog.findViewById(R.id.locale_dialog_selection_list),
                searchItem = toolbar.menu.findItem(R.id.locale_dialog_action_search),
                query = "English",
            )
        }
    }
}
