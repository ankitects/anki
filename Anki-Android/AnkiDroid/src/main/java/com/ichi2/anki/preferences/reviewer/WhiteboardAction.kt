/*
 * Copyright (c) 2026 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences.reviewer

import android.content.SharedPreferences
import com.ichi2.anki.reviewer.MappableAction
import com.ichi2.anki.reviewer.ReviewerBinding

enum class WhiteboardAction : MappableAction<ReviewerBinding> {
    TOGGLE_ERASER,
    CLEAR,
    UNDO,
    REDO,
    ;

    override val preferenceKey: String get() = "binding_whiteboard_$name"

    override fun getBindings(prefs: SharedPreferences): List<ReviewerBinding> {
        val prefValue = prefs.getString(preferenceKey, null) ?: return emptyList()
        return ReviewerBinding.fromPreferenceString(prefValue)
    }
}
