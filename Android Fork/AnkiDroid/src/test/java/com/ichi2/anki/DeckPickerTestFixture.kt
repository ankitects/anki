// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ConcatAdapter
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooperUntil
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.StartupResponse
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.BackupManagerTestUtilities

// TODO: move to testFixtures once RobolectricTest is moved

context(test: RobolectricTest)
fun withDeckPicker(
    deckCount: Int,
    withCards: Boolean = false,
    block: (DeckPicker) -> Unit,
) {
    // startup code occurs here so all users of this method are correctly setup
    test.ensureCollectionLoadIsSynchronous()
    test.setIntroductionSlidesShown(true)
    BackupManagerTestUtilities.setupSpaceForBackup(test.targetContext)
    // suppress the periodic 'backup your collection' prompt so the screenshot is just the deck list
    test.targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    if (withCards) test.ensureNonEmptyCollection()
    for (i in 0 until deckCount) {
        // 'Deck' is before 'Default' alphabetically
        test.addDeck("Test Deck $i")
    }
    val deckPicker =
        test.startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
            // Deck refreshes run on IO, so draining the main looper alone may finish first.
            advanceRobolectricLooperUntil(lazyMessage = { "the deck list or empty collection placeholder should be shown" }) {
                it.deckPickerBinding.deckPickerContent.isVisible || it.deckPickerBinding.noDecksPlaceholder.isVisible
            }
        }
    block(deckPicker)
}

fun DeckPickerViewModel.awaitStartup() =
    advanceRobolectricLooperUntil {
        startupJob?.isActive != true && flowOfStartupResponse.value !is StartupResponse.Success
    }

fun DeckPicker.awaitStartup() {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        viewModel.awaitStartup()
    }
}

/** The deck list's [DeckAdapter], which may be wrapped in a [ConcatAdapter] with other sections. */
val DeckPicker.deckAdapter: DeckAdapter
    get() =
        when (val adapter = deckPickerBinding.decks.adapter) {
            is ConcatAdapter -> adapter.adapters.filterIsInstance<DeckAdapter>().single()
            else -> adapter as DeckAdapter
        }

/** Waits for the adapter to display the expected decks, including background list diffs. */
fun DeckPicker.awaitVisibleDecks(expectedDeckIds: List<DeckId>) {
    val adapter = deckAdapter
    advanceRobolectricLooperUntil(
        lazyMessage = { "Expected visible decks $expectedDeckIds, but found ${adapter.currentList.map { it.did }}" },
    ) {
        adapter.currentList.map { it.did } == expectedDeckIds
    }
}

/** Selects a context-menu action and waits for its observable result before returning. */
fun DeckPicker.selectContextMenuOptionAndAwait(
    option: DeckPickerContextMenuOption,
    deckId: DeckId,
    condition: () -> Boolean,
) {
    supportFragmentManager.setDeckPickerContextMenuResult(DeckPickerContextMenuResult(deckId = deckId, option = option))
    advanceRobolectricLooperUntil(
        lazyMessage = { "Context-menu action $option on deck $deckId did not reach its expected state" },
        condition = condition,
    )
}
