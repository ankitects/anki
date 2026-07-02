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
package net.ankiweb.rsdroid.ankiutil

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseUtil {
    @JvmStatic
    fun queryScalar(
        database: SupportSQLiteDatabase,
        query: String,
        vararg selectionArgs: Any?,
    ): Int {
        var cursor: Cursor? = null
        val scalar: Int
        try {
            cursor = database.query(query, selectionArgs)
            if (!cursor.moveToNext()) {
                return 0
            }
            scalar = cursor.getInt(0)
        } finally {
            cursor?.close()
        }
        return scalar
    }

    @JvmStatic
    fun queryScalarFloat(
        database: SupportSQLiteDatabase,
        query: String,
        vararg selectionArgs: Any?,
    ): Double {
        var cursor: Cursor? = null
        val scalar: Double
        try {
            cursor = database.query(query, selectionArgs)
            if (!cursor.moveToNext()) {
                return 0.0
            }
            scalar = cursor.getDouble(0)
        } finally {
            cursor?.close()
        }
        return scalar
    }
}
