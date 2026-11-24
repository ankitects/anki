// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Alok Srivastava <alok020505@gmail.com>

package com.ichi2.preferences

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.getJavaMethodAsAccessible
import com.ichi2.testutils.targetContext
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test of [ReviewerControlPreference.getPreferenceAssignedTo]
 *
 * `getRelatedPreferences` matches preferences by exact class (`it::class == ReviewerControlPreference::class`),
 * so instances here must be real [ReviewerControlPreference]s rather than a test subclass - `side` and
 * `getPreferenceAssignedTo` are accessed via reflection as they're `protected`.
 */
@RunWith(AndroidJUnit4::class)
class ReviewerControlPreferenceTest : AndroidTest {
    private val bindingsToPersist = mutableMapOf<ReviewerControlPreference, Binding>()

    @Test
    fun `binding on the same side is found on a different preference`() {
        val gesture = Binding.GestureInput(Gesture.SWIPE_UP)
        val ownerPref = buildPreference(key = "owner", side = CardSide.QUESTION, binding = gesture)
        val queryingPref = buildPreference(key = "querying", side = CardSide.QUESTION)
        buildScreen(ownerPref, queryingPref)

        // the conflict should be found on the *other* preference, not a self-conflict
        assertEquals(ownerPref, queryingPref.callGetPreferenceAssignedTo(gesture))
    }

    @Test
    fun `binding on a different side is not found`() {
        val gesture = Binding.GestureInput(Gesture.SWIPE_UP)
        val questionPref = buildPreference(key = "question", side = CardSide.QUESTION, binding = gesture)
        val answerPref = buildPreference(key = "answer", side = CardSide.ANSWER)
        buildScreen(questionPref, answerPref)

        assertNull(answerPref.callGetPreferenceAssignedTo(gesture))
    }

    @Test
    fun `binding on 'both' sides conflicts with a single-side binding`() {
        val gesture = Binding.GestureInput(Gesture.SWIPE_UP)
        val questionPref = buildPreference(key = "question", side = CardSide.QUESTION, binding = gesture)
        val bothPref = buildPreference(key = "both", side = CardSide.BOTH)
        buildScreen(questionPref, bothPref)

        assertEquals(questionPref, bothPref.callGetPreferenceAssignedTo(gesture))
    }

    private fun buildPreference(
        key: String,
        side: CardSide,
        binding: Binding? = null,
    ): ReviewerControlPreference {
        val pref = ReviewerControlPreference(targetContext)
        pref.key = key
        pref.setSideForTest(side)
        if (binding != null) bindingsToPersist[pref] = binding
        return pref
    }

    private fun buildScreen(vararg preferences: ReviewerControlPreference) {
        val preferenceManager = PreferenceManager(targetContext)
        val screen = preferenceManager.createPreferenceScreen(targetContext)
        preferenceManager.setPreferences(screen)
        preferences.forEach { screen.addPreference(it) }
        preferences.forEach { pref ->
            bindingsToPersist[pref]?.let { binding ->
                val side = requireNotNull(pref.getSideForTest())
                pref.value = listOf(ReviewerBinding(binding, side)).toPreferenceString()
            }
        }
    }

    private fun ReviewerControlPreference.sideField() =
        ReviewerControlPreference::class.java.getDeclaredField("side").apply { isAccessible = true }

    private fun ReviewerControlPreference.setSideForTest(side: CardSide?) = sideField().set(this, side)

    private fun ReviewerControlPreference.getSideForTest() = sideField().get(this) as CardSide?

    private fun ReviewerControlPreference.callGetPreferenceAssignedTo(binding: Binding): ControlPreference? =
        getJavaMethodAsAccessible(
            ReviewerControlPreference::class.java,
            "getPreferenceAssignedTo",
            Binding::class.java,
        ).invoke(this, binding) as ControlPreference?
}
