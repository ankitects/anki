// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import anki.ankidroid.SqlValue
import anki.ankidroid.SqlValue.DataCase
import com.ichi2.anki.libanki.utils.NotInPyLib
import com.ichi2.anki.libanki.utils.StringToLong
import net.ankiweb.rsdroid.database.SQLHandler
import java.io.Closeable
import java.sql.SQLException

/**
 * A paged SQL result from the Rust backend, with no dependency on Android's Cursor API.
 *
 * Close the result when finished, including when only part of it has been read, to release the
 * backend's cached pages. Numeric and null values follow SQLite cursor conventions.
 */
// TODO: Move paging and value conversions to anki-backend-core
@NotInPyLib // DB's native SQL results must be usable without depending on Android cursor adapters.
class DBCursor internal constructor(
    private val database: SQLHandler,
    query: String,
    bindArgs: Array<out Any?>,
) : Closeable {
    private var page = database.fullQueryProto(query, bindArgs)
    private val sequenceNumber = page.sequenceNumber
    val count: Int = page.rowCount

    var position: Int = -1
        private set

    var isClosed: Boolean = false
        private set

    fun moveToFirst(): Boolean = moveToPosition(0)

    fun moveToNext(): Boolean = moveToPosition(position + 1)

    private fun moveToPosition(target: Int): Boolean {
        check(!isClosed) { "Database cursor is closed" }
        position = target.coerceAtMost(count)
        if (position !in 0 until count) return false
        if (position < page.startIndex || position >= page.startIndex + page.result.rowsCount) {
            page = database.getNextSlice(position.toLong(), sequenceNumber)
            check(page.sequenceNumber == sequenceNumber) { "Database query changed while reading results" }
        }
        check(position >= page.startIndex && position < page.startIndex + page.result.rowsCount) {
            "Database result page does not contain row $position"
        }
        return true
    }

    private fun value(column: Int): SqlValue {
        check(!isClosed) { "Database cursor is closed" }
        check(position in 0 until count) { "Database cursor is not positioned on a row" }
        return page.result.getRows((position - page.startIndex).toInt()).getFields(column)
    }

    fun getString(column: Int): String? =
        value(column).let {
            when (it.dataCase) {
                DataCase.LONGVALUE -> it.longValue.toString()
                DataCase.DOUBLEVALUE -> it.doubleValue.toString()
                DataCase.STRINGVALUE -> it.stringValue
                DataCase.DATA_NOT_SET -> null
                DataCase.BLOBVALUE -> throw SQLException("Unable to convert BLOB to string")
                else -> error("Unknown database value: ${it.dataCase}")
            }
        }

    fun getLong(column: Int): Long =
        value(column).let {
            when (it.dataCase) {
                DataCase.LONGVALUE -> it.longValue
                DataCase.DOUBLEVALUE -> it.doubleValue.toLong()
                DataCase.STRINGVALUE ->
                    try {
                        StringToLong.strtol(it.stringValue)
                    } catch (_: NumberFormatException) {
                        0L
                    }
                DataCase.DATA_NOT_SET -> 0L
                DataCase.BLOBVALUE -> throw SQLException("Unable to convert BLOB to long")
                else -> error("Unknown database value: ${it.dataCase}")
            }
        }

    fun getInt(column: Int): Int = getLong(column).toInt()

    fun getDouble(column: Int): Double =
        value(column).let {
            when (it.dataCase) {
                DataCase.LONGVALUE -> it.longValue.toDouble()
                DataCase.DOUBLEVALUE -> it.doubleValue
                DataCase.STRINGVALUE -> it.stringValue.toDoubleOrNull() ?: 0.0
                DataCase.DATA_NOT_SET -> 0.0
                DataCase.BLOBVALUE -> throw SQLException("Unable to convert BLOB to double")
                else -> error("Unknown database value: ${it.dataCase}")
            }
        }

    fun isNull(column: Int): Boolean = value(column).dataCase == DataCase.DATA_NOT_SET

    override fun close() {
        if (!isClosed) {
            isClosed = true
            database.cancelCurrentProtoQuery(sequenceNumber)
        }
    }
}
