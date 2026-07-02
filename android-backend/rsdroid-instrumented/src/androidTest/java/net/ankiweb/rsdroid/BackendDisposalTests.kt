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

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankiweb.rsdroid.ankiutil.DatabaseUtil.queryScalar
import net.ankiweb.rsdroid.ankiutil.InstrumentedTest
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase.Companion.withRustBackend
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class BackendDisposalTests : InstrumentedTest() {
    /** This test should be run under the profiler  */
    @Test
    @Ignore("Run under profiler")
    @Throws(IOException::class)
    fun testDisposalDoesNotLeak() {
        for (i in 0..9999) {
            Timber.d("Iteration %d", i)
            super.getBackend("initial_version_2_12_1.anki2").use { backend ->
                val db = withRustBackend(backend)
                queryScalar(db, "select count(*) from revlog")
            }
        }
    }

    @Test
    @Ignore
    fun getAssetFilePathFileLeak() {
        // testDisposalDoesNotLeak had a failure: open failed: EMFILE (Too many open files)
        // This determines if it is our file handling, or rust implementation which has the issue.
        for (i in 0..9999) {
            getAssetFilePath("initial_version_2_12_1.anki2")
        }
    }
}
