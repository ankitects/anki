/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package net.ankiweb.rsdroid

import android.content.ContentValues
import android.database.Cursor
import androidx.annotation.CheckResult
import net.ankiweb.rsdroid.ankiutil.DatabaseUtil.queryScalar
import net.ankiweb.rsdroid.ankiutil.InstrumentedTest
import net.ankiweb.rsdroid.database.RustSupportSQLiteDatabase
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

class RustDatabaseIntegrationTests : InstrumentedTest() {
    @Test
    fun testMoveToFirst() {
        val query = "select count(), sum(time)/1000 from revlog where id > 100"
        var cur: Cursor? = null
        try {
            cur = database.query(query, emptyArray())
            cur.moveToFirst()
            val count = cur.getInt(0)
            val minutes = Math.round(cur.getInt(1) / 60.0).toInt()
            MatcherAssert.assertThat(count, Matchers.`is`(0))
            MatcherAssert.assertThat("null is converted to 0 implicitly", minutes, Matchers.`is`(0))
        } finally {
            if (cur != null && !cur.isClosed) {
                cur.close()
            }
        }
    }

    @Test
    fun testInsert() {
        val database = database
        database.query("Create table test (id int)")
        val ret = database.insertForForId("insert into test (id) values (3)", null)

        // first row inserted is 1.
        MatcherAssert.assertThat(ret, Matchers.`is`(1L))
        val ret2 = database.insertForForId("insert into test (id) values (2)", null)
        MatcherAssert.assertThat(ret2, Matchers.`is`(2L))
    }

    @Test
    fun testUpdate() {
        val database = database
        database.query("Create table test (id int)")
        database.insertForForId("insert into test (id) values (3)", null)
        database.insertForForId("insert into test (id) values (4)", null)
        database.insertForForId("insert into test (id) values (5)", null)
        val values = ContentValues()
        values.put("id", 2)
        val ret2 = database.update("test", 0, values, "id <> 4", null)
        MatcherAssert.assertThat(ret2, Matchers.`is`(2))
        val result = queryScalar(database, "select count(*) from test where id = 2")
        MatcherAssert.assertThat(result, Matchers.`is`(2))
    }

    @get:CheckResult
    private val database: RustSupportSQLiteDatabase
        get() =
            try {
                val backendV1 = getBackend(fileName)
                RustSupportSQLiteDatabase(backendV1)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

    companion object {
        const val fileName = "initial_version_2_12_1.anki2"
    }
}
