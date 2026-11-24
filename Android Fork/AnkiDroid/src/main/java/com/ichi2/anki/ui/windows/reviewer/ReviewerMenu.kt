// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.view.Menu
import androidx.appcompat.view.menu.SubMenuBuilder
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.flowWithLifecycle
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.ReviewerMenuView
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.menu
import com.ichi2.anki.utils.ext.removeSubMenu
import com.ichi2.utils.setPaddedIcon
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

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
            .onEach { flagCode ->
                flagItem.setPaddedIcon(context, flagCode.drawableRes)
            }.launchIn(lifecycle.coroutineScope)
    }

    findItem(ViewerAction.MARK.menuId)?.let { markItem ->
        viewModel.isMarkedFlow
            .flowWithLifecycle(lifecycle)
            .onEach { isMarked ->
                if (isMarked) {
                    markItem.setPaddedIcon(context, R.drawable.ic_star)
                    markItem.setTitle(CommonString.menu_unmark_note)
                } else {
                    markItem.setPaddedIcon(context, R.drawable.ic_star_border_white)
                    markItem.title = with(context) { TR.sentenceCase.markNote }
                }
            }.launchIn(lifecycle.coroutineScope)
    }

    findItem(ViewerAction.UNDO.menuId)?.let { undoItem ->
        viewModel.undoLabelFlow
            .flowWithLifecycle(lifecycle)
            .onEach { label ->
                undoItem.title = label ?: CollectionManager.TR.undoUndo()
                undoItem.isEnabled = label != null
            }.launchIn(lifecycle.coroutineScope)
    }

    findItem(ViewerAction.REDO.menuId)?.let { redoItem ->
        viewModel.redoLabelFlow
            .flowWithLifecycle(lifecycle)
            .onEach { label ->
                redoItem.title = label ?: CollectionManager.TR.undoRedo()
                redoItem.isEnabled = label != null
            }.launchIn(lifecycle.coroutineScope)
    }

    findItem(ViewerAction.SUSPEND_MENU.menuId)?.let { suspendItem ->
        val suspendFlow = viewModel.canSuspendNoteFlow.flowWithLifecycle(lifecycle)
        suspendFlow
            .onEach { canSuspendNote ->
                if (canSuspendNote) {
                    if (suspendItem.hasSubMenu()) return@onEach
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
            }.launchIn(lifecycle.coroutineScope)
    }

    findItem(ViewerAction.BURY_MENU.menuId)?.let { buryItem ->
        val buryFlow = viewModel.canBuryNoteFlow.flowWithLifecycle(lifecycle)
        buryFlow
            .onEach { canBuryNote ->
                if (canBuryNote) {
                    if (buryItem.hasSubMenu()) return@onEach
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
            }.launchIn(lifecycle.coroutineScope)
    }
    findItem(ViewerAction.RECORD_VOICE.menuId)?.let { recordVoiceItem ->
        val recordVoiceFlow = viewModel.voiceRecorderEnabledFlow.flowWithLifecycle(lifecycle)
        recordVoiceFlow
            .onEach { isEnabled ->
                if (isEnabled) {
                    recordVoiceItem.setPaddedIcon(context, R.drawable.ic_action_mic)
                    recordVoiceItem.setTitle(CommonString.disable_voice_recording)
                } else {
                    recordVoiceItem.setPaddedIcon(context, R.drawable.ic_mic_outlined)
                    recordVoiceItem.setTitle(CommonString.enable_voice_recording)
                }
            }.launchIn(lifecycle.coroutineScope)
    }

    findItem(ViewerAction.TOGGLE_WHITEBOARD.menuId)?.let { toggleWhiteboardItem ->
        val toggleWhiteboardFlow = viewModel.whiteboardEnabledFlow.flowWithLifecycle(lifecycle)
        toggleWhiteboardFlow
            .onEach { isEnabled ->
                if (isEnabled) {
                    toggleWhiteboardItem.setPaddedIcon(context, R.drawable.ic_draw_filled)
                    toggleWhiteboardItem.setTitle(CommonString.disable_whiteboard)
                } else {
                    toggleWhiteboardItem.setPaddedIcon(context, R.drawable.ic_enable_whiteboard)
                    toggleWhiteboardItem.setTitle(CommonString.enable_whiteboard)
                }
            }.launchIn(lifecycle.coroutineScope)
    }

    findItem(ViewerAction.TOGGLE_AUTO_ADVANCE.menuId)?.let { autoAdvanceItem ->
        val isAutoAdvancedEnabledFlow = viewModel.isAutoAdvanceEnabledFlow.flowWithLifecycle(lifecycle)
        isAutoAdvancedEnabledFlow
            .onEach { isEnabled ->
                if (isEnabled) {
                    autoAdvanceItem.setPaddedIcon(context, R.drawable.ic_fast_forward)
                    autoAdvanceItem.setTitle(CommonString.disable_auto_advance)
                } else {
                    autoAdvanceItem.setPaddedIcon(context, R.drawable.ic_fast_forward_outlined)
                    autoAdvanceItem.setTitle(CommonString.enable_auto_advance)
                }
            }.launchIn(lifecycle.coroutineScope)
    }
}
