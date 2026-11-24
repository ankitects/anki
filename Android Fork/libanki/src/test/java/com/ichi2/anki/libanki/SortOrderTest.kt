// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.SortOrder.AfterSqlOrderBy
import com.ichi2.anki.libanki.SortOrder.BuiltinColumnSortKind
import com.ichi2.anki.libanki.SortOrder.NoOrdering
import com.ichi2.anki.libanki.SortOrder.UseCollectionOrdering
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import org.junit.Test
import kotlin.test.assertEquals

class SortOrderTest : InMemoryAnkiTest() {
    @Test
    fun `NoOrdering toString`() {
        assertEquals("NoOrdering", NoOrdering.toString())
    }

    @Test
    fun `UseCollectionOrdering toString`() {
        assertEquals("UseCollectionOrdering", UseCollectionOrdering.toString())
    }

    @Test
    fun `AfterSqlOrderBy toString`() {
        assertEquals(
            "AfterSqlOrderBy(customOrdering=c.ivl asc, c.due desc)",
            AfterSqlOrderBy("c.ivl asc, c.due desc").toString(),
        )
    }

    @Test
    fun `BuiltinColumnSortKind toString`() {
        assertEquals(
            "BuiltinColumnSortKind(column=cardDue, reverse=true)",
            BuiltinColumnSortKind(col.getBrowserColumn("cardDue")!!, reverse = true).toString(),
        )
    }
}
