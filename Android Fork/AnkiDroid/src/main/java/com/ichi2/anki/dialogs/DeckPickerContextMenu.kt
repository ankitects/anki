// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

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
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.compat.requireSerializableCompat
import com.ichi2.anki.contextmenu.DeckPickerMenuContentProvider
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.runCatching
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
        TIME_BUDGETS,
        ;

        fun label(context: Context): String =
            with(context) {
                when (this@DeckPickerContextMenuOption) {
                    RENAME_DECK -> TR.sentenceCase.renameDeck
                    DECK_OPTIONS -> TR.sentenceCase.deckOptions
                    CUSTOM_STUDY -> TR.sentenceCase.customStudy
                    DELETE_DECK -> TR.sentenceCase.deleteDeck
                    EXPORT_DECK -> getString(CommonString.export_deck)
                    UNBURY -> TR.studyingUnbury()
                    CUSTOM_STUDY_REBUILD -> TR.actionsRebuild()
                    CUSTOM_STUDY_EMPTY -> getString(CommonString.empty_cram_label)
                    CREATE_SUBDECK -> getString(CommonString.create_subdeck)
                    CREATE_SHORTCUT -> getString(CommonString.create_shortcut)
                    BROWSE_CARDS -> getString(CommonString.browse_cards)
                    EDIT_DESCRIPTION -> getString(CommonString.edit_deck_description)
                    ADD_CARD -> TR.actionsAdd()
                    SCHEDULE_REMINDERS -> getString(CommonString.schedule_reminders_title)
                    TIME_BUDGETS -> getString(R.string.time_budget_title)
                }
            }
    }

    companion object {
        /**
         * Builds a [DeckPickerContextMenu] for [deckId], reading the deck's name and
         * the dynamic / has-buried flags from the collection.
         * If a read fails, reports the error through [activity] and returns `null`.
         */
        context(activity: FragmentActivity)
        suspend fun createOrShowError(deckId: DeckId): DeckPickerContextMenu? =
            activity.runCatching {
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
            putLong(RESULT_DECK_ID, deckId)
            putSerializable(RESULT_CONTEXT_MENU_OPTION, option)
        }

    companion object {
        const val REQUEST_KEY = "request_key_deck_picker_context_menu"
        private const val RESULT_DECK_ID = "deck_id"
        private const val RESULT_CONTEXT_MENU_OPTION = "option"

        fun fromBundle(bundle: Bundle) =
            DeckPickerContextMenuResult(
                deckId = bundle.requireLong(RESULT_DECK_ID),
                option = bundle.requireSerializableCompat<DeckPickerContextMenuOption>(RESULT_CONTEXT_MENU_OPTION),
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
