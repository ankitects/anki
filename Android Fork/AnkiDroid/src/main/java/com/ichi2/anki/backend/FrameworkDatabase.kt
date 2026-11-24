// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Andrew <andrewdubya@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.backend

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import timber.log.Timber

/**
 * Framework SQLite access for reading collection versions and modifying unopened test collections.
 *
 * @param database the SQLite database containing the collection data.
 */
class FrameworkDatabase(
    private val database: SupportSQLiteDatabase,
) {
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

    /**
     * Execute a single SQL statement that does not return data.
     */
    fun execute(
        sql: String,
        vararg `object`: Any?,
    ) {
        database.execSQL(sql, `object`)
    }

    /**
     * Executes a collection of ';'-delimited SQL statements which do not return data.
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
}
