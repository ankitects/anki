/*
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.database.sqlite.SQLiteException
import anki.ankidroid.DbResponse
import anki.ankidroid.Row
import anki.ankidroid.SqlValue
import anki.ankidroid.SqlValue.DataCase
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.utils.StringToDouble
import net.ankiweb.rsdroid.utils.StringToLong
import java.util.*

open class StreamingProtobufSQLiteCursor(
    /**
     * Rust Implementation:
     *
     * When we request a query, rust calculates 2MB (default) of results and sends it to us
     *
     * We keep track of where we are with getSliceStartIndex: the index into the rust collection
     *
     * The next request should be for index: getSliceStartIndex() + getCurrentSliceRowCount()
     */
    private val backend: SQLHandler,
    private val query: String,
    bindArgs: Array<out Any?>,
) : AnkiDatabaseCursor() {
    private var results: DbResponse? = null

    /** The local position in the current slice  */
    private var positionInSlice = -1
    private var columnMapping: Array<String>? = null
    private var isClosed = false
    private var sequenceNumber = 0

    /** The total number of rows for the query  */
    private var rowCount = 0

    /**The current index into the collection or rows  */
    private val sliceStartIndex: Int
        get() = results!!.startIndex.toInt()

    private fun loadPage(startingAtIndex: Long) {
        try {
            val requestedIndex = if (startingAtIndex == -1L) 0 else startingAtIndex
            results = backend.getNextSlice(requestedIndex, sequenceNumber)
            positionInSlice = if (startingAtIndex == -1L) -1 else 0
            check(results!!.sequenceNumber == sequenceNumber) {
                "rsdroid does not currently handle nested cursor-based queries. Please change the code to avoid holding a reference to the query, or implement the functionality in rsdroid"
            }
        } catch (e: BackendException) {
            throw e.toSQLiteException(query)
        }
    }

    override fun getCount(): Int = rowCount

    override fun getPosition(): Int = sliceStartIndex + positionInSlice

    override fun moveToPosition(nextPositionGlobal: Int): Boolean {
        val nextPositionLocal = nextPositionGlobal - sliceStartIndex
        val isInCurrentSlice = nextPositionLocal >= 0 && nextPositionLocal < currentSliceRowCount
        if (!isInCurrentSlice && currentSliceRowCount > 0 && count != currentSliceRowCount) {
            // loadPage this resets the position to 0
            loadPage(nextPositionGlobal.toLong())
        } else {
            positionInSlice = nextPositionLocal
        }
        // moving to -1 should return false and mutate the position
        return positionInSlice >= 0 && currentSliceRowCount > 0 && positionInSlice < currentSliceRowCount
    }

    override fun getColumnIndex(columnName: String): Int {
        try {
            val names = columnNames
            for (i in names.indices) {
                if (columnName == names[i]) {
                    return i
                }
            }
        } catch (e: Exception) {
            return -1
        }
        return -1
    }

    @Throws(IllegalArgumentException::class)
    override fun getColumnIndexOrThrow(columnName: String): Int {
        try {
            val names = columnNames
            for (i in names.indices) {
                if (columnName == names[i]) {
                    return i
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException(e)
        }
        throw IllegalArgumentException(String.format("Could not find column '%s'", columnName))
    }

    override fun getColumnName(columnIndex: Int): String = columnNamesInternal[columnIndex]

    override fun getColumnNames(): Array<String> = columnNamesInternal

    private val columnNamesInternal: Array<String>
        get() {
            if (columnMapping == null) {
                columnMapping = backend.getColumnNames(query)
                checkNotNull(columnMapping) { "unable to obtain column mapping" }
            }
            return columnMapping!!
        }

    override fun getColumnCount(): Int =
        if (currentSliceRowCount == 0) {
            0
        } else {
            results!!.result.getRows(0).fieldsCount
        }

    override fun getString(columnIndex: Int): String? {
        val field = getFieldAtIndex(columnIndex)
        return when (field.dataCase) {
            DataCase.BLOBVALUE -> throw SQLiteException("unknown error (code 0): Unable to convert BLOB to string")
            DataCase.LONGVALUE -> field.longValue.toString()
            DataCase.DOUBLEVALUE -> field.doubleValue.toString()
            DataCase.STRINGVALUE -> field.stringValue
            DataCase.DATA_NOT_SET -> null
            else -> throw IllegalStateException("Unknown data case: " + field.dataCase)
        }
    }

    override fun getLong(columnIndex: Int): Long {
        val field = getFieldAtIndex(columnIndex)
        return when (field.dataCase) {
            DataCase.BLOBVALUE -> throw SQLiteException("unknown error (code 0): Unable to convert BLOB to long")
            DataCase.LONGVALUE -> field.longValue
            DataCase.DOUBLEVALUE -> field.doubleValue.toLong()
            DataCase.STRINGVALUE -> strtoll(field.stringValue)
            DataCase.DATA_NOT_SET -> 0
            else -> throw IllegalStateException("Unknown data case: " + field.dataCase)
        }
    }

    override fun getDouble(columnIndex: Int): Double {
        val field = getFieldAtIndex(columnIndex)
        return when (field.dataCase) {
            DataCase.BLOBVALUE -> throw SQLiteException("unknown error (code 0): Unable to convert BLOB to double")
            DataCase.LONGVALUE -> field.longValue.toDouble()
            DataCase.DOUBLEVALUE -> field.doubleValue
            DataCase.STRINGVALUE -> strtod(field.stringValue)
            DataCase.DATA_NOT_SET -> 0.0
            else -> throw IllegalStateException("Unknown data case: " + field.dataCase)
        }
    }

    override fun getShort(columnIndex: Int): Short = getLong(columnIndex).toShort()

    override fun getInt(columnIndex: Int): Int = getLong(columnIndex).toInt()

    override fun getFloat(columnIndex: Int): Float = getDouble(columnIndex).toFloat()

    private fun isNull(field: SqlValue): Boolean = field.dataCase == DataCase.DATA_NOT_SET

    override fun isNull(columnIndex: Int): Boolean {
        val field = getFieldAtIndex(columnIndex)
        return isNull(field)
    }

    override fun close() {
        isClosed = true
        backend.cancelCurrentProtoQuery(sequenceNumber)
    }

    override fun isClosed(): Boolean = isClosed

    override fun getType(columnIndex: Int): Int {
        val field = getFieldAtIndex(columnIndex)
        return when (field.dataCase) {
            DataCase.BLOBVALUE -> Cursor.FIELD_TYPE_BLOB
            DataCase.LONGVALUE -> Cursor.FIELD_TYPE_INTEGER
            DataCase.DOUBLEVALUE -> Cursor.FIELD_TYPE_FLOAT
            DataCase.STRINGVALUE -> Cursor.FIELD_TYPE_STRING
            DataCase.DATA_NOT_SET -> Cursor.FIELD_TYPE_NULL
            else -> throw IllegalStateException("Unknown data case: " + field.dataCase)
        }
    }

    protected val rowAtCurrentPosition: Row
        get() {
            val result = results!!.result
            val rowCount = currentSliceRowCount
            if (positionInSlice < 0 || positionInSlice >= rowCount) {
                throw CursorIndexOutOfBoundsException(
                    String.format(Locale.ROOT, "Index %d requested, with a size of %d", positionInSlice, rowCount),
                )
            }
            return result.getRows(positionInSlice)
        }

    private fun getFieldAtIndex(columnIndex: Int): SqlValue = rowAtCurrentPosition.getFields(columnIndex)

    val currentSliceRowCount: Int
        get() = results!!.result.rowsCount

    private fun strtoll(stringValue: String): Long =
        try {
            StringToLong.strtol(stringValue)
        } catch (exception: NumberFormatException) {
            0
        }

    private fun strtod(stringValue: String): Double =
        try {
            StringToDouble.strtod(stringValue)
        } catch (exception: NumberFormatException) {
            0.0
        }

    init {
        try {
            results = backend.fullQueryProto(query, bindArgs)
            sequenceNumber = results!!.sequenceNumber
            rowCount = results!!.rowCount
        } catch (e: BackendException) {
            throw e.toSQLiteException(query)
        }
    }
}
