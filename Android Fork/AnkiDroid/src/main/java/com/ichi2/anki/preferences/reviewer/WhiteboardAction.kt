// SPDX-License-Identifier: GPL-3.0-or-later

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
