// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.noteeditor.presentation.ToolbarLayoutMode

private const val PREF_NOTE_EDITOR_SCROLL_TOOLBAR = "noteEditorScrollToolbar"
private const val PREF_NOTE_EDITOR_SHOW_TOOLBAR = "noteEditorShowToolbar"

/**
 * Persists the toolbar layout using the existing show/scroll preferences.
 *
 * TODO: migrate to a single preference value.
 */
class ToolbarPreferences(
    private val preferences: SharedPreferences,
) {
    val isVisible: Boolean
        get() = preferences.getBoolean(PREF_NOTE_EDITOR_SHOW_TOOLBAR, true)

    /** The layout to use when visible, retained while the toolbar is hidden. */
    val isScrollable: Boolean
        get() = preferences.getBoolean(PREF_NOTE_EDITOR_SCROLL_TOOLBAR, true)

    var layout: ToolbarLayoutMode
        get() =
            when {
                !isVisible -> ToolbarLayoutMode.HIDDEN
                isScrollable -> ToolbarLayoutMode.SCROLL
                else -> ToolbarLayoutMode.STACK
            }
        set(value) {
            preferences.edit {
                putBoolean(PREF_NOTE_EDITOR_SHOW_TOOLBAR, value != ToolbarLayoutMode.HIDDEN)
                if (value != ToolbarLayoutMode.HIDDEN) {
                    putBoolean(PREF_NOTE_EDITOR_SCROLL_TOOLBAR, value == ToolbarLayoutMode.SCROLL)
                }
            }
        }
}
