/*
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>
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

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import net.ankiweb.rsdroid.Backend

/**
 * Helper routines for constructing Rust-backed and Framework-backed
 * SupportSQLiteDatabases.
 */
abstract class AnkiSupportSQLiteDatabase {
    companion object {
        /**
         * Wrap a Rust backend connection (which provides an SQL interface).
         * Caller is responsible for opening&closing the database.
         */
        @JvmStatic
        fun withRustBackend(backend: Backend): SupportSQLiteDatabase = RustSupportSQLiteDatabase(backend)

        /**
         * Open a connection using the Android framework.
         * If path is not provided, an in-memory database is opened.
         */
        @JvmStatic
        @JvmOverloads
        fun withFramework(
            context: Context,
            path: String?,
            dbCallback: SupportSQLiteOpenHelper.Callback? = null,
        ): SupportSQLiteDatabase {
            val configuration =
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(path)
                    .callback(dbCallback ?: DefaultDbCallback(1))
                    .build()
            return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        }
    }

    open class DefaultDbCallback(
        version: Int,
    ) : SupportSQLiteOpenHelper.Callback(version) {
        override fun onCreate(db: SupportSQLiteDatabase) {}

        override fun onUpgrade(
            db: SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int,
        ) {}

        override fun onCorruption(db: SupportSQLiteDatabase) {}
    }
}
