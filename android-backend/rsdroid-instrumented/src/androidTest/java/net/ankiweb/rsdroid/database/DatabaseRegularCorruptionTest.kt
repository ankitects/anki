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

import android.database.sqlite.SQLiteDatabaseCorruptException
import net.ankiweb.rsdroid.database.testutils.DatabaseCorruption
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class DatabaseRegularCorruptionTest : DatabaseCorruption() {
    // In both cases, openCollection fails with the exception.
    override fun assertCorruption(setupException: Exception) {
        // Rust: net.ankiweb.rsdroid.BackendException$BackendDbException: DBError { info: "SqliteFailure(Error { code: DatabaseCorrupt, extended_code: 11 }, Some(\"database disk image is malformed\"))", kind: Other }
        // Java: database disk image is malformed (code 11): , while compiling: PRAGMA journal_mode

//        assertThat(setupException.getClass(), typeCompatibleWith(BackendException.BackendDbException.class));
        MatcherAssert.assertThat(
            setupException.javaClass,
            Matchers.typeCompatibleWith(
                SQLiteDatabaseCorruptException::class.java,
            ),
        )

        // this mapping to an unrelated exception should be done at a higher level
        MatcherAssert.assertThat(
            setupException.localizedMessage,
            Matchers.containsString("database disk image is malformed"),
        )
        MatcherAssert.assertThat(setupException.localizedMessage, Matchers.containsString("11"))
    }

    override val corruptDatabaseAssetName = "initial_version_2_12_1_corrupt_regular.anki2"
}
