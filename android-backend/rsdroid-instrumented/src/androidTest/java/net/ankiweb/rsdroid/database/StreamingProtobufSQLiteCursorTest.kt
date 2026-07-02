/*
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ankiweb.rsdroid.database

import androidx.sqlite.db.SupportSQLiteDatabase
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.DatabaseIntegrationTests
import net.ankiweb.rsdroid.ankiutil.InstrumentedTest
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase.Companion.withRustBackend
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import timber.log.Timber
import java.io.IOException

class StreamingProtobufSQLiteCursorTest : InstrumentedTest() {
    @Test
    @Throws(IOException::class)
    fun testPaging() {
        super.getBackend("initial_version_2_12_1.anki2").use { backend ->
            val db = getWritableDatabase(backend)
            db.execSQL("create table tmp (id int)")
            for (i in 0..998) {
                db.execSQL("insert into tmp VALUES (?)", arrayOf<Any>(i))
            }
            checkValueAtRowEqualsRowNumForwards(db) // 999
            db.execSQL("insert into tmp VALUES (?)", arrayOf<Any>(999))
            checkValueAtRowEqualsRowNumForwards(db) // 1000
            db.execSQL("insert into tmp VALUES (?)", arrayOf<Any>(1000))
            checkValueAtRowEqualsRowNumForwards(db) // 1001
        }
    }

    private fun getWritableDatabase(backend: Backend): SupportSQLiteDatabase = withRustBackend(backend)

    @Test
    @Throws(IOException::class)
    fun testBackwards() {
        Timber.w("This is much slower than forwards")
        super.getBackend("initial_version_2_12_1.anki2").use { backend ->
            val db = getWritableDatabase(backend)
            db.execSQL("create table tmp (id int)")
            for (i in 0..998) {
                db.execSQL("insert into tmp VALUES (?)", arrayOf<Any>(i))
            }
            checkValueAtRowEqualsRowNumBackwards(db) // 999
            db.execSQL("insert into tmp VALUES (?)", arrayOf<Any>(999))
            checkValueAtRowEqualsRowNumBackwards(db) // 1000
            db.execSQL("insert into tmp VALUES (?)", arrayOf<Any>(1000))
            checkValueAtRowEqualsRowNumBackwards(db) // 1001
            db.query("select * from tmp").use { c ->
                c.moveToLast()
                MatcherAssert.assertThat(c.getLong(0), Matchers.`is`(1000L))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun moveToPositionStart() {
        super.getBackend("initial_version_2_12_1.anki2").use { backend ->
            val db = getWritableDatabase(backend)
            db.execSQL("create table tmp (id int)")
            for (i in 0..998) {
                db.execSQL("insert into tmp VALUES (?)", arrayOf<Any>(i))
            }
            val c = db.query("select * from tmp")
            MatcherAssert.assertThat(c.position, Matchers.`is`(-1))
            c.moveToNext()
            MatcherAssert.assertThat(c.position, Matchers.`is`(0))
            val firstValue = c.getLong(0)

            // move away
            c.moveToLast()

            // reset: what we're testing
            c.moveToPosition(-1)
            MatcherAssert.assertThat(c.position, Matchers.`is`(-1))
            c.moveToNext()
            MatcherAssert.assertThat(c.position, Matchers.`is`(0))
            MatcherAssert.assertThat(
                "values haven't changed",
                c.getLong(0),
                Matchers.`is`(firstValue),
            )
        }
    }

    private fun checkValueAtRowEqualsRowNumForwards(db: SupportSQLiteDatabase) {
        var position: Long = 0
        db.query("SELECT * from tmp").use { cur ->
            while (cur.moveToNext()) {
                MatcherAssert.assertThat(cur.getLong(0), Matchers.`is`(position))
                position++
            }
        }
    }

    private fun checkValueAtRowEqualsRowNumBackwards(db: SupportSQLiteDatabase) {
        db.query("SELECT * from tmp").use { cur ->
            var position = (cur.count - 1).toLong()
            cur.moveToLast()
            MatcherAssert.assertThat(cur.position.toLong(), Matchers.`is`(position))
            while (cur.moveToPrevious()) {
                position--
                MatcherAssert.assertThat(cur.getLong(0), Matchers.`is`(position))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCorruptionIsHandled() {
        val elements = DatabaseIntegrationTests.DB_PAGE_NUM_INT_ELEMENTS
        super.getBackend("initial_version_2_12_1.anki2").use { backend ->
            val db = getWritableDatabase(backend)
            db.execSQL("create table tmp (id int)")
            for (i in 0 until elements + 1) {
                db.execSQL("insert into tmp (id) values (?)", arrayOf<Any>(i))
            }
            db.query("select * from tmp order by id asc").use { c1 ->
                for (i in 0 until elements) {
                    Timber.d("start %d", i)
                    c1.moveToNext()
                    MatcherAssert.assertThat(c1.getInt(0), Matchers.`is`(i))
                    Timber.d("end %d", i)
                }
                db.query("select id + 5 from tmp order by id asc").use { c2 ->
                    for (i in 0 until elements) {
                        c2.moveToNext()
                        MatcherAssert.assertThat(c2.getInt(0), Matchers.`is`(i + 5))
                    }
                    c1.moveToNext()

                    // This should fail as we've overwritten the cache.
                    MatcherAssert.assertThat(c1.getInt(0), Matchers.`is`(elements))
                }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun smallQueryHasOneCount() {
        val elements = 30 // 465
        super.getBackend("initial_version_2_12_1.anki2").use { backend ->
            val db = getWritableDatabase(backend)
            db.execSQL("create table tmp (id varchar)")
            for (i in 0 until elements + 1) {
                val inputOfLength = String(CharArray(elements)).replace("\u0000", "a")
                db.execSQL("insert into tmp (id) values (?)", arrayOf<Any>(inputOfLength))
            }
            TestCursor(backend, "select * from tmp", arrayOf<Any?>()).use { c1 ->
                val sizes: MutableSet<Int> = HashSet()
                while (c1.moveToNext()) {
                    check(!(sizes.add(c1.currentSliceRowCount) && sizes.size > 1)) { "Expected single size of results" }
                }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun variableLengthStringsReturnDifferentRowCounts() {
        val elements = 50 // 1275 > 1000
        super.getBackend("initial_version_2_12_1.anki2").use { backend ->
            val db = getWritableDatabase(backend)
            db.execSQL("create table tmp (id varchar)")
            for (i in 0 until elements + 1) {
                val inputOfLength = String(CharArray(elements)).replace("\u0000", "a")
                db.execSQL("insert into tmp (id) values (?)", arrayOf<Any>(inputOfLength))
            }
            TestCursor(backend, "select * from tmp", arrayOf<Any?>()).use { c1 ->
                val sizes: MutableSet<Int> = HashSet()
                while (c1.moveToNext()) {
                    if (sizes.add(c1.currentSliceRowCount) && sizes.size > 1) {
                        return
                    }
                }
                throw IllegalStateException("Expected multiple sizes of results")
            }
        }
    }

    private class TestCursor(
        backend: SQLHandler?,
        query: String?,
        bindArgs: Array<Any?>,
    ) : StreamingProtobufSQLiteCursor(
            backend!!,
            query!!,
            bindArgs,
        )
}
