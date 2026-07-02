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
package net.ankiweb.rsdroid.database.testutils

import net.ankiweb.rsdroid.ankiutil.Shared.getTestFilePath
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import java.io.IOException

abstract class DatabaseCorruption : DatabaseComparison() {
    private var mDatabasePath: String? = null
    private var mException: Exception? = null

    override fun handleSetupException(e: Exception?): Boolean {
        mException = e
        return true
    }

    override val databasePath: String
        get() =
            if (mDatabasePath != null) {
                mDatabasePath!!
            } else {
                try {
                    val testFilePath = getTestFilePath(context, corruptDatabaseAssetName!!)
                    mDatabasePath = testFilePath
                    testFilePath
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
    protected abstract val corruptDatabaseAssetName: String?

    @Test
    fun testCorruption() {
        MatcherAssert.assertThat(mException, Matchers.notNullValue())
        assertCorruption(mException!!)
    }

    protected abstract fun assertCorruption(setupException: Exception)
}
