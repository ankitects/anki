// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.filtered

import androidx.core.view.isVisible
import anki.decks.copy
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.FragmentFilteredDeckOptionsBinding
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.testutils.simulateSystemBars
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for [FilteredDeckOptionsFragment].
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.filtered.FilteredDeckOptionsScreenshotTest"`
 */
class FilteredDeckOptionsScreenshotTest : ScreenshotTest() {
    @Test
    fun `new filtered deck`() =
        withOptions { _, _ ->
            captureScreen("newDeck")
        }

    @Test
    fun `existing filtered deck with system bars`() =
        withOptions(deckId = col.decks.newFiltered("Filtered deck")) { activity, _ ->
            activity.simulateSystemBars()
            captureScreen("existingDeck_systemBars")
        }

    @Test
    fun `landscape display cutout`() {
        RuntimeEnvironment.setQualifiers("+land")
        withOptions { activity, binding ->
            activity.simulateSystemBars(cutoutLeft = 32.dp)
            captureScreen("landscapeCutout")
            captureScrolledToEnd("landscapeCutout_scrolledToEnd", binding)
        }
    }

    @Test
    fun `second filter and preview delays`() =
        withOptions { activity, binding ->
            binding.switchSecondFilter.isChecked = true
            binding.checkBoxReschedule.isChecked = false
            advanceRobolectricLooperUntil {
                binding.secondFilterSearchContainer.isVisible && binding.rescheduleDelayAgainLayout.isVisible
            }
            binding.secondFilterSearchInput.setText("is:new")
            binding.switchSecondFilter.jumpDrawablesToCurrentState()
            binding.checkBoxReschedule.jumpDrawablesToCurrentState()
            activity.simulateSystemBars()
            captureScreen("secondFilter_systemBars")
            captureScrolledToEnd("previewDelays_systemBars_scrolledToEnd", binding)
        }

    private fun captureScrolledToEnd(
        name: String,
        binding: FragmentFilteredDeckOptionsBinding,
    ) {
        binding.scrollView.scrollTo(0, binding.scrollView.getChildAt(0).bottom)
        advanceRobolectricLooper()
        captureScreen(name)
    }

    private fun withOptions(
        deckId: DeckId = 0,
        block: (ConfigAwareSingleFragmentActivity, FragmentFilteredDeckOptionsBinding) -> Unit,
    ) {
        val scheduler = col.sched
        // The backend includes the current time in new deck names, outside Robolectric's clock.
        val options = scheduler.getOrCreateFilteredDeck(deckId).copy { name = "Filtered deck" }
        mockkObject(scheduler)
        try {
            every { scheduler.getOrCreateFilteredDeck(deckId) } returns options
            val activity =
                startActivityNormallyOpenCollectionWithIntent(
                    ConfigAwareSingleFragmentActivity::class.java,
                    FilteredDeckOptionsFragment.getIntent(targetContext, did = deckId),
                )
            val binding = FragmentFilteredDeckOptionsBinding.bind(activity.fragment!!.requireView())
            // Loading the options uses the collection dispatcher; draining the main looper alone can miss it.
            advanceRobolectricLooperUntil { binding.scrollView.isVisible }
            block(activity, binding)
        } finally {
            unmockkObject(scheduler)
        }
    }
}
