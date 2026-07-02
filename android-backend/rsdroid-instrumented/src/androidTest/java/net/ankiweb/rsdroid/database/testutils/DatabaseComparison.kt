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

import androidx.sqlite.db.SupportSQLiteDatabase
import net.ankiweb.rsdroid.BackendFactory.getBackend
import net.ankiweb.rsdroid.ankiutil.InstrumentedTest
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase.Companion.withFramework
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase.Companion.withRustBackend
import org.junit.Before
import org.junit.runners.Parameterized
import java.util.*

open class DatabaseComparison : InstrumentedTest() {
    @Parameterized.Parameter
    @JvmField
    var schedVersion: DatabaseType? = null
    protected lateinit var mDatabase: SupportSQLiteDatabase

    @Before
    fun setUp() {
        try {
            System.loadLibrary("rsdroid")
            mDatabase = database
            mDatabase.execSQL("create table nums (id int)")
        } catch (e: Exception) {
            if (!handleSetupException(e)) {
                throw e
            }
        }
    }

    protected open fun handleSetupException(e: Exception?): Boolean = false

    protected val database: SupportSQLiteDatabase
        get() {
            when (schedVersion) {
                DatabaseType.RUST -> {
                    val backend2 = getBackend(mutableListOf("en"))
                    backend2.openCollection(databasePath!!)
                    return withRustBackend(backend2)
                }

                DatabaseType.FRAMEWORK -> return withFramework(context, databasePath)
                else -> assert(false)
            }
            throw IllegalStateException()
        }
    protected open val databasePath: String?
        get() = // TODO: look into this - null should work
            try {
                when (schedVersion) {
                    DatabaseType.RUST -> ":memory:"
                    DatabaseType.FRAMEWORK -> null
                    else -> null
                }
            } catch (ex: NullPointerException) {
                throw IllegalStateException(
                    "Class is not annotated with @RunWith(Parameterized.class)",
                    ex,
                )
            }

    enum class DatabaseType {
        FRAMEWORK,
        RUST,
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun initParameters(): Collection<Array<Any>> =
            Arrays.asList(
                *arrayOf(
                    arrayOf(DatabaseType.FRAMEWORK),
                    arrayOf(DatabaseType.RUST),
                ),
            )
    }
}
