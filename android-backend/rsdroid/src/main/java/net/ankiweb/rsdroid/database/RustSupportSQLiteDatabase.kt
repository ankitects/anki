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
 *
 * Contains code under the following license
 *
 *     Copyright (C) 2006 The Android Open Source Project
 *     Copyright (C) 2016 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 * from android.database.sqlite.SQLiteStatement
 * from update/insert/delete
 */
package net.ankiweb.rsdroid.database

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteTransactionListener
import android.os.CancellationSignal
import android.text.TextUtils
import android.util.Pair
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import net.ankiweb.rsdroid.Backend
import java.util.*

/**
 * A wrapper that allows the Rust backend to act like a standard Android
 * database. Key differences:
 * - the backend must be instructed to open the collection by the caller
 * - .close() is a no-op - you should call .close() on the collection (or
 *   backend directly in testing) instead
 * - isOpen and getPath return dummy data
 */
class RustSupportSQLiteDatabase(
    private val backend: Backend,
) : SupportSQLiteDatabase {
    override var isReadOnly: Boolean
        @Suppress("UNUSED_PARAMETER")
        set(readOnly) {
            throw NotImplementedException()
        }
        get() {
            return false
        }

    override var isOpen: Boolean
        @Suppress("UNUSED_PARAMETER")
        set(open) {
            throw NotImplementedException()
        }
        get() {
            return true
        }

    override fun compileStatement(sql: String): SupportSQLiteStatement = RustSQLiteStatement(this, sql)

    override fun beginTransaction(): Unit = throw NotImplementedException()

    override fun endTransaction(): Unit = throw NotImplementedException()

    override fun setTransactionSuccessful(): Unit = throw NotImplementedException()

    override fun inTransaction(): Boolean = false

    override var version: Int
        @Suppress("UNUSED_PARAMETER")
        set(version) {
            throw NotImplementedException()
        }
        get() {
            throw NotImplementedException.todo()
        }

    override fun query(query: String): Cursor = query(query, emptyArray())

    override fun query(
        query: String,
        bindArgs: Array<out Any?>,
    ): Cursor = StreamingProtobufSQLiteCursor(backend, query, bindArgs)

    override fun query(query: SupportSQLiteQuery): Cursor = throw NotImplementedException.todo()

    override fun query(
        query: SupportSQLiteQuery,
        cancellationSignal: CancellationSignal?,
    ): Cursor = throw NotImplementedException.todo()

    @Throws(SQLException::class)
    override fun insert(
        table: String,
        conflictAlgorithm: Int,
        values: ContentValues,
    ): Long {
        val sql = StringBuilder()
        sql.append("INSERT")
        sql.append(CONFLICT_VALUES[conflictAlgorithm])
        sql.append(" INTO ")
        sql.append(table)
        sql.append('(')
        var bindArgs: Array<out Any?> = emptyArray()
        val size = values.size()
        if (size > 0) {
            bindArgs = arrayOfNulls(size)
            var i = 0
            for ((key, value) in values.valueSet()) {
                sql.append(if (i > 0) "," else "")
                sql.append(key)
                bindArgs[i++] = value
            }
            sql.append(')')
            sql.append(" VALUES (")
            i = 0
            while (i < size) {
                sql.append(if (i > 0) ",?" else "?")
                i++
            }
        } else {
            sql.append(null as String?).append(") VALUES (NULL")
        }
        sql.append(')')
        query(sql.toString(), bindArgs).close()
        return 0
    }

    override fun update(
        table: String,
        conflictAlgorithm: Int,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<out Any?>?,
    ): Int {
        // taken from SQLiteDatabase class.
        require(values.size() > 0) { "Empty values" }
        val sql = StringBuilder(120)
        sql.append("UPDATE ")
        sql.append(CONFLICT_VALUES[conflictAlgorithm])
        sql.append(table)
        sql.append(" SET ")

        // move all bind args to one array
        val setValuesSize = values.size()
        val bindArgsSize = if (whereArgs == null) setValuesSize else setValuesSize + whereArgs.size
        val bindArgs = arrayOfNulls<Any>(bindArgsSize)
        var i = 0
        for (colName in values.keySet()) {
            sql.append(if (i > 0) "," else "")
            sql.append(colName)
            bindArgs[i++] = values[colName]
            sql.append("=?")
        }
        if (whereArgs != null) {
            i = setValuesSize
            while (i < bindArgsSize) {
                bindArgs[i] = whereArgs[i - setValuesSize]
                i++
            }
        }
        if (!TextUtils.isEmpty(whereClause)) {
            sql.append(" WHERE ")
            sql.append(whereClause)
        }
        return executeGetRowsAffected(sql.toString(), bindArgs)
    }

    @Throws(SQLException::class)
    override fun execSQL(sql: String) {
        execSQL(sql, emptyArray())
    }

    @Throws(SQLException::class)
    override fun execSQL(
        sql: String,
        bindArgs: Array<out Any?>,
    ) {
        query(sql, bindArgs).close()
    }

    override fun needUpgrade(newVersion: Int): Boolean {
        // needed for metaDB, but not for Anki DB
        throw NotImplementedException.todo()
    }

    override var path: String
        @Suppress("UNUSED_PARAMETER")
        set(path) {
            throw NotImplementedException()
        }
        get() {
            return "<rust>"
        }

    override var isWriteAheadLoggingEnabled: Boolean
        @Suppress("UNUSED_PARAMETER")
        set(walEnabled) {
            throw NotImplementedException()
        }
        get() {
            return false
        }

    override fun disableWriteAheadLogging() {
        // Nothing to do - openAnkiDroidCollection does this
    }

    override var isDatabaseIntegrityOk: Boolean
        @Suppress("UNUSED_PARAMETER")
        set(ok) {
            throw NotImplementedException()
        }
        get() {
            val pragmaIntegrityCheck = query("pragma integrity_check")
            if (!pragmaIntegrityCheck.moveToFirst()) {
                return false
            }
            val value = pragmaIntegrityCheck.getString(0)
            return "ok" == value
        }

    override fun close() {
        // no-op
    }

    // Not part of interface
    fun executeGetRowsAffected(
        sql: String,
        bindArgs: Array<Any?>?,
    ): Int =
        try {
            backend.executeGetRowsAffected(sql, bindArgs)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    fun insertForForId(
        sql: String,
        bindArgs: Array<Any?>?,
    ): Long =
        try {
            backend.insertForId(sql, bindArgs)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

    /** Confirmed that the below are not used for our code  */
    override fun delete(
        table: String,
        whereClause: String?,
        whereArgs: Array<out Any?>?,
    ): Int {
        // Complex to implement and not required
        throw NotImplementedException()
    }

    override var isDbLockedByCurrentThread: Boolean
        @Suppress("UNUSED_PARAMETER")
        set(locked) {
            throw NotImplementedException()
        }
        get() {
            throw NotImplementedException()
        }

    override fun yieldIfContendedSafely(): Boolean = throw NotImplementedException()

    override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean = throw NotImplementedException()

    override fun setLocale(locale: Locale): Unit = throw NotImplementedException()

    override fun setMaxSqlCacheSize(cacheSize: Int): Unit = throw NotImplementedException()

    override fun setMaximumSize(numBytes: Long): Long = throw NotImplementedException()

    override val maximumSize: Long
        get() {
            throw NotImplementedException()
        }

    override var pageSize: Long
        @Suppress("UNUSED_PARAMETER")
        set(value) {
            throw NotImplementedException()
        }
        get() {
            throw NotImplementedException()
        }

    override fun setForeignKeyConstraintsEnabled(enabled: Boolean): Unit = throw NotImplementedException()

    override fun enableWriteAheadLogging(): Boolean = throw NotImplementedException()

    override var attachedDbs: List<Pair<String, String>>
        @Suppress("UNUSED_PARAMETER")
        set(dbs) {
            throw NotImplementedException()
        }
        get() {
            throw NotImplementedException()
        }

    override fun beginTransactionNonExclusive(): Unit = throw NotImplementedException()

    override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener): Unit = throw NotImplementedException()

    override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener): Unit =
        throw NotImplementedException()

    companion object {
        private val CONFLICT_VALUES = arrayOf("", " OR ROLLBACK ", " OR ABORT ", " OR FAIL ", " OR IGNORE ", " OR REPLACE ")
    }
}
