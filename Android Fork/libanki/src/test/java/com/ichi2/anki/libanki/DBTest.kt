// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import net.ankiweb.rsdroid.BackendException
import org.junit.Test
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DBTest : InMemoryAnkiTest() {
    @Test
    fun `scalar values preserve 64 bit precision and SQLite numeric conversions`() {
        assertEquals(Long.MIN_VALUE, col.db.queryLongScalar("select ?", Long.MIN_VALUE))
        assertEquals(Long.MAX_VALUE, col.db.queryLongScalar("select ?", Long.MAX_VALUE))
        assertEquals(-42L, col.db.queryLongScalar("select '  -42tail'"))
        assertEquals(0L, col.db.queryLongScalar("select 'not a number'"))
        assertEquals(-9L, col.db.queryLongScalar("select -9.75"))
        assertEquals(0L, col.db.queryLongScalar("select null"))
        assertEquals(0, col.db.queryScalar("select 1 where 0"))
        assertEquals(0L, col.db.queryLongScalar("select 1 where 0"))
        assertNoCachedQueries()
    }

    @Test
    fun `strings and nullable arguments round trip without Android`() {
        val text = "quote\" slash/line\n🎴"
        assertEquals(text, col.db.queryString("select ?", text))
        assertEquals("42", col.db.queryString("select 42"))
        assertEquals("1.25", col.db.queryString("select 1.25"))
        col.db.query("select ?, ?, ?", null, "2.5", 1.25).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertNull(cursor.getString(0))
            assertEquals(0.0, cursor.getDouble(0))
            assertEquals(2.5, cursor.getDouble(1))
            assertEquals(1.25, cursor.getDouble(2))
        }
        assertNoCachedQueries()
    }

    @Test
    fun `empty queries retain scalar and list behavior`() {
        col.db.query("select 1 where 0").use { cursor ->
            assertEquals(0, cursor.count)
            assertFalse(cursor.moveToFirst())
            assertFalse(cursor.moveToNext())
        }
        assertEquals(emptyList(), col.db.queryLongList("select 1 where 0"))
        assertEquals(emptyList(), col.db.queryStringList("select 'x' where 0"))
        assertFailsWith<SQLException> { col.db.queryString("select 'x' where 0") }
        assertNoCachedQueries()
    }

    @Test
    fun `cursor and list queries read every backend page`() =
        withSmallPages {
            val sql = "with recursive n(x) as (select 1 union all select x + 1 from n where x < 300) select x from n"
            col.db.query(sql).use { cursor ->
                assertEquals(300, cursor.count)
                val values = mutableListOf<Long>()
                while (cursor.moveToNext()) values.add(cursor.getLong(0))
                assertEquals((1L..300L).toList(), values)
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            assertEquals((1L..300L).toList(), col.db.queryLongList(sql))
            assertEquals((1..300).map { it.toString() }, col.db.queryStringList(sql))
            assertNoCachedQueries()
        }

    @Test
    fun `partially read cursors release cached pages and close is idempotent`() =
        withSmallPages {
            val cursor = col.db.query("select 1 union all select 2 union all select 3")
            assertTrue(cursor.moveToFirst())
            assertTrue(col.backend.getActiveSequenceNumbers().isNotEmpty())
            cursor.close()
            cursor.close()
            assertTrue(cursor.isClosed)
            assertFailsWith<IllegalStateException> { cursor.moveToNext() }
            assertFailsWith<IllegalStateException> { cursor.getLong(0) }
            assertNoCachedQueries()
            assertEquals(7, col.db.queryScalar("select 7"))
        }

    @Test
    fun `conversion failures also release cached queries`() {
        assertFailsWith<SQLException> { col.db.queryLongScalar("select x'0080ff'") }
        assertNoCachedQueries()
        assertFailsWith<SQLException> { col.db.queryStringList("select x'0080ff'") }
        assertNoCachedQueries()
    }

    @Test
    fun `cursor reads require a current row`() {
        col.db.query("select 1").use { cursor ->
            assertFailsWith<IllegalStateException> { cursor.getLong(0) }
            assertTrue(cursor.moveToNext())
            assertEquals(1, cursor.getInt(0))
            assertFalse(cursor.moveToNext())
            assertFailsWith<IllegalStateException> { cursor.getLong(0) }
        }
    }

    @Test
    fun `execute binds values and closes statement results`() {
        col.db.executeScript("create table jvm_test (value integer); insert into jvm_test values (1);")
        col.db.execute("update jvm_test set value = ?", 42)
        assertEquals(42, col.db.queryScalar("select value from jvm_test"))
        col.db.execute("select value from jvm_test")
        assertNoCachedQueries()
    }

    @Test
    fun `database wrapper does not own the collection connection`() {
        col.db.close()
        assertEquals(0, col.noteCount())
        addBasicNote()
        assertEquals(1, col.noteCount())
    }

    @Test
    fun `database errors retain the backend exception type`() {
        assertFailsWith<BackendException> { col.db.queryScalar("select * from missing_table") }
        assertEquals(1, col.db.queryScalar("select 1"))
        assertNoCachedQueries()
    }

    private fun assertNoCachedQueries() {
        assertTrue(col.backend.getActiveSequenceNumbers().isEmpty())
    }

    private fun withSmallPages(block: () -> Unit) {
        col.backend.setPageSize(9)
        try {
            block()
        } finally {
            col.backend.setPageSize(2 * 1024 * 1024)
        }
    }
}
