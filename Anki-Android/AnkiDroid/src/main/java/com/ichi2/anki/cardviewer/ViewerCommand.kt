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
package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import com.ichi2.anki.reviewer.Binding.Companion.keyCode
import com.ichi2.anki.reviewer.Binding.Companion.unicode
import com.ichi2.anki.reviewer.Binding.ModifierKeys
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.ctrl
import com.ichi2.anki.reviewer.Binding.ModifierKeys.Companion.shift
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableAction
import com.ichi2.anki.reviewer.ReviewerBinding

/** Abstraction: Discuss moving many of these to 'Reviewer'  */
enum class ViewerCommand : MappableAction<ReviewerBinding> {
    SHOW_ANSWER,
    ANSWER_AGAIN,
    ANSWER_HARD,
    ANSWER_GOOD,
    ANSWER_EASY,
    UNDO,
    REDO,
    EDIT,
    MARK,
    BURY_CARD,
    SUSPEND_CARD,
    DELETE,
    PLAY_MEDIA,
    EXIT,
    BURY_NOTE,
    SUSPEND_NOTE,
    TOGGLE_FLAG_RED,
    TOGGLE_FLAG_ORANGE,
    TOGGLE_FLAG_GREEN,
    TOGGLE_FLAG_BLUE,
    TOGGLE_FLAG_PINK,
    TOGGLE_FLAG_TURQUOISE,
    TOGGLE_FLAG_PURPLE,
    UNSET_FLAG,
    PAGE_UP,
    PAGE_DOWN,
    TAG,
    CARD_INFO,
    PREVIOUS_CARD_INFO,
    RECORD_VOICE,
    SAVE_VOICE,
    REPLAY_VOICE,
    TOGGLE_WHITEBOARD,
    TOGGLE_ERASER,
    CLEAR_WHITEBOARD,
    CHANGE_WHITEBOARD_PEN_COLOR,
    SHOW_HINT,
    SHOW_ALL_HINTS,
    ADD_NOTE,
    RESCHEDULE_NOTE,
    TOGGLE_AUTO_ADVANCE,
    USER_ACTION_1,
    USER_ACTION_2,
    USER_ACTION_3,
    USER_ACTION_4,
    USER_ACTION_5,
    USER_ACTION_6,
    USER_ACTION_7,
    USER_ACTION_8,
    USER_ACTION_9,
    ;

    override val preferenceKey: String
        get() = "binding_$name"

    override fun getBindings(prefs: SharedPreferences): List<ReviewerBinding> {
        val prefValue = prefs.getString(preferenceKey, null) ?: return defaultValue
        return ReviewerBinding.fromPreferenceString(prefValue)
    }

    // If we use the serialised format, then this adds additional coupling to the properties.
    val defaultValue: List<ReviewerBinding>
        get() {
            return when (this) {
                ANSWER_AGAIN ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_BUTTON_Y, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_1, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.ANSWER),
                    )
                ANSWER_HARD ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_BUTTON_X, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_2, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.ANSWER),
                    )
                ANSWER_GOOD ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_BUTTON_B, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_3, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_DPAD_CENTER, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_SPACE, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_ENTER, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_ENTER, CardSide.ANSWER),
                    )
                ANSWER_EASY ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_BUTTON_A, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_4, CardSide.ANSWER),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.ANSWER),
                    )
                EDIT -> listOf(keyCode(KeyEvent.KEYCODE_E, CardSide.BOTH))
                MARK -> listOf(unicode('*', CardSide.BOTH))
                BURY_CARD -> listOf(unicode('-', CardSide.BOTH))
                BURY_NOTE -> listOf(unicode('=', CardSide.BOTH))
                SUSPEND_CARD -> listOf(unicode('@', CardSide.BOTH))
                SUSPEND_NOTE -> listOf(unicode('!', CardSide.BOTH))
                PLAY_MEDIA -> listOf(keyCode(KeyEvent.KEYCODE_R, CardSide.BOTH), keyCode(KeyEvent.KEYCODE_F5, CardSide.BOTH))
                REPLAY_VOICE -> listOf(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH))
                RECORD_VOICE -> listOf(keyCode(KeyEvent.KEYCODE_V, CardSide.BOTH, shift()))
                SAVE_VOICE -> listOf(keyCode(KeyEvent.KEYCODE_S, CardSide.BOTH, shift()))
                UNDO -> listOf(keyCode(KeyEvent.KEYCODE_Z, CardSide.BOTH, ctrl()))
                REDO -> listOf(keyCode(KeyEvent.KEYCODE_Z, CardSide.BOTH, ModifierKeys(shift = true, ctrl = true, alt = false)))
                PREVIOUS_CARD_INFO ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_I, CardSide.BOTH, ModifierKeys(shift = false, ctrl = true, alt = true)),
                    )
                TOGGLE_FLAG_RED ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_1, CardSide.BOTH, ctrl()),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_1, CardSide.BOTH, ctrl()),
                    )
                TOGGLE_FLAG_ORANGE ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_2, CardSide.BOTH, ctrl()),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_2, CardSide.BOTH, ctrl()),
                    )
                TOGGLE_FLAG_GREEN ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_3, CardSide.BOTH, ctrl()),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_3, CardSide.BOTH, ctrl()),
                    )
                TOGGLE_FLAG_BLUE ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_4, CardSide.BOTH, ctrl()),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_4, CardSide.BOTH, ctrl()),
                    )
                TOGGLE_FLAG_PINK ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_5, CardSide.BOTH, ctrl()),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_5, CardSide.BOTH, ctrl()),
                    )
                TOGGLE_FLAG_TURQUOISE ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_6, CardSide.BOTH, ctrl()),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_6, CardSide.BOTH, ctrl()),
                    )
                TOGGLE_FLAG_PURPLE ->
                    listOf(
                        keyCode(KeyEvent.KEYCODE_7, CardSide.BOTH, ctrl()),
                        keyCode(KeyEvent.KEYCODE_NUMPAD_7, CardSide.BOTH, ctrl()),
                    )
                TOGGLE_AUTO_ADVANCE -> listOf(keyCode(KeyEvent.KEYCODE_A, CardSide.BOTH, shift()))
                SHOW_HINT -> listOf(keyCode(KeyEvent.KEYCODE_H, CardSide.BOTH))
                SHOW_ALL_HINTS -> listOf(keyCode(KeyEvent.KEYCODE_G, CardSide.BOTH))
                ADD_NOTE -> listOf(keyCode(KeyEvent.KEYCODE_A, CardSide.BOTH))
                SHOW_ANSWER,
                DELETE,
                EXIT,
                UNSET_FLAG,
                PAGE_UP,
                PAGE_DOWN,
                TAG,
                CARD_INFO,
                TOGGLE_WHITEBOARD,
                TOGGLE_ERASER,
                CLEAR_WHITEBOARD,
                CHANGE_WHITEBOARD_PEN_COLOR,
                RESCHEDULE_NOTE,
                USER_ACTION_1,
                USER_ACTION_2,
                USER_ACTION_3,
                USER_ACTION_4,
                USER_ACTION_5,
                USER_ACTION_6,
                USER_ACTION_7,
                USER_ACTION_8,
                USER_ACTION_9,
                -> emptyList()
            }
        }

    private fun keyCode(
        keycode: Int,
        side: CardSide,
        keys: ModifierKeys = ModifierKeys.none(),
    ): ReviewerBinding = ReviewerBinding(keyCode(keys, keycode), side)

    private fun unicode(
        c: Char,
        side: CardSide,
    ): ReviewerBinding = ReviewerBinding(unicode(c), side)

    fun interface CommandProcessor {
        /**
         * @return whether the command was executed
         */
        fun executeCommand(
            which: ViewerCommand,
            fromGesture: Gesture?,
        ): Boolean
    }
}
