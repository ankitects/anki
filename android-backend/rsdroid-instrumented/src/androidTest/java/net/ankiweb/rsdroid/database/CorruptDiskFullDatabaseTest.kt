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

import android.database.sqlite.SQLiteFullException
import net.ankiweb.rsdroid.database.testutils.DatabaseCorruption
import net.ankiweb.rsdroid.exceptions.BackendJsonException
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** It was (pleasantly) surprisingly hard to corrupt a database via java  */
@RunWith(Parameterized::class)
class CorruptDiskFullDatabaseTest : DatabaseCorruption() {
    override fun assertCorruption(setupException: Exception) {
        when (schedVersion) {
            DatabaseType.RUST -> {
                // RUST is now a bit more tolerant, with newer sqlite, fails loading config JSON
                MatcherAssert.assertThat(
                    setupException.javaClass,
                    Matchers.typeCompatibleWith(
                        BackendJsonException::class.java,
                    ),
                )
                MatcherAssert.assertThat(
                    setupException.localizedMessage,
                    Matchers.containsString("decoding deck config: expected value at line 1 column 1"),
                )
            }
            DatabaseType.FRAMEWORK -> {
                // FRAMEWORK still complains about disk full corruption:
                // error while compiling: "create table nums (id int)": DBError { info: "SqliteFailure(Error { code: DiskFull, extended_code: 13 }, Some(\"database or disk is full\"))", kind: Other }
                MatcherAssert.assertThat(
                    setupException.javaClass,
                    Matchers.typeCompatibleWith(
                        SQLiteFullException::class.java,
                    ),
                )
                // Java: "database or disk is full (code 13)"
                MatcherAssert.assertThat(
                    setupException.localizedMessage,
                    Matchers.containsString("database or disk is full"),
                )
            }
            else -> null
        }
    }

    override val corruptDatabaseAssetName = "initial_version_2_12_1_corrupt_diskfull.anki2"
}
