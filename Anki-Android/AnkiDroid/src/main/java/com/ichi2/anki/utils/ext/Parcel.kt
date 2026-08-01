/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
 */

package com.ichi2.anki.utils.ext

import android.os.Parcel
import androidx.core.os.ParcelCompat
import java.io.Serializable

fun <T : Serializable> Parcel.writeSerializableList(list: List<T?>?) {
    if (list == null) {
        writeInt(-1)
        return
    }
    writeInt(list.size)
    for (item in list) {
        if (item == null) {
            writeInt(0)
            continue
        }
        writeInt(1)
        writeSerializable(item)
    }
}

inline fun <reified T : Serializable> Parcel.readSerializableList(): List<T?>? {
    val size = readInt()
    if (size == -1) return null
    return List(size = size) {
        if (readInt() == 0) {
            null
        } else {
            ParcelCompat.readSerializable(
                this,
                T::class.java.classLoader,
                T::class.java,
            )
        }
    }
}
