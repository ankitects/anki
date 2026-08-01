/*
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2009 Andrew <andrewdubya@gmail.com>
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
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
package com.ichi2.anki.libanki

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import timber.log.Timber

/**
 * Database layer for AnkiDroid. Wraps a [SupportSQLiteDatabase], and provides some helpers on top.
 *
 * @param database the SQLite database containing the collection data.
 */
class DB(
    val database: SupportSQLiteDatabase,
) {
    var mod = false

    fun close() {
        try {
            database.close()
            Timber.d("Database %s closed = %s", database.path, !database.isOpen)
        } catch (e: Exception) {
            // The pre-framework requery API ate this exception, but the framework API exposes it.
            // We may want to propagate it in the future, but for now maintain the old API and log.
            Timber.e(e, "Failed to close database %s", database.path)
        }
    }

    // Allows to avoid using new Object[]
    fun query(
        query: String,
        vararg selectionArgs: Any,
    ): Cursor = database.query(query, selectionArgs)

    fun queryScalar(
        query: String,
        vararg selectionArgs: Any,
    ): Int {
        val scalar: Int
        database.query(query, selectionArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getInt(0)
        }
        return scalar
    }

    fun queryString(
        query: String,
        vararg bindArgs: Any,
    ): String {
        database.query(query, bindArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                throw SQLException("No result for query: $query")
            }
            return cursor.getString(0)
        }
    }

    fun queryLongScalar(
        query: String,
        vararg bindArgs: Any,
    ): Long {
        var scalar: Long
        database.query(query, bindArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getLong(0)
        }
        return scalar
    }

    fun queryLongList(
        query: String,
        vararg bindArgs: Any,
    ): ArrayList<Long> {
        val results = ArrayList<Long>()
        database.query(query, bindArgs).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getLong(0))
            }
        }
        return results
    }

    fun queryStringList(
        query: String,
        vararg bindArgs: Any,
    ): ArrayList<String> {
        val results = ArrayList<String>()
        database.query(query, bindArgs).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0))
            }
        }
        return results
    }

    /**
     * Execute a single SQL statement that does not return data.
     *
     * This method discards the undo and study queues unless the statement is a `SELECT`.
     */
    fun execute(
        sql: String,
        vararg `object`: Any?,
    ) {
        // permalink: https://github.com/ankitects/anki/blob/83c615cc7f9aef3c336936fa797671965538f89c/rslib/src/backend/dbproxy.rs#L170-L178
        if (!sql.lowercase().trim().startsWith("select")) {
            Timber.i("clearing undo and study queues")
        }
        database.execSQL(sql, `object`)
    }

    /**
     * Executes a collection of ';'-delimited SQL statements which do not return data.
     *
     * This method discards the undo and study queues unless all statements are `SELECT`.
     */
    @KotlinCleanup("""Use Kotlin string. Change split so that there is no empty string after last ";".""")
    fun executeScript(sql: String) {
        val queries = sql.split(";")
        for (query in queries) {
            if (query.isNotEmpty()) {
                database.execSQL(query)
            }
        }
    }

    fun update(
        table: String,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<String>?,
    ): Int = database.update(table, SQLiteDatabase.CONFLICT_NONE, values, whereClause, whereArgs)

    fun insert(
        table: String,
        values: ContentValues,
    ): Long = database.insert(table, SQLiteDatabase.CONFLICT_NONE, values)

    val path: String
        get() = database.path ?: ":memory:"

    companion object {
        private val MOD_SQL_STATEMENTS = arrayOf("insert", "update", "delete")
    }
}
