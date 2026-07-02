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

import android.database.sqlite.SQLiteDoneException
import androidx.sqlite.db.SupportSQLiteStatement

class RustSQLiteStatement(
    private val database: RustSupportSQLiteDatabase,
    private val sql: String,
) : SupportSQLiteStatement {
    private val mBindings = HashMap<Int, Any?>()

    override fun execute() {
        database.query(sql, bindings).close()
    }

    override fun executeUpdateDelete(): Int = database.executeGetRowsAffected(sql, bindings)

    override fun executeInsert(): Long = database.insertForForId(sql, bindings)

    override fun simpleQueryForLong(): Long {
        database.query(sql, bindings).use { query ->
            if (!query.moveToFirst()) {
                throw SQLiteDoneException()
            }
            return query.getLong(0)
        }
    }

    override fun simpleQueryForString(): String {
        database.query(sql, bindings).use { query ->
            if (!query.moveToFirst()) {
                throw SQLiteDoneException()
            }
            return query.getString(0)
        }
    }

    override fun bindNull(index: Int) {
        bind(index, null)
    }

    override fun bindLong(
        index: Int,
        value: Long,
    ) {
        bind(index, value)
    }

    override fun bindDouble(
        index: Int,
        value: Double,
    ) {
        bind(index, value)
    }

    override fun bindString(
        index: Int,
        value: String,
    ) {
        bind(index, value)
    }

    override fun bindBlob(
        index: Int,
        value: ByteArray,
    ) {
        bind(index, value)
    }

    override fun clearBindings() {
        mBindings.clear()
    }

    override fun close() {}

    private fun bind(
        index: Int,
        value: Any?,
    ) {
        mBindings[index] = value
    }

    val bindings: Array<Any?>
        get() {
            val max = max(mBindings.keys)
            val ret = arrayOfNulls<Any>(max + 1)
            for (i in 0..max) {
                if (mBindings.containsKey(i)) {
                    ret[i] = mBindings[i]
                }
            }
            return ret
        }

    private fun max(integerSet: Set<Int>): Int {
        var max = -1
        for (i in integerSet) {
            max = kotlin.math.max(max, i)
        }
        return max
    }
}
