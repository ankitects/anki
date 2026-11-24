// SPDX-License-Identifier: GPL-3.0-or-later

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
