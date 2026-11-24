// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.android.view.locationInWindow
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.dispatchInsets
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Edge-to-edge inset handling for the [DeckPicker] FAB and deck list.
 *
 * See issue 21241: the FAB shifted when returning from Settings.
 */
@RunWith(AndroidJUnit4::class)
class DeckPickerInsetsTest : RobolectricTest() {
    @Test
    fun `destroying the deck picker removes its global layout listener`() {
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        addDeck("Test Deck")
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        val deckPicker = controller.get()
        advanceRobolectricLooperUntil(lazyMessage = { "the deck list should be shown" }) {
            deckPicker.deckPickerBinding.deckPickerContent.isVisible
        }
        deckPicker.dispatchInsets(navBarBottom = 48.dp)
        deckPicker.layoutForTest()
        val list = deckPicker.deckPickerBinding.decks
        val observer = deckPicker.deckPickerBinding.root.viewTreeObserver
        check(list.height > 0) { "the deck list must be laid out, or the layout listener is a no-op" }
        val padding = list.paddingBottom
        check(padding > 0)

        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, 0)
        observer.dispatchOnGlobalLayout()
        assertThat("the listener updates padding while the activity is alive", list.paddingBottom, equalTo(padding))

        controller.pause().stop().destroy()
        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, 0)
        observer.dispatchOnGlobalLayout()
        assertThat("the window observer must no longer call the destroyed activity", list.paddingBottom, equalTo(0))
    }

    @Test
    fun `deck list padding follows a dialog keyboard opening and closing`() =
        withDeckPicker(deckCount = 2) { deckPicker ->
            deckPicker.dispatchInsets(navBarBottom = 48.dp)
            deckPicker.layoutForTest()
            val list = deckPicker.deckPickerBinding.decks
            val restingPadding = list.paddingBottom

            repeat(2) {
                // A dialog opens the keyboard while the deck picker's FAB remains visible.
                deckPicker.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)
                deckPicker.layoutForTest()
                assertThat(
                    "the deck list must gain clearance while the keyboard is open",
                    list.paddingBottom,
                    greaterThan(restingPadding),
                )

                deckPicker.dispatchInsets(navBarBottom = 48.dp)
                deckPicker.layoutForTest()
                assertThat("the keyboard must not leave extra space below the decks", list.paddingBottom, equalTo(restingPadding))
            }
        }

    @Test
    fun `FAB is above the navigation bar`() =
        withDeckPicker(deckCount = 2) { deckPicker ->
            val navBarBottom = 48.dp.toPx(targetContext)
            deckPicker.dispatchInsets(navBarBottom = 48.dp)
            deckPicker.layoutForTest()

            // the studied-today line refreshes after the insets have been applied (as on resume)
            deckPicker.viewModel.flowOfStudiedTodayStats.value = "Studied 3 cards in 3 minutes today"
            deckPicker.layoutForTest()

            val summary = deckPicker.deckPickerBinding.reviewSummaryTextView
            val summaryTextHeight = summary.height - summary.paddingBottom
            val fabColumnPadding = 12.dp.toPx(targetContext)
            val fabMargin = 16.dp.toPx(targetContext)
            assertThat(
                "the FAB is unchanged compared to its pre-edge-to-edge position",
                deckPicker.fabDistanceToWindowBottom,
                equalTo(navBarBottom + fabColumnPadding + fabMargin + summaryTextHeight / 2),
            )
        }

    @Test
    fun `the FAB follows the summary line's layout passes`() =
        withDeckPicker(deckCount = 2) { deckPicker ->
            // note: the summary layout can be updated without the text event firing
            // (e.g. screen width change)
            val navBarBottom = 48.dp.toPx(targetContext)
            deckPicker.dispatchInsets(navBarBottom = 48.dp)
            deckPicker.viewModel.flowOfStudiedTodayStats.value = "Studied 3 cards in 3 minutes today"
            deckPicker.layoutForTest()

            val summary = deckPicker.deckPickerBinding.reviewSummaryTextView
            summary.text = "Studied 3 cards in 3 minutes today\n(0.05s/card)"
            deckPicker.layoutForTest()

            val summaryTextHeight = summary.height - summary.paddingBottom
            val fabColumnPadding = 12.dp.toPx(targetContext)
            val fabMargin = 16.dp.toPx(targetContext)
            assertThat(
                "the FAB rests half the re-wrapped summary line above its padded position",
                deckPicker.fabDistanceToWindowBottom,
                equalTo(navBarBottom + fabColumnPadding + fabMargin + summaryTextHeight / 2),
            )
        }

    @Test
    fun `a same-height studied-today refresh does not move the FAB`() =
        withDeckPicker(deckCount = 2) { deckPicker ->
            deckPicker.viewModel.flowOfStudiedTodayStats.value = "Studied 10 cards in 5 minutes today"
            deckPicker.layoutForTest()
            deckPicker.dispatchInsets(navBarBottom = 48.dp)
            deckPicker.layoutForTest()
            val restingPosition = deckPicker.fabDistanceToWindowBottom

            // same text length, so the line's height cannot change
            deckPicker.viewModel.flowOfStudiedTodayStats.value = "Studied 11 cards in 5 minutes today"
            deckPicker.layoutForTest()

            assertThat(
                "a same-height studied-today refresh must not move the FAB",
                deckPicker.fabDistanceToWindowBottom,
                equalTo(restingPosition),
            )
        }

    @Test
    fun `the FAB does not move when the deck list first appears`() {
        // do not advance the looper: the FAB must be seen before the collection is loaded
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        addDeck("Test Deck")

        // Flows start collection on STARTED, so only call .create()
        val controller = Robolectric.buildActivity(DeckPicker::class.java, Intent()).create()
        saveControllerForCleanup(controller)
        val deckPicker = controller.get()

        check(!deckPicker.deckPickerBinding.deckPickerContent.isVisible) {
            "the deck list/summary line should be hidden until the collection loads"
        }
        val marginBeforeLoad = deckPicker.fabBottomMargin

        controller.start().resume().visible()
        // advances the looper so the collection loads and the list appears
        advanceRobolectricLooperUntil(lazyMessage = { "the deck list should be shown" }) {
            deckPicker.deckPickerBinding.deckPickerContent.isVisible
        }
        deckPicker.layoutForTest()
        // one line at the test's layout width, so the resting position is the seeded one
        deckPicker.viewModel.flowOfStudiedTodayStats.value = "Studied 3 cards today"
        deckPicker.layoutForTest()

        assertThat(
            "the FAB margin should be unchanged",
            marginBeforeLoad,
            equalTo(deckPicker.fabBottomMargin),
        )
        assertThat("the FAB should be above the summary line", deckPicker.fabBottomMargin, greaterThan(0))
    }

    @Test
    fun `the FAB is raised again after leaving the initial state`() =
        withDeckPicker(deckCount = 2) { deckPicker ->
            deckPicker.layoutForTest()
            val raisedMargin = deckPicker.fabBottomMargin
            check(raisedMargin > 0) { "the FAB should start raised above the summary line" }

            // delete every deck: the deck picker enters the initial state
            col.decks.remove(listOf("Test Deck 0", "Test Deck 1").map { col.decks.id(it) })
            deckPicker.updateDeckList()
            // Refreshing the decks runs on IO: draining the main looper alone may finish first.
            advanceRobolectricLooperUntil(lazyMessage = { "the empty collection placeholder should be shown" }) {
                deckPicker.deckPickerBinding.noDecksPlaceholder.isVisible
            }
            deckPicker.layoutForTest()
            check(deckPicker.fabBottomMargin == 0) { "the FAB should be unraised initially" }

            // create a deck: the summary line reappears with unchanged studied-today text,
            // so no layout pass fires on it
            addDeck("A Deck")
            deckPicker.updateDeckList()
            advanceRobolectricLooperUntil(lazyMessage = { "the deck list should be shown again" }) {
                deckPicker.deckPickerBinding.deckPickerContent.isVisible
            }
            deckPicker.layoutForTest()

            assertThat(
                "the FAB should be raised above the summary line again",
                deckPicker.fabBottomMargin,
                equalTo(raisedMargin),
            )
        }

    /** The bottom margin raising the FAB above the 'Studied X cards' line. */
    private val DeckPicker.fabBottomMargin: Int
        get() = (floatingActionButtonBinding.fabLinearLayout.layoutParams as MarginLayoutParams).bottomMargin

    /** The gap, in pixels, between the bottom of the FAB and the bottom of its container. */
    private val DeckPicker.fabDistanceToWindowBottom: Int
        get() {
            val fab = floatingActionButtonBinding.fabMain
            val container = floatingActionButtonBinding.root
            val fabBottom = fab.locationInWindow().y + fab.height
            val containerBottom = container.locationInWindow().y + container.height
            return containerBottom - fabBottom
        }

    /**
     * Forces measure/layout passes so view bounds can be asserted.
     *
     * Laid out at the window's current size so line wrapping is stable across passes.
     */
    private fun DeckPicker.layoutForTest() {
        advanceRobolectricLooper()
        performLayout()
    }

    /** [layoutForTest] without processing pending tasks: lays out the current state */
    private fun DeckPicker.performLayout() {
        val content = findViewById<View>(android.R.id.content)
        val width = if (content.width > 0) content.width else 1080
        val height = if (content.height > 0) content.height else 2400
        // Two passes - the summary line's layout listener:
        // 1. sets a margin
        // 2. applies the margin
        repeat(2) {
            content.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
            content.layout(0, 0, width, height)
        }
    }
}
