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

import androidx.annotation.CheckResult
import anki.ankidroid.DbResponse
import org.json.JSONArray

interface SQLHandler {
    @CheckResult
    fun fullQuery(
        query: String,
        bindArgs: Array<Any?>?,
    ): JSONArray

    fun fullQuery(query: String): JSONArray = fullQuery(query, null)

    fun executeGetRowsAffected(
        sql: String,
        bindArgs: Array<Any?>?,
    ): Int

    fun insertForId(
        sql: String,
        bindArgs: Array<Any?>?,
    ): Long

    @CheckResult
    fun getColumnNames(sql: String): Array<String>

    fun closeDatabase()

    @CheckResult
    fun getPath(): String?

    // Protobuf-related (#6)
    fun getNextSlice(
        startIndex: Long,
        sequenceNumber: Int,
    ): DbResponse

    fun fullQueryProto(
        query: String,
        bindArgs: Array<out Any?>,
    ): DbResponse

    fun cancelCurrentProtoQuery(sequenceNumber: Int)

    fun cancelAllProtoQueries()

    /**
     * Sets the page size for all future calls to
     * [SQLHandler.getNextSlice]
     * and
     * [SQLHandler.fullQueryProto]
     *
     * Default: 2MB
     */
    fun setPageSize(pageSizeBytes: Long)
}
