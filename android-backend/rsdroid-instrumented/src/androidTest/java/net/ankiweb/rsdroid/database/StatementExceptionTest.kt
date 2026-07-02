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

import android.database.sqlite.SQLiteDoneException
import net.ankiweb.rsdroid.database.testutils.DatabaseComparison
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StatementExceptionTest : DatabaseComparison() {
    @Test
    fun simpleQueryForLongNoRowsFailure() {
        val s = super.mDatabase.compileStatement("select id from nums limit 1")
        try {
            s.simpleQueryForLong()
            Assert.fail("should throw")
        } catch (e: SQLiteDoneException) {
            assertThat(e.message, Matchers.nullValue())
        }
    }

    @Test
    fun simpleQueryForStringNoRowsFailure() {
        val s = super.mDatabase.compileStatement("select id from nums limit 1")
        try {
            s.simpleQueryForString()
            Assert.fail("should throw")
        } catch (e: SQLiteDoneException) {
            assertThat(e.message, Matchers.nullValue())
        }
    }
}
