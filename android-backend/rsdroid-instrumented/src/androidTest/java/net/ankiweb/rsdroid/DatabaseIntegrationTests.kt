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

import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteException
import net.ankiweb.rsdroid.ankiutil.DatabaseUtil.queryScalar
import net.ankiweb.rsdroid.ankiutil.DatabaseUtil.queryScalarFloat
import net.ankiweb.rsdroid.database.testutils.DatabaseComparison
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DatabaseIntegrationTests : DatabaseComparison() {
    @Test
    fun testScalar() {
        val returnValue = queryScalar(mDatabase, "select 2")
        MatcherAssert.assertThat(returnValue, Matchers.`is`(2))
    }

    @Test
    fun testArgs() {
        val returnValue = queryScalar(mDatabase, "select ?", 1)
        MatcherAssert.assertThat(returnValue, Matchers.`is`(1))
    }

    @Test
    fun testNullArgValue() {
        try {
            queryScalar(mDatabase, "select 4", null as Any?)
        } catch (ex: IllegalArgumentException) {
            MatcherAssert.assertThat(
                ex.message,
                Matchers.`is`("Cannot bind argument at index 1 because the index is out of range.  The statement has 0 parameters."),
            )
        }
    }

    @Test
    fun testNullIsCastToZero() {
        // now, we get into the nitty-gritty of SQLite
        val returnValue = queryScalar(mDatabase, "select null")
        MatcherAssert.assertThat(returnValue, Matchers.`is`(0))
    }

    @Test
    fun testNullDoubleIsCastToZero() {
        // now, we get into the nitty-gritty of SQLite
        val returnValue = queryScalarFloat(mDatabase, "select null")
        MatcherAssert.assertThat(returnValue, Matchers.`is`(0.0))
    }

    @Test
    fun testMultipleParameters() {
        mDatabase.query("select ?, ?", arrayOf<Any>(1, 2)).use { returnValue ->
            returnValue.moveToFirst()
            MatcherAssert.assertThat(returnValue.getInt(0), Matchers.`is`(1))
            MatcherAssert.assertThat(returnValue.getInt(1), Matchers.`is`(2))
        }
    }

    @Test
    fun testCursorIndexException() {
        mDatabase.execSQL("create table tmp (id int)")
        mDatabase.execSQL("insert into tmp (id) VALUES (?)", arrayOf<Any>(1))
        mDatabase.query("select * from tmp").use { c ->
            try {
                c.getString(0)
                Assert.fail("no exception thrown")
            } catch (e: CursorIndexOutOfBoundsException) {
                MatcherAssert.assertThat(
                    e.message,
                    Matchers.`is`("Index -1 requested, with a size of 1"),
                )
            }
        }
    }

    @Test
    fun testStringConversions() {
        testStringConversion(SQLOutput.asInt("1"), "1")
        testStringConversion(SQLOutput.asFloat("1.6"), "1.6")
        testStringConversion(SQLOutput.asText("hi"), "hi")
        testStringConversion(SQLOutput.asNull(), null)
    }

    @Test
    fun testFailingStringConversions() {
        try {
            testStringConversion(SQLOutput.asBlob(), "unused")
        } catch (e: SQLiteException) {
            MatcherAssert.assertThat(
                e.message,
                Matchers.isOneOf(
                    "unknown error (code 0): Unable to convert BLOB to string",
                    "unknown error (code 0 SQLITE_OK): Unable to convert BLOB to string",
                ),
            )
        }
    }

    @Test
    fun testIntConversions() {
        testIntConversion(SQLOutput.asInt("1"), 1)
        testIntConversion(SQLOutput.asFloat("1.6"), 1)
        testIntConversion(SQLOutput.asNull(), 0)
        testIntConversion(SQLOutput.asText("hi"), 0)
        testIntConversion(SQLOutput.asText("2"), 2)
        testIntConversion(SQLOutput.asText("2.52"), 2)
    }

    @Test
    fun testFailingIntConversions() {
        try {
            testIntConversion(SQLOutput.asBlob(), 42)
        } catch (e: SQLiteException) {
            MatcherAssert.assertThat(
                e.message,
                Matchers.isOneOf(
                    "unknown error (code 0): Unable to convert BLOB to long",
                    "unknown error (code 0 SQLITE_OK): Unable to convert BLOB to long",
                ),
            )
        }
    }

    @Test
    fun testLongConversions() {
        testLongConversion(SQLOutput.asInt("1"), 1L)
        testLongConversion(SQLOutput.asFloat("1.6"), 1L)
        testLongConversion(SQLOutput.asNull(), 0L)
        testLongConversion(SQLOutput.asText("hi"), 0L)
        testLongConversion(SQLOutput.asText("2"), 2L)
        testLongConversion(SQLOutput.asText("2.52"), 2L)
        testLongConversion(SQLOutput.asInt("9223372036854775808"), Long.MAX_VALUE)
    }

    @Test
    fun testFailingLongConversions() {
        try {
            testLongConversion(SQLOutput.asBlob(), 42L)
        } catch (e: SQLiteException) {
            MatcherAssert.assertThat(
                e.message,
                Matchers.isOneOf(
                    "unknown error (code 0): Unable to convert BLOB to long",
                    "unknown error (code 0 SQLITE_OK): Unable to convert BLOB to long",
                ),
            )
        }
    }

    @Test
    fun testFloatConversions() {
        testFloatConversion(SQLOutput.asInt("1"), 1.0f)
        testFloatConversion(SQLOutput.asFloat("1.6"), 1.6f)
        testFloatConversion(SQLOutput.asNull(), 0.0f)
        testFloatConversion(SQLOutput.asText("hi"), 0.0f)
        testFloatConversion(SQLOutput.asText("2"), 2f)
        testFloatConversion(SQLOutput.asText("2.52"), 2.52f)
    }

    @Test
    fun testFailingFloatConversions() {
        try {
            testFloatConversion(SQLOutput.asBlob(), 42f)
        } catch (e: SQLiteException) {
            MatcherAssert.assertThat(
                e.message,
                Matchers.isOneOf(
                    "unknown error (code 0): Unable to convert BLOB to double",
                    "unknown error (code 0 SQLITE_OK): Unable to convert BLOB to double",
                ),
            )
        }
    }

    @Test
    fun testDoubleConversions() {
        testDoubleConversion(SQLOutput.asInt("1"), 1.0)
        testDoubleConversion(SQLOutput.asFloat("1.6"), 1.6)
        testDoubleConversion(SQLOutput.asNull(), 0.0)
        testDoubleConversion(SQLOutput.asText("hi"), 0.0)
        testDoubleConversion(SQLOutput.asText("2"), 2.0)
        testDoubleConversion(SQLOutput.asText("2.52"), 2.52)
    }

    @Test
    fun testFailingDoubleConversions() {
        try {
            testDoubleConversion(SQLOutput.asBlob(), 42.0)
        } catch (e: SQLiteException) {
            MatcherAssert.assertThat(
                e.message,
                Matchers.isOneOf(
                    "unknown error (code 0): Unable to convert BLOB to double",
                    "unknown error (code 0 SQLITE_OK): Unable to convert BLOB to double",
                ),
            )
        }
    }

    @Test
    fun testRowCountEmpty() {
        val db = mDatabase
        db.execSQL("create table test (id int)")
        db
            .query("select * from test")
            .use { c -> MatcherAssert.assertThat(c.count, Matchers.`is`(0)) }
    }

    @Test
    fun testRowCountSingle() {
        val db = mDatabase
        db.execSQL("create table test (id int)")
        db.execSQL("insert into test VALUES (1)")
        db
            .query("select * from test")
            .use { c -> MatcherAssert.assertThat(c.count, Matchers.`is`(1)) }
    }

    @Test
    fun testRowCountPage() {
        val db = mDatabase
        db.execSQL("create table test (id int)")
        for (i in 0 until DB_PAGE_NUM_INT_ELEMENTS) {
            db.execSQL("insert into test VALUES (1)")
        }
        db.query("select * from test").use { c ->
            MatcherAssert.assertThat(
                c.count,
                Matchers.`is`(
                    DB_PAGE_NUM_INT_ELEMENTS,
                ),
            )
        }
    }

    @Test
    fun testRowCountPageAndOne() {
        val db = mDatabase
        db.execSQL("create table test (id int)")
        for (i in 0 until DB_PAGE_NUM_INT_ELEMENTS + 1) {
            db.execSQL("insert into test VALUES (1)")
        }
        db.query("select * from test").use { c ->
            MatcherAssert.assertThat(
                c.count,
                Matchers.`is`(
                    DB_PAGE_NUM_INT_ELEMENTS + 1,
                ),
            )
        }
    }

    @Test
    fun testBackwards() {
        val db = mDatabase
        db.execSQL("create table test (id int)")
        for (i in 0 until DB_PAGE_NUM_INT_ELEMENTS + 1) {
            db.execSQL("insert into test VALUES (1)")
        }
        db.query("select * from test").use { c ->
            c.moveToLast()
            while (c.moveToPrevious()) {
                c.getLong(0)
            }
            MatcherAssert.assertThat(c.position, Matchers.`is`(-1))
        }
    }

    @Test
    fun moveToBeforeFirst() {
        val db = mDatabase
        db.execSQL("create table test (id int)")
        for (i in 0 until DB_PAGE_NUM_INT_ELEMENTS + 1) {
            db.execSQL("insert into test VALUES (1)")
        }
        db.query("select * from test").use { c ->
            MatcherAssert.assertThat(c.position, Matchers.`is`(-1))
            Assert.assertTrue(c.moveToFirst())
            MatcherAssert.assertThat(c.position, Matchers.`is`(0))
            // despite returning false, it works
            Assert.assertFalse(
                "moveToPosition(-1) should return false, but should work",
                c.moveToPosition(-1),
            )
            MatcherAssert.assertThat(c.position, Matchers.`is`(-1))
        }
    }

    @Test
    fun testRealConversionIssue() {
        val db = mDatabase
        db.execSQL(
            "create table if not exists revlog (" + "   id              integer primary key," +
                "   cid             integer not null," + "   usn             integer not null," +
                "   ease            integer not null," + "   ivl             integer not null," +
                "   lastIvl         integer not null," + "   factor          integer not null," +
                "   time            integer not null," + "   type            integer not null)",
        )

        // one in ms: 1622631861000 (Wednesday, 2 June 2021 11:04:21)
        // two in ms: 1622691861001 ( Thursday, 3 June 2021 03:44:21.001)
        db.execSQL(
            "insert into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) VALUES (1, 1, 0, 1, 10, 5, 250, 1622631861000, 1)",
        )
        db.execSQL(
            "insert into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type) VALUES (2, 1, 0, 1, 10, 5, 250, 1622691861001, 1)",
        )
        val list = ArrayList<DoubleArray>(7) // one by day of the week
        val query =
            "SELECT strftime('%w',datetime( cast(id/ 1000  -" + 3600 +
                " as int), 'unixepoch')) as wd, " +
                "sum(case when ease = 1 then 0 else 1 end) / " +
                "cast(count() as float) * 100, " +
                "count() " +
                "from revlog " +
                "group by wd " +
                "order by wd"
        db.query(query).use { cur ->
            while (cur.moveToNext()) {
                list.add(doubleArrayOf(cur.getDouble(0), cur.getDouble(1), cur.getDouble(2)))
            }
        }
        val expected = ArrayList<DoubleArray>()
        expected.add(doubleArrayOf(3.0, 0.0, 2.0))
        MatcherAssert.assertThat(
            list[0],
            Matchers.`is`(
                expected[0],
            ),
        )
    }

    fun testLongConversion(
        output: SQLOutput,
        expected: Long,
    ) {
        testConversion(output, { c: Cursor -> c.getLong(0) }, expected)
    }

    fun testStringConversion(
        output: SQLOutput,
        expected: String?,
    ) {
        testConversion(output, { c: Cursor -> c.getString(0) }, expected)
    }

    fun testIntConversion(
        output: SQLOutput,
        expected: Int,
    ) {
        testConversion(output, { c: Cursor -> c.getInt(0) }, expected)
    }

    fun testFloatConversion(
        output: SQLOutput,
        expected: Float,
    ) {
        testConversion(output, { c: Cursor -> c.getFloat(0) }, expected)
    }

    fun testDoubleConversion(
        output: SQLOutput,
        expected: Double,
    ) {
        testConversion(output, { c: Cursor -> c.getDouble(0) }, expected)
    }

    fun testConversion(
        output: SQLOutput,
        f: (Cursor) -> Any?,
        expected: Any?,
    ) {
        mDatabase.query("select cast(" + output.value + " as " + output.sqlType + " )").use { c ->
            c.moveToFirst()
            MatcherAssert.assertThat(f(c), Matchers.`is`(expected))
        }
    }

    enum class SQLiteType {
        NULL,
        INTEGER,
        FLOAT,
        STRING,
        BLOB,
        ;

        val sqlType: String
            get() =
                when (this) {
                    INTEGER -> "INTEGER"
                    FLOAT -> "REAL"
                    NULL, STRING -> "TEXT"
                    BLOB -> "BLOB"
                }
    }

    class SQLOutput(
        var type: SQLiteType,
        var value: String?,
    ) {
        val sqlType: String
            get() = type.sqlType

        companion object {
            fun asText(value: String): SQLOutput = SQLOutput(SQLiteType.STRING, "\"" + value + "\"")

            fun asFloat(value: String): SQLOutput = SQLOutput(SQLiteType.FLOAT, value)

            fun asInt(value: String): SQLOutput = SQLOutput(SQLiteType.INTEGER, value)

            fun asNull(): SQLOutput = SQLOutput(SQLiteType.NULL, null)

            fun asBlob(): SQLOutput {
                return SQLOutput(SQLiteType.BLOB, "\"aa\"") // Unsure about this
            }
        }
    }

    companion object {
        private const val INT_SIZE_BYTES = 8
        private const val OPTIONAL_BYTES = 1

        /** Number of integers in 1 page of DB results when under test (111)  */
        @JvmField
        var DB_PAGE_NUM_INT_ELEMENTS = TEST_PAGE_SIZE / (INT_SIZE_BYTES + OPTIONAL_BYTES)
    }
}
