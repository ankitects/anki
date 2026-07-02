/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.compat.requireSerializableCompat
import com.ichi2.anki.contextmenu.DeckPickerMenuContentProvider
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.requireLong
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.utils.title

class DeckPickerContextMenu : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        require(requireArguments().containsKey(ARG_DECK_ID)) { "Missing argument deck id" }
        require(requireArguments().containsKey(ARG_DECK_NAME)) { "Missing argument deck name" }
        require(requireArguments().containsKey(ARG_DECK_IS_DYN)) { "Missing argument deck is dynamic" }
        require(requireArguments().containsKey(ARG_DECK_HAS_BURIED_IN_DECK)) { "Missing argument deck has buried" }
        val options = createOptionsList()
        return AlertDialog
            .Builder(requireActivity())
            .title(text = requireArguments().getString(ARG_DECK_NAME))
            .setItems(
                options.map { it.label(requireContext()) }.toTypedArray(),
            ) { _, index: Int ->
                parentFragmentManager.setDeckPickerContextMenuResult(
                    DeckPickerContextMenuResult(
                        deckId = requireArguments().getLong(ARG_DECK_ID),
                        option = options[index],
                    ),
                )
            }.create()
    }

    private fun createOptionsList(): List<DeckPickerContextMenuOption> =
        DeckPickerMenuContentProvider.createOptionsList(
            requireArguments().getBoolean(ARG_DECK_IS_DYN),
            requireArguments().getBoolean(ARG_DECK_HAS_BURIED_IN_DECK),
        )

    enum class DeckPickerContextMenuOption {
        RENAME_DECK,
        DECK_OPTIONS,
        CONCEPT_MAP,
        CUSTOM_STUDY,
        DELETE_DECK,
        EXPORT_DECK,
        UNBURY,
        CUSTOM_STUDY_REBUILD,
        CUSTOM_STUDY_EMPTY,
        CREATE_SUBDECK,
        CREATE_SHORTCUT,
        BROWSE_CARDS,
        EDIT_DESCRIPTION,
        ADD_CARD,
        SCHEDULE_REMINDERS,
        ;

        fun label(context: Context): String =
            with(context) {
                when (this@DeckPickerContextMenuOption) {
                    RENAME_DECK -> TR.sentenceCase.renameDeck
                    DECK_OPTIONS -> TR.sentenceCase.deckOptions
                    CONCEPT_MAP -> TR.statisticsConceptMap()
                    CUSTOM_STUDY -> TR.sentenceCase.customStudy
                    DELETE_DECK -> TR.sentenceCase.deleteDeck
                    EXPORT_DECK -> getString(R.string.export_deck)
                    UNBURY -> TR.studyingUnbury()
                    CUSTOM_STUDY_REBUILD -> TR.actionsRebuild()
                    CUSTOM_STUDY_EMPTY -> getString(R.string.empty_cram_label)
                    CREATE_SUBDECK -> getString(R.string.create_subdeck)
                    CREATE_SHORTCUT -> getString(R.string.create_shortcut)
                    BROWSE_CARDS -> getString(R.string.browse_cards)
                    EDIT_DESCRIPTION -> getString(R.string.edit_deck_description)
                    ADD_CARD -> TR.actionsAdd()
                    SCHEDULE_REMINDERS -> getString(R.string.schedule_reminders_do_not_translate)
                }
            }
    }

    companion object {
        /**
         * Builds a [DeckPickerContextMenu] for [deckId], reading the deck's name and
         * the dynamic / has-buried flags from the collection.
         */
        suspend fun newInstance(deckId: DeckId): DeckPickerContextMenu =
            withCol {
                DeckPickerContextMenu().apply {
                    arguments =
                        Bundle().apply {
                            putLong(ARG_DECK_ID, deckId)
                            putString(ARG_DECK_NAME, decks.name(deckId))
                            putBoolean(ARG_DECK_IS_DYN, decks.isFiltered(deckId))
                            putBoolean(ARG_DECK_HAS_BURIED_IN_DECK, sched.haveBuried())
                        }
                }
            }

        @VisibleForTesting
        const val ARG_DECK_ID = "arg_deck_id"

        @VisibleForTesting
        const val ARG_DECK_NAME = "arg_deck_name"

        @VisibleForTesting
        const val ARG_DECK_IS_DYN = "arg_deck_is_dyn"

        @VisibleForTesting
        const val ARG_DECK_HAS_BURIED_IN_DECK = "arg_deck_has_buried_in_deck"
    }
}

/**
 * Result delivered by the deck-picker context menus
 *
 * @see DeckPickerContextMenuOption
 * @see DeckPickerContextMenu
 * @see DeckPickerMenuContentProvider
 */
data class DeckPickerContextMenuResult(
    val deckId: DeckId,
    val option: DeckPickerContextMenuOption,
) {
    fun toBundle(): Bundle =
        Bundle().apply {
            putLong(ARG_DECK_ID, deckId)
            putSerializable(ARG_OPTION, option)
        }

    companion object {
        const val REQUEST_KEY = "request_key_deck_picker_context_menu"
        private const val ARG_DECK_ID = "deck_id"
        private const val ARG_OPTION = "option"

        fun fromBundle(bundle: Bundle) =
            DeckPickerContextMenuResult(
                deckId = bundle.requireLong(ARG_DECK_ID),
                option = bundle.requireSerializableCompat<DeckPickerContextMenuOption>(ARG_OPTION),
            )
    }
}

fun FragmentManager.setDeckPickerContextMenuResult(result: DeckPickerContextMenuResult) {
    setFragmentResult(DeckPickerContextMenuResult.REQUEST_KEY, result.toBundle())
}

fun FragmentActivity.setDeckPickerContextMenuResultListener(listener: (DeckPickerContextMenuResult) -> Unit) {
    setFragmentResultListener(DeckPickerContextMenuResult.REQUEST_KEY) { _, bundle ->
        listener(DeckPickerContextMenuResult.fromBundle(bundle))
    }
}
