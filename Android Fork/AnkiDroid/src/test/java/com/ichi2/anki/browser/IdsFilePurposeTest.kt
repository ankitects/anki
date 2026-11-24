// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdsFilePurposeTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `purposes preserve existing snapshot prefixes`() {
        val expectedPrefixes =
            mapOf(
                IdsFile.Purpose.SELECTION to "multiselect-values",
                IdsFile.Purpose.FIND_AND_REPLACE to "find-replace",
                IdsFile.Purpose.TAGS to "ids",
                IdsFile.Purpose.PREVIEW to "ids",
                IdsFile.Purpose.EXPORT to "export",
                IdsFile.Purpose.SET_DUE_DATE to "set-due-date",
            )
        assertEquals(IdsFile.Purpose.entries.toSet(), expectedPrefixes.keys)
        for ((purpose, prefix) in expectedPrefixes) {
            val file = IdsFile(temporaryFolder.root, listOf(42L), purpose)
            assertTrue(file.name.startsWith(prefix))
            assertTrue(file.name.endsWith(".tmp"))
            assertEquals(listOf(42L), file.getIdsOrNull())
        }
    }
}
