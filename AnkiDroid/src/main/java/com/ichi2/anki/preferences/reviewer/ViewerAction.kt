/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.preferences.reviewer

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.preferences.reviewer.MenuDisplayType.ALWAYS
import com.ichi2.anki.preferences.reviewer.MenuDisplayType.DISABLED
import com.ichi2.anki.preferences.reviewer.MenuDisplayType.MENU_ONLY
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.Binding.AppDefinedModifierKeys
import com.ichi2.anki.reviewer.Binding.ModifierKeys
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.shift
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableAction
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.ui.internationalization.sentenceCase

/**
 * @param menuId menu Id of the action
 *
 * @param defaultDisplayType the default display type of the action in the toolbar.
 * Use `null` if the action is restricted to gestures/controls and shouldn't be in the menu,
 * or if the item has a [parentMenu].
 */
enum class ViewerAction(
    @IdRes val menuId: Int = 0,
    @DrawableRes val drawableRes: Int? = null,
    val defaultDisplayType: MenuDisplayType? = null,
    val parentMenu: ViewerAction? = null,
) : MappableAction<ReviewerBinding> {
    // Always
    UNDO(R.id.action_undo, R.drawable.ic_undo_white, ALWAYS),

    // Menu only
    REDO(R.id.action_redo, R.drawable.ic_redo, MENU_ONLY),
    FLAG_MENU(R.id.action_flag, R.drawable.ic_flag_transparent, MENU_ONLY),
    MARK(R.id.action_mark, R.drawable.ic_star, MENU_ONLY),
    EDIT(R.id.action_edit_note, R.drawable.ic_mode_edit_white, MENU_ONLY),
    BURY_MENU(R.id.action_bury, R.drawable.ic_flip_to_back_white, MENU_ONLY),
    SUSPEND_MENU(R.id.action_suspend, R.drawable.ic_suspend, MENU_ONLY),
    DELETE(R.id.action_delete, R.drawable.ic_delete_white, MENU_ONLY),
    TOGGLE_WHITEBOARD(R.id.action_toggle_whiteboard, R.drawable.ic_enable_whiteboard, MENU_ONLY),

    // Disabled
    BROWSE(R.id.action_browse, R.drawable.ic_flashcard_black, DISABLED),
    STATISTICS(R.id.action_statistics, R.drawable.ic_bar_chart_black, DISABLED),
    DECK_OPTIONS(R.id.action_deck_options, R.drawable.ic_tune_white, DISABLED),
    CARD_INFO(R.id.action_card_info, R.drawable.ic_dialog_info, DISABLED),
    PREVIOUS_CARD_INFO(R.id.action_previous_card_info, R.drawable.ic_outline_info_24, DISABLED),
    ADD_NOTE(R.id.action_add_note, R.drawable.ic_add, DISABLED),
    TAG(R.id.action_edit_tags, R.drawable.ic_tag, DISABLED),
    RESCHEDULE_NOTE(R.id.action_set_due_date, R.drawable.ic_reschedule, DISABLED),
    RESET_PROGRESS(R.id.action_reset_progress, R.drawable.ic_backup_restore, DISABLED),
    TOGGLE_AUTO_ADVANCE(R.id.action_toggle_auto_advance, R.drawable.ic_fast_forward_outlined, DISABLED),
    RECORD_VOICE(R.id.action_record_voice, R.drawable.ic_mic_outlined, DISABLED),
    PLAY_MEDIA(R.id.action_replay_media, R.drawable.ic_play_circle_white, DISABLED),
    USER_ACTION_1(R.id.user_action_1, R.drawable.user_action_1, DISABLED),
    USER_ACTION_2(R.id.user_action_2, R.drawable.user_action_2, DISABLED),
    USER_ACTION_3(R.id.user_action_3, R.drawable.user_action_3, DISABLED),
    USER_ACTION_4(R.id.user_action_4, R.drawable.user_action_4, DISABLED),
    USER_ACTION_5(R.id.user_action_5, R.drawable.user_action_5, DISABLED),
    USER_ACTION_6(R.id.user_action_6, R.drawable.user_action_6, DISABLED),
    USER_ACTION_7(R.id.user_action_7, R.drawable.user_action_7, DISABLED),
    USER_ACTION_8(R.id.user_action_8, R.drawable.user_action_8, DISABLED),
    USER_ACTION_9(R.id.user_action_9, R.drawable.user_action_9, DISABLED),

    // Child items
    BURY_NOTE(R.id.action_bury_note, drawableRes = null, parentMenu = BURY_MENU),
    BURY_CARD(R.id.action_bury_card, drawableRes = null, parentMenu = BURY_MENU),
    SUSPEND_NOTE(R.id.action_suspend_note, drawableRes = null, parentMenu = SUSPEND_MENU),
    SUSPEND_CARD(R.id.action_suspend_card, drawableRes = null, parentMenu = SUSPEND_MENU),
    UNSET_FLAG(Flag.NONE.id, Flag.NONE.drawableRes, parentMenu = FLAG_MENU),
    FLAG_RED(Flag.RED.id, Flag.RED.drawableRes, parentMenu = FLAG_MENU),
    FLAG_ORANGE(Flag.ORANGE.id, Flag.ORANGE.drawableRes, parentMenu = FLAG_MENU),
    FLAG_GREEN(Flag.GREEN.id, Flag.GREEN.drawableRes, parentMenu = FLAG_MENU),
    FLAG_BLUE(Flag.BLUE.id, Flag.BLUE.drawableRes, parentMenu = FLAG_MENU),
    FLAG_PINK(Flag.PINK.id, Flag.PINK.drawableRes, parentMenu = FLAG_MENU),
    FLAG_TURQUOISE(Flag.TURQUOISE.id, Flag.TURQUOISE.drawableRes, parentMenu = FLAG_MENU),
    FLAG_PURPLE(Flag.PURPLE.id, Flag.PURPLE.drawableRes, parentMenu = FLAG_MENU),

    // Command only
    SHOW_ANSWER,
    ANSWER_AGAIN,
    ANSWER_HARD,
    ANSWER_GOOD,
    ANSWER_EASY,
    TOGGLE_FLAG_RED,
    TOGGLE_FLAG_ORANGE,
    TOGGLE_FLAG_GREEN,
    TOGGLE_FLAG_BLUE,
    TOGGLE_FLAG_PINK,
    TOGGLE_FLAG_TURQUOISE,
    TOGGLE_FLAG_PURPLE,
    SHOW_HINT,
    SHOW_ALL_HINTS,
    REPLAY_VOICE,
    PAGE_UP,
    PAGE_DOWN,
    EXIT,
    ;

    override val preferenceKey: String get() = "binding_$name"

    override fun getBindings(prefs: SharedPreferences): List<ReviewerBinding> {
        val prefValue = prefs.getString(preferenceKey, null) ?: return defaultBindings
        return ReviewerBinding.fromPreferenceString(prefValue)
    }

    private val defaultBindings: List<ReviewerBinding> get() =
        when (this) {
            UNDO -> listOf(keycode(KeyEvent.KEYCODE_Z, ctrl()))
            REDO -> listOf(keycode(KeyEvent.KEYCODE_Z, ModifierKeys(shift = true, ctrl = true, alt = false)))
            MARK -> listOf(unicode('*'))
            EDIT -> listOf(keycode(KeyEvent.KEYCODE_E))
            ADD_NOTE -> listOf(keycode(KeyEvent.KEYCODE_A))
            BURY_NOTE -> listOf(unicode('='))
            BURY_CARD -> listOf(unicode('-'))
            SUSPEND_NOTE -> listOf(unicode('!'))
            SUSPEND_CARD -> listOf(unicode('@'))
            TOGGLE_AUTO_ADVANCE -> listOf(keycode(KeyEvent.KEYCODE_A, shift()))
            SHOW_HINT -> listOf(keycode(KeyEvent.KEYCODE_H))
            SHOW_ALL_HINTS -> listOf(keycode(KeyEvent.KEYCODE_G))
            RECORD_VOICE -> listOf(keycode(KeyEvent.KEYCODE_V, shift()))
            REPLAY_VOICE -> listOf(keycode(KeyEvent.KEYCODE_V))
            BROWSE -> listOf(keycode(KeyEvent.KEYCODE_B))
            STATISTICS -> listOf(keycode(KeyEvent.KEYCODE_T))
            PLAY_MEDIA -> listOf(keycode(KeyEvent.KEYCODE_R))
            PREVIOUS_CARD_INFO -> listOf(keycode(KeyEvent.KEYCODE_I, ModifierKeys(shift = false, ctrl = true, alt = true)))
            RESET_PROGRESS -> listOf(keycode(KeyEvent.KEYCODE_N, ModifierKeys(ctrl = true, alt = true, shift = false)))
            TOGGLE_FLAG_RED ->
                listOf(
                    keycode(KeyEvent.KEYCODE_1, ctrl()),
                    keycode(KeyEvent.KEYCODE_NUMPAD_1, ctrl()),
                )
            TOGGLE_FLAG_ORANGE ->
                listOf(
                    keycode(KeyEvent.KEYCODE_2, ctrl()),
                    keycode(KeyEvent.KEYCODE_NUMPAD_2, ctrl()),
                )
            TOGGLE_FLAG_GREEN ->
                listOf(
                    keycode(KeyEvent.KEYCODE_3, ctrl()),
                    keycode(KeyEvent.KEYCODE_NUMPAD_3, ctrl()),
                )
            TOGGLE_FLAG_BLUE ->
                listOf(
                    keycode(KeyEvent.KEYCODE_4, ctrl()),
                    keycode(KeyEvent.KEYCODE_NUMPAD_4, ctrl()),
                )
            TOGGLE_FLAG_PINK ->
                listOf(
                    keycode(KeyEvent.KEYCODE_5, ctrl()),
                    keycode(KeyEvent.KEYCODE_NUMPAD_5, ctrl()),
                )
            TOGGLE_FLAG_TURQUOISE ->
                listOf(
                    keycode(KeyEvent.KEYCODE_6, ctrl()),
                    keycode(KeyEvent.KEYCODE_NUMPAD_6, ctrl()),
                )
            TOGGLE_FLAG_PURPLE ->
                listOf(
                    keycode(KeyEvent.KEYCODE_7, ctrl()),
                    keycode(KeyEvent.KEYCODE_NUMPAD_7, ctrl()),
                )
            ANSWER_AGAIN ->
                listOf(
                    keycode(KeyEvent.KEYCODE_BUTTON_Y, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_1, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_NUMPAD_1, side = CardSide.ANSWER),
                )
            ANSWER_HARD ->
                listOf(
                    keycode(KeyEvent.KEYCODE_BUTTON_X, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_2, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_NUMPAD_2, side = CardSide.ANSWER),
                )
            ANSWER_GOOD ->
                listOf(
                    keycode(KeyEvent.KEYCODE_BUTTON_B, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_3, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_NUMPAD_3, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_DPAD_CENTER, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_SPACE, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_ENTER, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_NUMPAD_ENTER, side = CardSide.ANSWER),
                )
            ANSWER_EASY ->
                listOf(
                    keycode(KeyEvent.KEYCODE_BUTTON_A, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_4, side = CardSide.ANSWER),
                    keycode(KeyEvent.KEYCODE_NUMPAD_4, side = CardSide.ANSWER),
                )
            SHOW_ANSWER -> {
                listOf(
                    keycode(KeyEvent.KEYCODE_SPACE, side = CardSide.QUESTION),
                    keycode(KeyEvent.KEYCODE_ENTER, side = CardSide.QUESTION),
                    keycode(KeyEvent.KEYCODE_NUMPAD_ENTER, side = CardSide.QUESTION),
                )
            }
            // No default gestures
            DELETE,
            CARD_INFO,
            TAG,
            EXIT,
            RESCHEDULE_NOTE,
            TOGGLE_WHITEBOARD,
            PAGE_UP,
            PAGE_DOWN,
            USER_ACTION_1,
            USER_ACTION_2,
            USER_ACTION_3,
            USER_ACTION_4,
            USER_ACTION_5,
            USER_ACTION_6,
            USER_ACTION_7,
            USER_ACTION_8,
            USER_ACTION_9,
            // Menu flag actions. They set the flag, but don't toggle it
            UNSET_FLAG,
            FLAG_RED,
            FLAG_ORANGE,
            FLAG_BLUE,
            FLAG_GREEN,
            FLAG_PINK,
            FLAG_TURQUOISE,
            FLAG_PURPLE,
            // Menu only
            DECK_OPTIONS,
            BURY_MENU,
            SUSPEND_MENU,
            FLAG_MENU,
            -> emptyList()
        }

    fun isSubMenu() = ViewerAction.entries.any { it.parentMenu == this }

    fun title(context: Context): String =
        with(context) {
            when (this@ViewerAction) {
                BROWSE -> TR.qtMiscBrowse()
                STATISTICS -> TR.statisticsTitle()
                RESCHEDULE_NOTE -> TR.sentenceCase.setDueDate
                PREVIOUS_CARD_INFO -> TR.sentenceCase.previousCardInfo
                UNDO -> getString(R.string.undo)
                REDO -> getString(R.string.redo)
                FLAG_MENU -> TR.browsingFlag()
                MARK -> TR.sentenceCase.markNote
                EDIT -> getString(R.string.cardeditor_title_edit_card)
                BURY_MENU -> TR.studyingBury()
                SUSPEND_MENU -> TR.studyingSuspend()
                DELETE -> TR.sentenceCase.deleteNote
                TOGGLE_WHITEBOARD -> getString(R.string.gesture_toggle_whiteboard)
                DECK_OPTIONS -> getString(R.string.menu__deck_options)
                CARD_INFO -> TR.sentenceCase.cardInfo
                ADD_NOTE -> getString(R.string.menu_add_note)
                TAG -> getString(R.string.menu_edit_tags)
                RESET_PROGRESS -> getString(R.string.card_editor_reset_card)
                TOGGLE_AUTO_ADVANCE -> getString(R.string.toggle_auto_advance)
                RECORD_VOICE -> getString(R.string.record_voice)
                PLAY_MEDIA -> getString(R.string.replay_media)
                USER_ACTION_1 -> getString(R.string.user_action_1)
                USER_ACTION_2 -> getString(R.string.user_action_2)
                USER_ACTION_3 -> getString(R.string.user_action_3)
                USER_ACTION_4 -> getString(R.string.user_action_4)
                USER_ACTION_5 -> getString(R.string.user_action_5)
                USER_ACTION_6 -> getString(R.string.user_action_6)
                USER_ACTION_7 -> getString(R.string.user_action_7)
                USER_ACTION_8 -> getString(R.string.user_action_8)
                USER_ACTION_9 -> getString(R.string.user_action_9)
                BURY_NOTE -> TR.sentenceCase.buryNote
                BURY_CARD -> TR.sentenceCase.buryCard
                SUSPEND_NOTE -> TR.sentenceCase.suspendNote
                SUSPEND_CARD -> TR.sentenceCase.suspendCard
                UNSET_FLAG,
                FLAG_RED,
                FLAG_ORANGE,
                FLAG_GREEN,
                FLAG_BLUE,
                FLAG_PINK,
                FLAG_TURQUOISE,
                FLAG_PURPLE,
                SHOW_ANSWER,
                ANSWER_AGAIN,
                ANSWER_HARD,
                ANSWER_GOOD,
                ANSWER_EASY,
                TOGGLE_FLAG_RED,
                TOGGLE_FLAG_ORANGE,
                TOGGLE_FLAG_GREEN,
                TOGGLE_FLAG_BLUE,
                TOGGLE_FLAG_PINK,
                TOGGLE_FLAG_TURQUOISE,
                TOGGLE_FLAG_PURPLE,
                SHOW_HINT,
                SHOW_ALL_HINTS,
                REPLAY_VOICE,
                PAGE_UP,
                PAGE_DOWN,
                EXIT,
                -> getString(R.string.empty_string)
            }
        }

    private fun keycode(
        keycode: Int,
        keys: ModifierKeys = ModifierKeys.none(),
        side: CardSide = CardSide.BOTH,
    ): ReviewerBinding {
        val binding = Binding.keyCode(keycode, keys)
        return ReviewerBinding(binding = binding, side = side)
    }

    private fun unicode(
        unicodeChar: Char,
        keys: ModifierKeys = AppDefinedModifierKeys.allowShift(),
        side: CardSide = CardSide.BOTH,
    ): ReviewerBinding {
        val binding = Binding.unicode(unicodeChar, keys)
        return ReviewerBinding(binding = binding, side = side)
    }

    companion object {
        fun fromId(
            @IdRes id: Int,
        ): ViewerAction = entries.first { it.menuId == id }

        fun fromPreferenceKey(preferenceKey: String): ViewerAction? = entries.firstOrNull { it.preferenceKey == preferenceKey }

        fun getSubMenus(): List<ViewerAction> = ViewerAction.entries.mapNotNull { it.parentMenu }.distinct()
    }
}
