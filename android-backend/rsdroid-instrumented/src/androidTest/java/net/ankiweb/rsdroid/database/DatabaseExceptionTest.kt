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
package net.ankiweb.rsdroid.database

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import net.ankiweb.rsdroid.database.testutils.DatabaseComparison
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DatabaseExceptionTest : DatabaseComparison() {
    @Test
    fun testBadSqlException() {
        try {
            mDatabase.execSQL("select 2 from b")
        } catch (ex: SQLiteException) {
            // Original: no such table: b (code 1): , while compiling: select 1 from b
            // Rust: error while compiling: select 2 from b: DBError { info: "SqliteFailure(Error { code: Unknown, extended_code: 1 }, Some(\"no such table: b\"))", kind: Other }
            val message = ex.localizedMessage
            MatcherAssert.assertThat(message, CoreMatchers.containsString("no such table: b"))
            MatcherAssert.assertThat(message, CoreMatchers.containsString("1"))
            MatcherAssert.assertThat(message, CoreMatchers.containsString("select 2 from b"))
        }
    }

    @Test
    fun testConstraintViolation() {
        try {
            mDatabase.execSQL("CREATE TABLE test (id int PRIMARY KEY)")
            mDatabase.execSQL("INSERT INTO test (id) VALUES (1)")
            mDatabase.execSQL("INSERT INTO test (id) VALUES (1)")
            MatcherAssert.assertThat("Exception should be thrown", true, CoreMatchers.`is`(false))
        } catch (ex: SQLiteConstraintException) {
            // Java: "column id is not unique (code 19)"
            // Rust: UNIQUE constraint failed: test.id
            // fully: DBError { info: "SqliteFailure(Error { code: ConstraintViolation, extended_code: 1555 }, Some(\"UNIQUE constraint failed: test.id\"))", kind: Other }
            MatcherAssert.assertThat(ex.message, CoreMatchers.containsString("id"))
            MatcherAssert.assertThat(
                ex.message,
                Matchers.anyOf(
                    CoreMatchers.containsString("unique"),
                    CoreMatchers.containsString("UNIQUE"),
                ),
            )
        }
    }

    @Test
    @Ignore("TODO: Not yet handled")
    fun testDatabaseLocked() {
        // required for check database

        // For this test, we need to lock in rsdroid before opening the collection,
        // which is quite a lot of effort due to JNI being harder to write

        // This is broken in-app, but only slightly on an error case.
        // Using the in-app lock, this causes a hang rather than corruption.
        // I suspect the outcome will be different if performed out-of-process - maybe an exception?
        try {
            mDatabase.execSQL("PRAGMA locking_mode = EXCLUSIVE; BEGIN EXCLUSIVE;")
        } catch (ex: SQLiteDatabaseLockedException) {
            // assert
        }
    }
}
