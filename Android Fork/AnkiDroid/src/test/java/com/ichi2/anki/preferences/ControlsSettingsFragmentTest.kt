// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.preferences.ReviewerControlPreference
import com.ichi2.testutils.HamcrestUtils
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ControlsSettingsFragmentTest : RobolectricTest() {
    @Test
    fun `XML keys match the Enum keys`() {
        for (screen in ControlPreferenceScreen.entries) {
            val xmlKeys =
                PreferenceTestUtils.getKeysFromXml(targetContext, screen.xmlRes, excludeCategories = true).toMutableList().apply {
                    remove("binding_BROWSE")
                    remove("binding_STATISTICS")
                    remove("binding_whiteboard_UNDO")
                    remove("binding_whiteboard_REDO")
                    remove("binding_whiteboard_CLEAR")
                    remove("binding_whiteboard_TOGGLE_ERASER")
                }
            val enumKeys = screen.getActions().map { it.preferenceKey }

            assertThat(xmlKeys, HamcrestUtils.containsInAnyOrder(enumKeys))
        }
    }

    /**
     * Tests for alert dialog behavior in [ControlsSettingsFragment] using 'swipe up' gesture as the input binding.
     */
    @Test
    fun `skip button binding only to answer`() {
        withControlsSettings {
            val binding = Binding.GestureInput(Gesture.SWIPE_UP)
            answerPref.setBinding(binding)
            clickAlertDialogButton { negativeButton }

            val showAnswerBindings = showAnswerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            val answerBindings = answerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            assertThat("binding is assigned to Show answer", showAnswerBindings, not(hasItem(equalTo(Pair(binding, CardSide.QUESTION)))))
            assertThat("binding not assigned to answer", answerBindings, hasItem(equalTo(Pair(binding, CardSide.ANSWER))))
        }
    }

    @Test
    fun `assign buttons binding to both answer and show answer`() {
        withControlsSettings {
            val binding = Binding.GestureInput(Gesture.SWIPE_UP)
            answerPref.setBinding(binding)
            clickAlertDialogButton { positiveButton }

            val showAnswerBindings = showAnswerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            val answerBindings = answerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            assertThat("binding not assigned to Show answer", showAnswerBindings, hasItem(equalTo(Pair(binding, CardSide.QUESTION))))
            assertThat("binding not assigned to answer", answerBindings, hasItem(equalTo(Pair(binding, CardSide.ANSWER))))
        }
    }

    @Test
    fun `no dialog if binding is already assigned to show answer`() {
        withControlsSettings {
            val binding = Binding.GestureInput(Gesture.SWIPE_UP)
            showAnswerPref.addBinding(binding, CardSide.QUESTION)

            answerPref.setBinding(binding)

            assertNull(ShadowDialog.getLatestDialog(), "no dialog should be shown")
            val showAnswerBindings = showAnswerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            val answerBindings = answerPref.getMappableBindings().map { Pair(it.binding, it.side) }
            assertThat("binding not kept on Show answer", showAnswerBindings, hasItem(Pair(binding, CardSide.QUESTION)))
            assertThat("binding not assigned to answer", answerBindings, hasItem(Pair(binding, CardSide.ANSWER)))
        }
    }
}

private val ControlsSettingsFragment.showAnswerPref
    get() = requirePreference<ReviewerControlPreference>(getString(R.string.show_answer_command_key))

private val ControlsSettingsFragment.answerPref
    get() = requirePreference<ReviewerControlPreference>(getString(R.string.answer_good_command_key))

/**
 * launches the [ControlsSettingsFragment] and runs [block] on it.
 */
context(_: DeferredNavigation)
private fun withControlsSettings(block: ControlsSettingsFragment.() -> Unit) {
    val intent =
        PreferencesActivity.getIntent(
            ApplicationProvider.getApplicationContext(),
            ControlsSettingsFragment::class,
        )
    ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
        scenario.onActivity { activity ->
            val preferencesFragment = activity.fragment as PreferencesFragment
            val controlsSettingsFragment =
                preferencesFragment.childFragmentManager.findFragmentByTag(
                    ControlsSettingsFragment::class.java.name,
                ) as ControlsSettingsFragment
            controlsSettingsFragment.block()
        }
    }
}
