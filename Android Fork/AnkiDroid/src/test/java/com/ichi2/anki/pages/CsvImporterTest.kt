// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.SingleFragmentActivity
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CsvImporterTest : RobolectricTest() {
    @Test
    fun `acknowledging an import error closes the importer`() =
        runTest {
            val activity =
                startActivityNormallyOpenCollectionWithIntent(
                    SingleFragmentActivity::class.java,
                    CsvImporter.getIntent(targetContext, "/missing.csv"),
                )
            val response = activity.handleUiPostRequest("importDialogRequireClose", byteArrayOf())

            assertEquals(UiPostRequestResponse.Handled(byteArrayOf()), response)
            assertTrue(activity.isFinishing)
        }
}
