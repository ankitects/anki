// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.provider

import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.database.CursorWindow
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class LazyCursorTest {
    @Test
    fun `large results only load rows when read`() {
        val loaded = mutableListOf<Int>()
        LazyCursor(arrayOf("_id"), 1_000_000) { position, row ->
            loaded.add(position)
            row.addRow(arrayOf(position.toLong()))
        }.use { cursor ->
            assertThat(cursor.count, equalTo(1_000_000))
            assertThat(loaded, empty())
            assertThrows(CursorIndexOutOfBoundsException::class.java) { cursor.getLong(0) }
            assertThat(cursor.moveToFirst(), equalTo(true))
            assertThat(cursor.moveToLast(), equalTo(true))
            assertThat(loaded, empty())
            assertThat(cursor.getLong(0), equalTo(999_999L))
            assertThat(loaded, contains(999_999))
            assertThat(cursor.moveToFirst(), equalTo(true))
            assertThat(cursor.getLong(0), equalTo(0L))
            assertThat(cursor.moveToFirst(), equalTo(true))
            assertThat(loaded, contains(999_999, 0))
            assertThat(cursor.moveToPrevious(), equalTo(false))
            assertThrows(CursorIndexOutOfBoundsException::class.java) { cursor.getLong(0) }
            assertThat(cursor.moveToLast(), equalTo(true))
            assertThat(cursor.getLong(0), equalTo(999_999L))
            assertThat(loaded, contains(999_999, 0, 999_999))
            assertThat(cursor.moveToNext(), equalTo(false))
            assertThrows(CursorIndexOutOfBoundsException::class.java) { cursor.getLong(0) }
        }
    }

    @Test
    fun `empty results never load a row`() {
        LazyCursor(arrayOf("_id"), 0) { _, _ -> error("No rows to load") }.use { cursor ->
            assertThat(cursor.count, equalTo(0))
            assertThat(cursor.moveToFirst(), equalTo(false))
        }
    }

    @Test
    fun `moving releases the previous row`() {
        val rows = mutableListOf<Cursor>()
        LazyCursor(arrayOf("_id"), 2) { position, row ->
            rows.add(row)
            row.addRow(arrayOf(position))
        }.use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
            cursor.moveToNext()
            assertThat(rows[0].isClosed, equalTo(true))
            assertThat(rows, hasSize(1))
            cursor.getInt(0)
            assertThat(rows[1].isClosed, equalTo(false))
            cursor.moveToPrevious()
            assertThat(rows[1].isClosed, equalTo(true))
            assertThat(cursor.getInt(0), equalTo(0))
        }
        assertThat(rows.all { it.isClosed }, equalTo(true))
    }

    @Test
    fun `rows can be copied into a cursor window`() {
        val loaded = mutableListOf<Int>()
        LazyCursor(arrayOf("_id", "text", "nullable", "real"), 3) { position, row ->
            loaded.add(position)
            row.addRow(arrayOf<Any?>(position.toLong(), "row $position", null, 1.5))
        }.use { cursor ->
            cursor.moveToFirst()
            CursorWindow("test").use { window ->
                cursor.fillWindow(1, window)
                assertThat(loaded, contains(1, 2))
                assertThat(cursor.position, equalTo(0))
                assertThat(window.numRows, equalTo(2))
                assertThat(window.getLong(1, 0), equalTo(1L))
                assertThat(window.getString(2, 1), equalTo("row 2"))
                assertThat(window.getType(1, 2), equalTo(Cursor.FIELD_TYPE_NULL))
                assertThat(window.getDouble(1, 3), equalTo(1.5))
            }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun `results spanning multiple windows retain every row`() {
        val loaded = mutableListOf<Int>()
        val payload = "x".repeat(1024)
        LazyCursor(arrayOf("_id", "text"), 10) { position, row ->
            loaded.add(position)
            row.addRow(arrayOf<Any>(position, "$position:$payload"))
        }.use { cursor ->
            cursor.moveToLast()
            val originalPosition = cursor.position
            // Each row fits, but the complete result cannot fit in this window.
            CursorWindow("small", 4096).use { window ->
                var nextPosition = 0
                var windowCount = 0
                while (nextPosition < cursor.count) {
                    cursor.fillWindow(nextPosition, window)
                    assertThat(cursor.position, equalTo(originalPosition))
                    assertThat(window.startPosition, equalTo(nextPosition))
                    assertThat("The window must hold at least one row", window.numRows, greaterThan(0))
                    if (windowCount == 0) {
                        assertThat("The results must span multiple windows", window.numRows, lessThan(cursor.count))
                        assertThat("Filling the first window must not load the last row", loaded, not(hasItem(cursor.count - 1)))
                    }
                    for (position in nextPosition until nextPosition + window.numRows) {
                        assertThat(window.getInt(position, 0), equalTo(position))
                        assertThat(window.getString(position, 1), equalTo("$position:$payload"))
                    }
                    nextPosition += window.numRows
                    windowCount++
                }
                assertThat(nextPosition, equalTo(cursor.count))
                assertThat(windowCount, greaterThan(1))

                // Refill a previous window after reading the entire result.
                cursor.fillWindow(0, window)
                assertThat(cursor.position, equalTo(originalPosition))
                assertThat(window.startPosition, equalTo(0))
                assertThat(window.numRows, greaterThan(0))
                assertThat(window.getInt(0, 0), equalTo(0))
                assertThat(window.getString(0, 1), equalTo("0:$payload"))
            }
        }
    }

    @Test
    fun `closing releases the loaded row`() {
        var loadedRow: Cursor? = null
        val cursor =
            LazyCursor(arrayOf("_id"), 1) { _, row ->
                loadedRow = row
                row.addRow(arrayOf(1L))
            }
        cursor.moveToFirst()
        cursor.getLong(0)
        cursor.close()
        assertThat(cursor.isClosed, equalTo(true))
        assertThat(loadedRow!!.isClosed, equalTo(true))
        assertThrows(IllegalStateException::class.java) { cursor.getLong(0) }
    }

    @Test
    fun `closing an unread cursor prevents loading`() {
        val cursor = LazyCursor(arrayOf("_id"), 1) { _, _ -> error("Cursor is closed") }
        cursor.moveToFirst()
        cursor.close()
        assertThrows(IllegalStateException::class.java) { cursor.getLong(0) }
    }

    @Test
    fun `numeric strings use MatrixCursor conversions`() {
        var loads = 0
        LazyCursor(arrayOf("number", "invalid", "nullable"), 1) { _, row ->
            loads++
            row.addRow(arrayOf("123", "not a number", null))
        }.use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getShort(0), equalTo(123.toShort()))
            assertThat(cursor.getInt(0), equalTo(123))
            assertThat(cursor.getLong(0), equalTo(123L))
            assertThat(cursor.getFloat(0), equalTo(123f))
            assertThat(cursor.getDouble(0), equalTo(123.0))
            assertThat(cursor.getType(0), equalTo(Cursor.FIELD_TYPE_STRING))
            assertThrows(NumberFormatException::class.java) { cursor.getInt(1) }
            assertThat(cursor.getInt(2), equalTo(0))
            assertThat(cursor.isNull(2), equalTo(true))
            assertThat(loads, equalTo(1))
        }
    }
}
