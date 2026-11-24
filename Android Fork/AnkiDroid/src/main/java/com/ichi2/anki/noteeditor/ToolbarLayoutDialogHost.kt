// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.ichi2.anki.noteeditor.data.ToolbarPreferences
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.themes.Themes

private const val STATE_TOOLBAR_LAYOUT_DIALOG = "toolbarLayoutDialog"

/**
 * Hosts the 'Toolbar layout' dialog and restores its visibility after recreation.
 *
 * @see ToolbarLayoutDialog
 */
internal class ToolbarLayoutDialogHost {
    private var isShowing by mutableStateOf(false)

    fun bind(
        view: ComposeView,
        preferences: ToolbarPreferences,
        savedInstanceState: Bundle?,
        onChanged: () -> Unit,
    ) {
        isShowing = savedInstanceState?.getBoolean(STATE_TOOLBAR_LAYOUT_DIALOG) ?: false
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            AnkiDroidTheme {
                if (isShowing) {
                    ToolbarLayoutDialog(
                        selected = preferences.layout,
                        onSelect = { layout ->
                            preferences.layout = layout
                            isShowing = false
                            onChanged()
                        },
                        onDismiss = { isShowing = false },
                        showSelectionBackground = Themes.currentTheme != DayTheme.EINK && Themes.currentTheme != NightTheme.EINK,
                    )
                }
            }
        }
    }

    fun show() {
        isShowing = true
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_TOOLBAR_LAYOUT_DIALOG, isShowing)
    }
}
