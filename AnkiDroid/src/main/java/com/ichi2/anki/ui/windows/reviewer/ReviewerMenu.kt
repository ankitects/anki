/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
package com.ichi2.anki.ui.windows.reviewer

import android.view.Menu
import androidx.appcompat.view.menu.SubMenuBuilder
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.ReviewerMenuView
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.ext.menu
import com.ichi2.anki.utils.ext.removeSubMenu
import com.ichi2.utils.setPaddedIcon

fun ReviewerMenuView.setup(
    lifecycle: Lifecycle,
    viewModel: ReviewerViewModel,
) {
    if (isEmpty()) {
        isVisible = false
        return
    }

    findItem(ViewerAction.FLAG_MENU.menuId)?.let { flagItem ->
        viewModel.flagFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycle.coroutineScope) { flagCode ->
                flagItem.setPaddedIcon(context, flagCode.drawableRes)
            }
    }

    findItem(ViewerAction.MARK.menuId)?.let { markItem ->
        viewModel.isMarkedFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycle.coroutineScope) { isMarked ->
                if (isMarked) {
                    markItem.setPaddedIcon(context, R.drawable.ic_star)
                    markItem.setTitle(R.string.menu_unmark_note)
                } else {
                    markItem.setPaddedIcon(context, R.drawable.ic_star_border_white)
                    markItem.title = with(context) { TR.sentenceCase.markNote }
                }
            }
    }

    findItem(ViewerAction.UNDO.menuId)?.let { undoItem ->
        viewModel.undoLabelFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycle.coroutineScope) { label ->
                undoItem.title = label ?: CollectionManager.TR.undoUndo()
                undoItem.isEnabled = label != null
            }
    }

    findItem(ViewerAction.REDO.menuId)?.let { redoItem ->
        viewModel.redoLabelFlow
            .flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycle.coroutineScope) { label ->
                redoItem.title = label ?: CollectionManager.TR.undoRedo()
                redoItem.isEnabled = label != null
            }
    }

    findItem(ViewerAction.SUSPEND_MENU.menuId)?.let { suspendItem ->
        val suspendFlow = viewModel.canSuspendNoteFlow.flowWithLifecycle(lifecycle)
        suspendFlow.collectLatestIn(lifecycle.coroutineScope) { canSuspendNote ->
            if (canSuspendNote) {
                if (suspendItem.hasSubMenu()) return@collectLatestIn
                suspendItem.title = ViewerAction.SUSPEND_MENU.title(context)
                val submenu =
                    SubMenuBuilder(context, suspendItem.menu, suspendItem).apply {
                        add(Menu.NONE, ViewerAction.SUSPEND_NOTE.menuId, Menu.NONE, ViewerAction.SUSPEND_NOTE.title(context))
                        add(Menu.NONE, ViewerAction.SUSPEND_CARD.menuId, Menu.NONE, ViewerAction.SUSPEND_CARD.title(context))
                    }
                suspendItem.setSubMenu(submenu)
            } else {
                suspendItem.removeSubMenu()
                suspendItem.title = ViewerAction.SUSPEND_CARD.title(context)
            }
        }
    }

    findItem(ViewerAction.BURY_MENU.menuId)?.let { buryItem ->
        val buryFlow = viewModel.canBuryNoteFlow.flowWithLifecycle(lifecycle)
        buryFlow.collectLatestIn(lifecycle.coroutineScope) { canBuryNote ->
            if (canBuryNote) {
                if (buryItem.hasSubMenu()) return@collectLatestIn
                buryItem.title = ViewerAction.BURY_MENU.title(context)
                val submenu =
                    SubMenuBuilder(context, buryItem.menu, buryItem).apply {
                        add(Menu.NONE, ViewerAction.BURY_NOTE.menuId, Menu.NONE, ViewerAction.BURY_NOTE.title(context))
                        add(Menu.NONE, ViewerAction.BURY_CARD.menuId, Menu.NONE, ViewerAction.BURY_CARD.title(context))
                    }
                buryItem.setSubMenu(submenu)
            } else {
                buryItem.removeSubMenu()
                buryItem.title = ViewerAction.BURY_CARD.title(context)
            }
        }
    }
    findItem(ViewerAction.RECORD_VOICE.menuId)?.let { recordVoiceItem ->
        val recordVoiceFlow = viewModel.voiceRecorderEnabledFlow.flowWithLifecycle(lifecycle)
        recordVoiceFlow.collectLatestIn(lifecycle.coroutineScope) { isEnabled ->
            if (isEnabled) {
                recordVoiceItem.setPaddedIcon(context, R.drawable.ic_action_mic)
                recordVoiceItem.setTitle(R.string.disable_voice_recording)
            } else {
                recordVoiceItem.setPaddedIcon(context, R.drawable.ic_mic_outlined)
                recordVoiceItem.setTitle(R.string.enable_voice_recording)
            }
        }
    }

    findItem(ViewerAction.TOGGLE_WHITEBOARD.menuId)?.let { toggleWhiteboardItem ->
        val toggleWhiteboardFlow = viewModel.whiteboardEnabledFlow.flowWithLifecycle(lifecycle)
        toggleWhiteboardFlow.collectLatestIn(lifecycle.coroutineScope) { isEnabled ->
            if (isEnabled) {
                toggleWhiteboardItem.setPaddedIcon(context, R.drawable.ic_draw_filled)
                toggleWhiteboardItem.setTitle(R.string.disable_whiteboard)
            } else {
                toggleWhiteboardItem.setPaddedIcon(context, R.drawable.ic_enable_whiteboard)
                toggleWhiteboardItem.setTitle(R.string.enable_whiteboard)
            }
        }
    }

    findItem(ViewerAction.TOGGLE_AUTO_ADVANCE.menuId)?.let { autoAdvanceItem ->
        val isAutoAdvancedEnabledFlow = viewModel.isAutoAdvanceEnabledFlow.flowWithLifecycle(lifecycle)
        isAutoAdvancedEnabledFlow.collectLatestIn(lifecycle.coroutineScope) { isEnabled ->
            if (isEnabled) {
                autoAdvanceItem.setPaddedIcon(context, R.drawable.ic_fast_forward)
                autoAdvanceItem.setTitle(R.string.disable_auto_advance)
            } else {
                autoAdvanceItem.setPaddedIcon(context, R.drawable.ic_fast_forward_outlined)
                autoAdvanceItem.setTitle(R.string.enable_auto_advance)
            }
        }
    }
}
