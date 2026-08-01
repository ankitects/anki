/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file incorporates code under the following license
 *
 *     Copyright (C) 2006 The Android Open Source Project
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
 * From: Android\Sdk\sources\android-29\android\database\DatabaseUtils.java
 */
package com.ichi2.anki.testutil

import android.database.Cursor
import android.database.CursorWindow

object DatabaseUtils {
    /**
     * Fills the specified cursor window by iterating over the contents of the cursor.
     * The window is filled until the cursor is exhausted or the window runs out
     * of space.
     *
     * The original position of the cursor is left unchanged by this operation.
     *
     * @param cursor The cursor that contains the data to put in the window.
     * @param positionParam The start position for filling the window.
     * @param window The window to fill.
     */
    fun cursorFillWindow(
        cursor: Cursor,
        positionParam: Int,
        window: CursorWindow,
    ) {
        var position = positionParam
        if (position < 0 || position >= cursor.count) {
            return
        }
        val oldPos = cursor.position
        val numColumns = cursor.columnCount
        window.clear()
        window.startPosition = position
        window.setNumColumns(numColumns)
        if (cursor.moveToPosition(position)) {
            rowloop@ do {
                if (!window.allocRow()) {
                    break
                }
                for (i in 0 until numColumns) {
                    val success: Boolean =
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> window.putNull(position, i)
                            Cursor.FIELD_TYPE_INTEGER -> window.putLong(cursor.getLong(i), position, i)
                            Cursor.FIELD_TYPE_FLOAT -> window.putDouble(cursor.getDouble(i), position, i)
                            Cursor.FIELD_TYPE_BLOB -> {
                                val value = cursor.getBlob(i)
                                if (value != null) window.putBlob(value, position, i) else window.putNull(position, i)
                            }
                            Cursor.FIELD_TYPE_STRING -> {
                                val value = cursor.getString(i)
                                if (value != null) window.putString(value, position, i) else window.putNull(position, i)
                            }
                            else -> {
                                val value = cursor.getString(i)
                                if (value != null) window.putString(value, position, i) else window.putNull(position, i)
                            }
                        }
                    if (!success) {
                        window.freeLastRow()
                        break@rowloop
                    }
                }
                position += 1
            } while (cursor.moveToNext())
        }
        cursor.moveToPosition(oldPos)
    }
}
