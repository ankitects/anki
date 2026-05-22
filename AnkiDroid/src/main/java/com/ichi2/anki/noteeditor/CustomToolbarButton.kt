/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.noteeditor

import com.ichi2.anki.common.utils.HashUtil.hashSetInit
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.noteeditor.Toolbar.TextWrapper
import timber.log.Timber
import java.util.ArrayList

typealias ButtonText = String

class CustomToolbarButton(
    var index: Int,
    var buttonText: ButtonText,
    val prefix: String,
    val suffix: String,
) {
    fun toFormatter(): Toolbar.TextFormatter = TextWrapper(prefix, suffix)

    companion object {
        const val KEEP_EMPTY_ENTRIES = -1

        fun fromString(s: String?): CustomToolbarButton? {
            if (s.isNullOrEmpty()) {
                return null
            }
            val fields = s.split(Consts.FIELD_SEPARATOR.toRegex(), KEEP_EMPTY_ENTRIES.coerceAtLeast(0)).toTypedArray()
            if (fields.size != 4) {
                return null
            }
            val index: Int =
                try {
                    fields[0].toInt()
                } catch (e: Exception) {
                    Timber.w(e)
                    return null
                }
            return CustomToolbarButton(index, fields[1], fields[2], fields[3])
        }

        fun fromStringSet(hs: Set<String?>): ArrayList<CustomToolbarButton> {
            val buttons = ArrayList<CustomToolbarButton>(hs.size)
            for (s in hs) {
                val customToolbarButton = fromString(s)
                if (customToolbarButton != null) {
                    buttons.add(customToolbarButton)
                }
            }
            buttons.sortWith { o1: CustomToolbarButton, o2: CustomToolbarButton -> o1.index.compareTo(o2.index) }
            for (i in buttons.indices) {
                buttons[i].index = i
            }
            return buttons
        }

        fun toStringSet(buttons: ArrayList<CustomToolbarButton>): Set<String> {
            val ret = hashSetInit<String>(buttons.size)
            for (b in buttons) {
                val values = arrayOf(b.index.toString(), b.buttonText, b.prefix, b.suffix)
                for (i in values.indices) {
                    values[i] = values[i].replace(Consts.FIELD_SEPARATOR, "")
                }
                ret.add(values.joinToString(Consts.FIELD_SEPARATOR))
            }
            return ret
        }
    }
}
