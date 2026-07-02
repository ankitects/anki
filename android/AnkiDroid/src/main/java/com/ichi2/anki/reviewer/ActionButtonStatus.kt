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

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.IdRes
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.HashUtil.hashMapInit

// loads of unboxing issues, which are safe
class ActionButtonStatus {
    /**
     * Custom button allocation
     */
    private val customButtons: MutableMap<Int, Int> = hashMapInit(25) // setup's size

    fun setup(preferences: SharedPreferences) {
        // NOTE: the default values below should be in sync with preferences_custom_buttons.xml and reviewer.xml
        setupButton(preferences, R.id.action_undo, "customButtonUndo", SHOW_AS_ACTION_ALWAYS)
        setupButton(preferences, R.id.action_redo, "customButtonRedo", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_schedule, "customButtonScheduleCard", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_flag, "customButtonFlag", SHOW_AS_ACTION_ALWAYS)
        setupButton(preferences, R.id.action_tag, "customButtonTags", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_edit, "customButtonEditCard", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_add_note_reviewer, "customButtonAddCard", MENU_DISABLED)
        setupButton(preferences, R.id.action_replay, "customButtonReplay", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_card_info, "customButtonCardInfo", MENU_DISABLED)
        setupButton(preferences, R.id.action_previous_card_info, "customButtonPreviousCardInfo", MENU_DISABLED)
        setupButton(preferences, R.id.action_clear_whiteboard, "customButtonClearWhiteboard", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_hide_whiteboard, "customButtonShowHideWhiteboard", SHOW_AS_ACTION_ALWAYS)
        setupButton(preferences, R.id.action_select_tts, "customButtonSelectTts", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_open_deck_options, "customButtonDeckOptions", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_bury, "customButtonBury", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_bury_card, "customButtonBury", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_suspend, "customButtonSuspend", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_suspend_card, "customButtonSuspend", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_mark_card, "customButtonMarkCard", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_delete, "customButtonDelete", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_toggle_mic_tool_bar, "customButtonToggleMicToolBar", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_toggle_whiteboard, "customButtonEnableWhiteboard", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_toggle_eraser, "customButtonToggleEraser", SHOW_AS_ACTION_ALWAYS)
        setupButton(preferences, R.id.action_toggle_stylus, "customButtonToggleStylus", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_save_whiteboard, "customButtonSaveWhiteboard", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.action_change_whiteboard_pen_color, "customButtonWhiteboardPenColor", SHOW_AS_ACTION_IF_ROOM)
        setupButton(preferences, R.id.action_toggle_auto_advance, "customButtonToggleAutoAdvance", SHOW_AS_ACTION_NEVER)
        setupButton(preferences, R.id.user_action_1, "customButtonUserAction1", MENU_DISABLED)
        setupButton(preferences, R.id.user_action_2, "customButtonUserAction2", MENU_DISABLED)
        setupButton(preferences, R.id.user_action_3, "customButtonUserAction3", MENU_DISABLED)
        setupButton(preferences, R.id.user_action_4, "customButtonUserAction4", MENU_DISABLED)
        setupButton(preferences, R.id.user_action_5, "customButtonUserAction5", MENU_DISABLED)
        setupButton(preferences, R.id.user_action_6, "customButtonUserAction6", MENU_DISABLED)
        setupButton(preferences, R.id.user_action_7, "customButtonUserAction7", MENU_DISABLED)
        setupButton(preferences, R.id.user_action_8, "customButtonUserAction8", MENU_DISABLED)
        setupButton(preferences, R.id.user_action_9, "customButtonUserAction9", MENU_DISABLED)
    }

    private fun setupButton(
        preferences: SharedPreferences,
        @IdRes resourceId: Int,
        preferenceName: String,
        showAsActionType: Int,
    ) {
        customButtons[resourceId] =
            preferences
                .getString(
                    preferenceName,
                    showAsActionType.toString(),
                )!!
                .toInt()
    }

    fun setCustomButtons(menu: Menu) {
        for ((itemId, value) in customButtons) {
            if (value != MENU_DISABLED) {
                val item = menu.findItem(itemId)
                item.setShowAsAction(value)
            } else {
                menu.findItem(itemId).isVisible = false
            }
        }
    }

    fun hideWhiteboardIsDisabled(): Boolean = customButtons[R.id.action_hide_whiteboard] == MENU_DISABLED

    fun toggleEraserIsDisabled(): Boolean = customButtons[R.id.action_toggle_eraser] == MENU_DISABLED

    fun toggleStylusIsDisabled(): Boolean = customButtons[R.id.action_toggle_stylus] == MENU_DISABLED

    fun clearWhiteboardIsDisabled(): Boolean = customButtons[R.id.action_clear_whiteboard] == MENU_DISABLED

    fun selectTtsIsDisabled(): Boolean = customButtons[R.id.action_select_tts] == MENU_DISABLED

    fun saveWhiteboardIsDisabled(): Boolean = customButtons[R.id.action_save_whiteboard] == MENU_DISABLED

    fun whiteboardPenColorIsDisabled(): Boolean = customButtons[R.id.action_change_whiteboard_pen_color] == MENU_DISABLED

    fun suspendIsDisabled(): Boolean = customButtons[R.id.action_suspend] == MENU_DISABLED

    fun buryIsDisabled(): Boolean = customButtons[R.id.action_bury] == MENU_DISABLED

    fun flagsIsOverflown(): Boolean = customButtons[R.id.action_flag] == SHOW_AS_ACTION_NEVER

    fun autoAdvanceMenuIsNeverShown(): Boolean = customButtons[R.id.action_toggle_auto_advance] == MENU_DISABLED

    companion object {
        const val SHOW_AS_ACTION_NEVER = MenuItem.SHOW_AS_ACTION_NEVER
        const val SHOW_AS_ACTION_IF_ROOM = MenuItem.SHOW_AS_ACTION_IF_ROOM
        const val SHOW_AS_ACTION_ALWAYS = MenuItem.SHOW_AS_ACTION_ALWAYS
        const val MENU_DISABLED = 3
    }
}
