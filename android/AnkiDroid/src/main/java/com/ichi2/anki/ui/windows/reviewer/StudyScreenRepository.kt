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

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.preferences.reviewer.MenuDisplayType
import com.ichi2.anki.preferences.reviewer.ReviewerMenuRepository
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.enums.ToolbarPosition
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.ext.cardStateCustomizer
import timber.log.Timber
import java.net.BindException
import java.net.ServerSocket

class StudyScreenRepository(
    private val prefs: PrefsRepository = Prefs,
) {
    val isMarkShownInToolbar: Boolean
    val isFlagShownInToolbar: Boolean
    var isWhiteboardEnabled by prefs.booleanPref(KEY_WHITEBOARD_ENABLED, false)
    var isRecordVoiceEnabled by prefs.booleanPref(KEY_RECORD_VOICE_ENABLED, false)
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

    fun getServerPort(): Int {
        if (!prefs.useFixedPortInReviewer) return 0
        return try {
            ServerSocket(prefs.reviewerPort)
                .use {
                    it.reuseAddress = true
                    it.localPort
                }.also {
                    if (prefs.reviewerPort == 0) {
                        prefs.reviewerPort = it
                    }
                }
        } catch (_: BindException) {
            Timber.w("Fixed port %d under use. Using dynamic port", prefs.reviewerPort)
            0
        }
    }

    fun generateStateMutationKey(): String = TimeManager.time.intTimeMS().toString()

    suspend fun getCustomSchedulingJs(): String = CollectionManager.withCol { cardStateCustomizer }

    suspend fun getShouldShowNextTimes(): Boolean = CollectionPreferences.getShowIntervalOnButtons()

    companion object {
        private const val KEY_WHITEBOARD_ENABLED = "whiteboardEnabled"
        private const val KEY_RECORD_VOICE_ENABLED = "recordVoiceEnabled"
    }
}
