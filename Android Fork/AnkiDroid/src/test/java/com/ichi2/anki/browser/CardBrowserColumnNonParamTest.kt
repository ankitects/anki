// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertContentEquals

/** @see CardBrowserColumn */
class CardBrowserColumnNonParamTest : JvmTest() {
    @Test
    fun `all keys are documented`() {
        // meta test - the column keys aren't documented well
        // this ensures the columns are greppable in the code
        val ankiColumnKeys =
            col.backend
                .allBrowserColumns()
                .map { it.key }
                .sorted()
        val ourKeys = CardBrowserColumn.entries.map { it.ankiColumnKey }.sorted()

        assertContentEquals(ankiColumnKeys, ourKeys)
    }
}
