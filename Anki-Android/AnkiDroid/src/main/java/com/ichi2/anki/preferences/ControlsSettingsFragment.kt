// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.preferences

import android.content.res.Configuration
import androidx.annotation.XmlRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.lifecycle.withStarted
import androidx.preference.Preference
import androidx.preference.children
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.PreviewerAction
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableAction
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.preferences.ControlPreference
import com.ichi2.preferences.ReviewerControlPreference
import com.ichi2.utils.show
import kotlinx.coroutines.launch
import timber.log.Timber

class ControlsSettingsFragment :
    SettingsFragment(),
    TabLayout.OnTabSelectedListener {
    override val preferenceResource: Int
        get() = R.xml.preferences_controls
    override val analyticsScreenNameConstant: String
        get() = "prefs.controls"

    override fun initSubscreen() {
        requirePreference<ControlsTabPreference>(R.string.pref_controls_tab_layout_key).setOnTabSelectedListener(this)
        val initialScreen = ControlPreferenceScreen.entries.first()
        addPreferencesFromResource(initialScreen.xmlRes)
        // TODO replace the preference with something dismissible. This is meant only to improve
        //  the discoverability of the system shortcut for the shortcuts dialog.
        requirePreference<Preference>(R.string.pref_keyboard_shortcuts_key).apply {
            isVisible = resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY
            setOnPreferenceClickListener {
                requireActivity().requestShowKeyboardShortcuts()
                true
            }
        }
        setControlPreferencesDefaultValues(initialScreen)
        setDynamicTitle()
        setupNewStudyScreenSettings()
        setupAnswerCommands()
    }

    /**
     * Selects the appropriate tab based on a preference key from search results.
     * This allows search navigation to automatically switch to the correct tab.
     */
    fun selectTabForPreference(key: String) {
        val targetTabIndex = actionToScreenMap[key]?.ordinal
        if (targetTabIndex == null) {
            Timber.w("Could not find the preference with %s key", key)
            return
        }

        view?.post {
            requirePreference<ControlsTabPreference>(
                R.string.pref_controls_tab_layout_key,
            ).selectTab(targetTabIndex)
        }
    }

    /**
     * Highlights a search result with proper lifecycle handling.
     *
     * This handles the specific case where a tab must be selected before highlighting,
     * ensuring the fragment is in the appropriate lifecycle states.
     */
    fun highlightPreference(result: SearchPreferenceResult) {
        lifecycleScope.launch {
            withStarted {
                selectTabForPreference(result.key)
            }
            withResumed {
                view?.post {
                    result.highlight(this@ControlsSettingsFragment)
                }
            }
        }
    }

    private fun setControlPreferencesDefaultValues(screen: ControlPreferenceScreen) {
        val commands = screen.getActions().associateBy { it.preferenceKey }
        val prefs = sharedPrefs()
        preferenceScreen
            .allPreferences()
            .filterIsInstance<ControlPreference>()
            .filter { pref -> pref.value == null }
            .forEach { pref -> commands[pref.key]?.getBindings(prefs)?.toPreferenceString()?.let { pref.value = it } }
    }

    override fun onTabSelected(tab: TabLayout.Tab) {
        val screen = ControlPreferenceScreen.entries[tab.position]
        Timber.v("Selected tab %d - %s", tab.position, screen.name)
        addPreferencesFromResource(screen.xmlRes)
        setControlPreferencesDefaultValues(screen)
        setDynamicTitle()
        setupNewStudyScreenSettings()
        setupAnswerCommands()
    }

    @NeedsTest("Only the tab elements are removed")
    override fun onTabUnselected(tab: TabLayout.Tab?) {
        val preferences = preferenceScreen.children.toList()
        val tabsPrefIndex = preferences.indexOfFirst { it is ControlsTabPreference }
        for (pref in preferences.subList(tabsPrefIndex + 1, preferences.size)) {
            preferenceScreen.removePreference(pref)
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab?) = Unit

    private fun setDynamicTitle() {
        findPreference<ControlPreference>(getString(R.string.reschedule_command_key))?.let {
            val preferenceTitle = TR.sentenceCase.setDueDate
            it.title = preferenceTitle
            it.dialogTitle = preferenceTitle
        }
        findPreference<ControlPreference>(getString(R.string.toggle_whiteboard_command_key))?.let {
            it.title = TR.sentenceCase.gestureToggleWhiteboard(getString(R.string.gesture_toggle_whiteboard))
        }
        findPreference<ControlPreference>(getString(R.string.flag_red_command_key))?.let {
            it.title = TR.sentenceCase.gestureFlagRed(getString(R.string.gesture_flag_red))
        }
        findPreference<ControlPreference>(getString(R.string.flag_orange_command_key))?.let {
            it.title = TR.sentenceCase.gestureFlagOrange(getString(R.string.gesture_flag_orange))
        }
        findPreference<ControlPreference>(getString(R.string.flag_green_command_key))?.let {
            it.title = TR.sentenceCase.gestureFlagGreen(getString(R.string.gesture_flag_green))
        }
        findPreference<ControlPreference>(getString(R.string.flag_blue_command_key))?.let {
            it.title = TR.sentenceCase.gestureFlagBlue(getString(R.string.gesture_flag_blue))
        }
        findPreference<ControlPreference>(getString(R.string.flag_pink_command_key))?.let {
            it.title = TR.sentenceCase.gestureFlagPink(getString(R.string.gesture_flag_pink))
        }
        findPreference<ControlPreference>(getString(R.string.flag_turquoise_command_key))?.let {
            it.title = TR.sentenceCase.gestureFlagTurquoise(getString(R.string.gesture_flag_turquoise))
        }
        findPreference<ControlPreference>(getString(R.string.flag_purple_command_key))?.let {
            it.title = TR.sentenceCase.gestureFlagPurple(getString(R.string.gesture_flag_purple))
        }
        findPreference<ControlPreference>(getString(R.string.remove_flag_command_key))?.let {
            it.title = TR.sentenceCase.gestureFlagRemove(getString(R.string.gesture_flag_remove))
        }
        findPreference<ControlPreference>(getString(R.string.bury_card_command_key))?.let {
            it.title = TR.sentenceCase.buryCard
        }
        findPreference<ControlPreference>(getString(R.string.suspend_card_command_key))?.let {
            it.title = TR.sentenceCase.suspendCard
        }
        findPreference<ControlPreference>(getString(R.string.card_info_command_key))?.let {
            it.title = TR.sentenceCase.cardInfo
        }
        findPreference<ControlPreference>(getString(R.string.bury_note_command_key))?.let {
            it.title = TR.sentenceCase.buryNote
        }
        findPreference<ControlPreference>(getString(R.string.suspend_note_command_key))?.let {
            it.title = TR.sentenceCase.suspendNote
        }
        findPreference<ControlPreference>(getString(R.string.mark_command_key))?.let {
            it.title = TR.sentenceCase.markNote
        }
        findPreference<ControlPreference>(getString(R.string.previewer_mark_key))?.let {
            it.title = TR.sentenceCase.markNote
        }
        findPreference<ControlPreference>(getString(R.string.previous_card_info_command_key))?.let {
            it.title = TR.sentenceCase.previousCardInfo
        }
        findPreference<ControlPreference>(getString(R.string.delete_command_key))?.let {
            it.title = TR.sentenceCase.deleteNote
        }
        findPreference<ControlPreference>(getString(R.string.answer_again_command_key))?.let {
            it.title = TR.sentenceCase.answerAgain
        }
        findPreference<ControlPreference>(getString(R.string.answer_hard_command_key))?.let {
            it.title = TR.sentenceCase.answerHard
        }
        findPreference<ControlPreference>(getString(R.string.answer_good_command_key))?.let {
            it.title = TR.sentenceCase.answerGood
        }
    }

    private fun setupNewStudyScreenSettings() {
        if (!Prefs.isNewStudyScreenEnabled) {
            findPreference<Preference>(R.string.gestures_corner_touch_preference)?.dependency = getString(R.string.gestures_preference)
            findPreference<Preference>(R.string.pref_swipe_sensitivity_key)?.dependency = getString(R.string.gestures_preference)
            findPreference<Preference>(R.string.pref_key_whiteboard_undo)?.isVisible = false
            findPreference<Preference>(R.string.pref_key_whiteboard_toggle_eraser)?.isVisible = false
            findPreference<Preference>(R.string.pref_key_whiteboard_redo)?.isVisible = false
            findPreference<Preference>(R.string.pref_key_whiteboard_clear)?.isVisible = false
            return
        }
        for (keyRes in legacyStudyScreenSettings) {
            val key = getString(keyRes)
            findPreference<Preference>(key)?.isVisible = false
        }
        findPreference<ControlPreference>(getString(R.string.browse_command_key))?.apply {
            title = TR.qtMiscBrowse()
            isVisible = true
            if (value == null) {
                value = ViewerAction.BROWSE.getBindings(sharedPrefs()).toPreferenceString()
            }
        }
        findPreference<ControlPreference>(getString(R.string.statistics_command_key))?.apply {
            title = TR.statisticsTitle()
            isVisible = true
            if (value == null) {
                value = ViewerAction.STATISTICS.getBindings(sharedPrefs()).toPreferenceString()
            }
        }
    }

    private fun setupAnswerCommands() {
        val showAnswerPref = (findPreference<ControlPreference>(R.string.show_answer_command_key) as? ReviewerControlPreference)

        val answerCommandKeys =
            listOf(
                ViewerAction.ANSWER_AGAIN.preferenceKey,
                ViewerAction.ANSWER_HARD.preferenceKey,
                ViewerAction.ANSWER_GOOD.preferenceKey,
                ViewerAction.ANSWER_EASY.preferenceKey,
            )
        for (key in answerCommandKeys) {
            (findPreference<Preference>(key) as? ReviewerControlPreference)?.let { answerPref ->
                val items =
                    arrayOf(
                        getString(R.string.only_answer),
                        getString(R.string.flip_and_answer),
                    )
                answerPref.setOnBindingSelectedListener { binding ->
                    AlertDialog.Builder(requireContext()).show {
                        setTitle(answerPref.title)
                        setIcon(answerPref.icon)
                        setItems(items) { _, index ->
                            when (index) {
                                0 -> answerPref.addBinding(binding, CardSide.ANSWER)
                                1 -> {
                                    answerPref.addBinding(binding, CardSide.ANSWER)
                                    showAnswerPref?.addBinding(binding, CardSide.QUESTION)
                                }
                            }
                        }
                    }
                    true
                }
            }
        }
    }

    companion object {
        private val actionToScreenMap: Map<String, ControlPreferenceScreen> by lazy {
            ControlPreferenceScreen.entries
                .flatMap { screen ->
                    screen.getActions().map { action -> action.preferenceKey to screen }
                }.toMap()
        }

        val legacyStudyScreenSettings =
            listOf(
                R.string.save_voice_command_key,
                R.string.toggle_eraser_command_key,
                R.string.clear_whiteboard_command_key,
                R.string.change_whiteboard_pen_color_command_key,
                R.string.gestures_preference,
            )
    }
}

enum class ControlPreferenceScreen(
    @XmlRes val xmlRes: Int,
) {
    REVIEWER(R.xml.preferences_reviewer_controls),
    PREVIEWER(R.xml.preferences_previewer_controls),
    ;

    fun getActions(): List<MappableAction<*>> =
        when (this) {
            REVIEWER -> if (Prefs.isNewStudyScreenEnabled) ViewerAction.entries else ViewerCommand.entries
            PREVIEWER -> PreviewerAction.entries
        }
}
