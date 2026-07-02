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

import android.content.ContentResolver
import android.database.CharArrayBuffer
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle
import timber.log.Timber

/**
 * Base class for all database cursors, abstracting database methods to a common interface
 * Throwing on non-database-related cursor-methods
 *
 * This is useful because cursors are an android-specific implementation and not a database-specific
 * implementation, and many of the methods are not relevant.
 */
abstract class AnkiDatabaseCursor : Cursor {
    override fun isFirst(): Boolean = position == 0

    override fun isBeforeFirst(): Boolean = position < 0

    abstract override fun getCount(): Int

    abstract override fun getPosition(): Int

    abstract override fun getColumnIndex(columnName: String): Int

    @Throws(IllegalArgumentException::class)
    abstract override fun getColumnIndexOrThrow(columnName: String): Int

    abstract override fun getColumnName(columnIndex: Int): String

    abstract override fun getColumnNames(): Array<String>

    abstract override fun getColumnCount(): Int

    abstract override fun getString(columnIndex: Int): String?

    abstract override fun getShort(columnIndex: Int): Short

    abstract override fun getInt(columnIndex: Int): Int

    abstract override fun getLong(columnIndex: Int): Long

    abstract override fun getFloat(columnIndex: Int): Float

    abstract override fun getDouble(columnIndex: Int): Double

    abstract override fun isNull(columnIndex: Int): Boolean

    abstract override fun close()

    abstract override fun isClosed(): Boolean

    abstract override fun getType(columnIndex: Int): Int

    override fun getBlob(columnIndex: Int): ByteArray = throw NotImplementedException()

    override fun setNotificationUri(
        cr: ContentResolver,
        uri: Uri,
    ): Unit = throw NotImplementedException()

    @Deprecated("Deprecated in Java")
    override fun deactivate() {
        Timber.w("deactivate - not implemented - throwing")
        throw NotImplementedException()
    }

    override fun copyStringToBuffer(
        columnIndex: Int,
        buffer: CharArrayBuffer,
    ): Unit = throw NotImplementedException()

    @Deprecated("Deprecated in Java")
    override fun requery(): Boolean {
        Timber.w("requery - not implemented - throwing")
        throw NotImplementedException()
    }

    override fun getNotificationUri(): Uri = throw NotImplementedException()

    override fun getWantsAllOnMoveCalls(): Boolean = false

    override fun setExtras(extras: Bundle): Unit = throw NotImplementedException()

    override fun getExtras(): Bundle = throw NotImplementedException()

    override fun respond(extras: Bundle): Bundle = throw NotImplementedException()

    abstract override fun moveToPosition(nextPositionGlobal: Int): Boolean

    override fun registerContentObserver(observer: ContentObserver) {
        Timber.w("Not implemented: registerContentObserver - shouldn't matter unless requery() is called")
    }

    override fun unregisterContentObserver(observer: ContentObserver) {
        Timber.w("Not implemented: unregisterContentObserver - shouldn't matter unless requery() is called")
    }

    override fun registerDataSetObserver(observer: DataSetObserver) {
        Timber.w("Not implemented: registerDataSetObserver - shouldn't matter unless requery() is called")
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver) {
        Timber.w("Not implemented: unregisterDataSetObserver - shouldn't matter unless requery() is called")
    }

    override fun isLast(): Boolean = position == lastPosition

    override fun isAfterLast(): Boolean = position >= count

    override fun move(offset: Int): Boolean = moveToPosition(position + offset)

    override fun moveToLast(): Boolean {
        val toMoveTo = lastPosition
        return moveToPosition(toMoveTo)
    }

    override fun moveToFirst(): Boolean = moveToPosition(0)

    override fun moveToNext(): Boolean {
        val toMoveTo = position + 1
        return moveToPosition(toMoveTo)
    }

    override fun moveToPrevious(): Boolean {
        val toMoveTo = position - 1
        return moveToPosition(toMoveTo)
    }

    protected val lastPosition: Int
        get() = count - 1
}
