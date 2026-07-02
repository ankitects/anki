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

import net.ankiweb.rsdroid.ankiutil.InstrumentedTest
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase.Companion.withRustBackend
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Ignore
import org.junit.Test
import java.io.IOException

class BackendSlowTests : InstrumentedTest() {
    @Test
    @Ignore("Run manually - slow test")
    @Throws(IOException::class)
    fun ensureSQLIsStreamed() {
        // Issue #6 - commentary there.
        // We need to ensure that the SQL is streamed so we don't OOM the Java.
        // Presently the Rust loads all the data at once - from the exceptions below,
        // we also need to work around this.

        // Testing was done on an API 21 emulator - 1536 RAM, 256MB VM Heap
        // using JSON, these parameters crashed the app, this does not happen with protobuf
        val numberOfElements = 10000
        val numberOfAppends = 10 // multiply by (2^n)
        super.getBackend("initial_version_2_12_1.anki2").use { backend ->
            val db = withRustBackend(backend)
            db.query("create table tmp (id varchar)")
            val longString = StringBuilder("VeryLongStringWhich Will MaybeCauseAnOOM IfWeDoItWrong")
            // double the string length on each iteration
            for (i in 0 until numberOfAppends) {
                longString.append(longString)
            }
            for (i in 0 until numberOfElements) {
                // add a suffix so the string can't be interned
                db.execSQL(
                    "insert into tmp (id) values (?)",
                    arrayOf<Any>(longString.toString() + i),
                )
            }
            var count = 0
            db.query("select * from tmp").use { c ->
                while (c.moveToNext()) {
                    c.getString(0)
                    count += 1
                }
            }
            MatcherAssert.assertThat(count, Matchers.`is`(numberOfElements))
        }
    }
}
