/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
