/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.contextmenu

import android.content.Context

class AnkiCardContextMenu(
    context: Context,
) : SystemContextMenu(context) {
    override val activityName: String
        get() = "com.ichi2.anki.AnkiCardContextMenuAction"

    companion object {
        fun ensureConsistentStateWithPreferenceStatus(
            context: Context,
            preferenceStatus: Boolean,
        ) {
            AnkiCardContextMenu(context).ensureConsistentStateWithPreferenceStatus(preferenceStatus)
        }
    }
}
