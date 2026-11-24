// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.ext.cardStateCustomizer
import com.ichi2.anki.preferences.reviewer.MenuDisplayType
import com.ichi2.anki.preferences.reviewer.ReviewerMenuRepository
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.ToolbarPosition
import com.ichi2.anki.utils.CollectionPreferences

class StudyScreenRepository(
    private val prefs: PrefsRepository = Prefs,
) {
    val isMarkShownInToolbar: Boolean
    val isFlagShownInToolbar: Boolean
    var isWhiteboardEnabled by prefs.booleanPref(PREF_WHITEBOARD_ENABLED, false)
    var isRecordVoiceEnabled by prefs.booleanPref(PREF_RECORD_VOICE_ENABLED, false)
    val isHtmlTypeAnswerEnabled get() = prefs.isHtmlTypeAnswerEnabled

    init {
        val actions =
            ReviewerMenuRepository(prefs.sharedPrefs)
                .getActionsByMenuDisplayTypes(
                    MenuDisplayType.ALWAYS,
                ).getValue(MenuDisplayType.ALWAYS)
        val isToolbarShown = prefs.toolbarPosition != ToolbarPosition.NONE
        isMarkShownInToolbar = isToolbarShown && ViewerAction.MARK in actions
        isFlagShownInToolbar = isToolbarShown && ViewerAction.FLAG_MENU in actions
    }

    fun generateStateMutationKey(): String = TimeManager.time.intTimeMS().toString()

    suspend fun getCustomSchedulingJs(): String = CollectionManager.withCol { config.cardStateCustomizer }

    suspend fun getShouldShowNextTimes(): Boolean = CollectionPreferences.getShowIntervalOnButtons()

    companion object {
        private const val PREF_WHITEBOARD_ENABLED = "whiteboardEnabled"
        private const val PREF_RECORD_VOICE_ENABLED = "recordVoiceEnabled"
    }
}
