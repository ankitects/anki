// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>

package com.ichi2.anki.contextmenu

import android.view.Menu
import android.view.MenuItem
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.dialogs.DeckPickerContextMenu
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.runCatching
import com.ichi2.anki.settings.Prefs

/**
 * MenuContentProvider implementation for DeckPicker for providing deck specific context menu items.
 */
class DeckPickerMenuContentProvider(
    private val id: DeckId,
    private val isDynamic: Boolean,
    private val hasBuriedInDeck: Boolean,
    private val deckPicker: DeckPicker,
) : MenuContentProvider {
    override fun populateMenu(menu: Menu) {
        val options = createOptionsList()
        options.forEachIndexed { index, option ->
            menu.add(0, index, index, option.label(deckPicker))
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val options = createOptionsList()
        val selectedOption = options.getOrNull(item.itemId) ?: return false

        deckPicker.supportFragmentManager.setDeckPickerContextMenuResult(
            DeckPickerContextMenuResult(deckId = id, option = selectedOption),
        )
        return true
    }

    override fun onPrepareMenu(menu: Menu) {
    }

    /**
     * Creates the list of context menu options based on deck properties.
     * This is based on [DeckPickerContextMenu.createOptionsList]
     */
    private fun createOptionsList(): List<DeckPickerContextMenu.DeckPickerContextMenuOption> = createOptionsList(isDynamic, hasBuriedInDeck)

    companion object {
        /**
         * Builds a [DeckPickerMenuContentProvider] for [deckId] (reading the dynamic /
         * has-buried flags from the collection) and shows it as a popup anchored at
         * ([x], [y]) on the deck picker's recycler view.
         * If a read fails, reports the error through [deckPicker].
         */
        context(deckPicker: DeckPicker)
        suspend fun show(
            deckId: DeckId,
            x: Float,
            y: Float,
        ) {
            val provider =
                deckPicker.runCatching {
                    withCol {
                        DeckPickerMenuContentProvider(
                            id = deckId,
                            isDynamic = decks.isFiltered(deckId),
                            hasBuriedInDeck = sched.haveBuried(),
                            deckPicker = deckPicker,
                        )
                    }
                } ?: return
            val anchorParent = deckPicker.deckPickerBinding.deckPickerContent
            val target = deckPicker.deckPickerBinding.decks
            MouseContextMenuHandler(viewGroup = anchorParent, menuContentProvider = provider)
                .showContextMenu(view = target, x = x, y = y)
        }

        fun createOptionsList(
            isDynamic: Boolean,
            hasBuriedInDeck: Boolean,
        ): List<DeckPickerContextMenu.DeckPickerContextMenuOption> =
            mutableListOf<DeckPickerContextMenu.DeckPickerContextMenuOption>().apply {
                add(DeckPickerContextMenu.DeckPickerContextMenuOption.ADD_CARD)
                add(DeckPickerContextMenu.DeckPickerContextMenuOption.BROWSE_CARDS)
                if (isDynamic) {
                    add(DeckPickerContextMenu.DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD)
                    add(DeckPickerContextMenu.DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY)
                }
                add(DeckPickerContextMenu.DeckPickerContextMenuOption.RENAME_DECK)
                if (!isDynamic) {
                    add(DeckPickerContextMenu.DeckPickerContextMenuOption.CREATE_SUBDECK)
                }
                add(DeckPickerContextMenu.DeckPickerContextMenuOption.DECK_OPTIONS)
                if (!isDynamic) {
                    add(DeckPickerContextMenu.DeckPickerContextMenuOption.CUSTOM_STUDY)
                }
                add(DeckPickerContextMenu.DeckPickerContextMenuOption.EXPORT_DECK)
                if (hasBuriedInDeck) {
                    add(DeckPickerContextMenu.DeckPickerContextMenuOption.UNBURY)
                }
                add(DeckPickerContextMenu.DeckPickerContextMenuOption.CREATE_SHORTCUT)
                if (!isDynamic) {
                    add(DeckPickerContextMenu.DeckPickerContextMenuOption.EDIT_DESCRIPTION)
                }
                if (Prefs.newReviewRemindersEnabled) {
                    add(DeckPickerContextMenu.DeckPickerContextMenuOption.SCHEDULE_REMINDERS)
                }
                add(DeckPickerContextMenu.DeckPickerContextMenuOption.DELETE_DECK)
            }
    }
}
