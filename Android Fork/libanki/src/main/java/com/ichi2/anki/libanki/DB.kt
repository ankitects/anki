// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Andrew <andrewdubya@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.libanki

import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import net.ankiweb.rsdroid.database.SQLHandler
import org.slf4j.LoggerFactory
import java.sql.SQLException

private val logger = LoggerFactory.getLogger(DB::class.java)

/** SQL helpers for the collection's Rust-backed database, independent of Android. */
class DB(
    val database: SQLHandler,
) {
    var mod = false

    /** The collection owns the connection; this wrapper does not close it. */
    fun close() = Unit

    // Allows to avoid using new Object[]
    fun query(
        query: String,
        vararg selectionArgs: Any?,
    ): DBCursor = DBCursor(database, query, selectionArgs)

    fun queryScalar(
        query: String,
        vararg selectionArgs: Any?,
    ): Int {
        val scalar: Int
        query(query, *selectionArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getInt(0)
        }
        return scalar
    }

    fun queryString(
        query: String,
        vararg bindArgs: Any?,
    ): String {
        query(query, *bindArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                throw SQLException("No result for query: $query")
            }
            return cursor.getString(0) ?: throw SQLException("Null string result for query: $query")
        }
    }

    fun queryLongScalar(
        query: String,
        vararg bindArgs: Any?,
    ): Long {
        var scalar: Long
        query(query, *bindArgs).use { cursor ->
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getLong(0)
        }
        return scalar
    }

    fun queryLongList(
        query: String,
        vararg bindArgs: Any?,
    ): ArrayList<Long> {
        val results = ArrayList<Long>()
        query(query, *bindArgs).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getLong(0))
            }
        }
        return results
    }

    fun queryStringList(
        query: String,
        vararg bindArgs: Any?,
    ): ArrayList<String> {
        val results = ArrayList<String>()
        query(query, *bindArgs).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(cursor.getString(0) ?: throw SQLException("Null string result for query: $query"))
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
            logger.info("clearing undo and study queues")
        }
        DBCursor(database, sql, `object`).close()
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
                execute(query)
            }
        }
    }

    val path: String
        get() = "<rust>"
}
