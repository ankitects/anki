/*
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
package com.ichi2.anki.export

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import anki.cards.cardIds
import anki.generic.Empty
import anki.import_export.ExportLimit
import anki.import_export.exportLimit
import anki.notes.noteIds
import com.ichi2.anki.ALL_DECKS_ID
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.browser.removeSafely
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.databinding.DialogExportOptionsBinding
import com.ichi2.anki.exportApkgPackage
import com.ichi2.anki.exportCollectionPackage
import com.ichi2.anki.exportSelectedCards
import com.ichi2.anki.exportSelectedNotes
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shows the possible options for exporting(collection, decks or notes/card selection).
 * Intended to replicate the desktop UI.
 */
class ExportDialogFragment : DialogFragment() {
    private lateinit var binding: DialogExportOptionsBinding

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.containsKey(ARG_IDS_FILE) == true) {
            removeIdsFile()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogExportOptionsBinding.inflate(requireActivity().layoutInflater, null, false)
        binding.apply {
            initializeCommonUi()
            initializeCollectionExportUi()
            initializeApkgExportUi()
            initializeNotesExportUi()
            initializeCardsExportUi()
        }
        val extraDid = arguments?.getLong(ARG_DECK_ID, -1) // 0 is for "All decks"
        val extraType: ExportType? = arguments?.getSerializableCompat(ARG_TYPE)
        initializeDecks(extraDid)
        // start with the option for exporting a collection like on desktop unless we received a
        // deck id or a type of selection(plus selected ids), in this case preselect apkg export
        if ((extraDid != null && extraDid != -1L) || extraType != null) {
            binding.exportTypeSelector.setSelection(ExportConfiguration.Apkg.index)
            showExtrasOptionsFor(ExportConfiguration.Apkg)
        } else {
            binding.exportTypeSelector.setSelection(ExportConfiguration.Collection.index)
            showExtrasOptionsFor(ExportConfiguration.Collection)
        }
        return AlertDialog
            .Builder(requireActivity())
            .setView(binding.root)
            .negativeButton(R.string.dialog_cancel)
            .positiveButton(text = TR.actionsExport()) {
                val selectedIndex = binding.exportTypeSelector.selectedItemPosition
                // just to be safe, if not exporting a collection and the decks spinner is not
                // enabled(the user was really fast or fetching the decks is delayed for some
                // reason) then simply return
                if (selectedIndex != 0 && !binding.deckSelector.isEnabled) return@positiveButton
                when (ExportConfiguration.from(selectedIndex)) {
                    ExportConfiguration.Collection -> handleCollectionExport()
                    ExportConfiguration.Apkg -> handleAnkiPackageExport()
                    ExportConfiguration.Notes -> handleNotesInPlainTextExport()
                    ExportConfiguration.Cards -> handleCardsInPlainTextExport()
                }
            }.create()
    }

    /**
     * @param did the target deck id
     * @return returns the position of the deck with id inside the decks adapter or defaults to
     * 0("All decks") if a position wasn't found
     */
    private fun findDeckPosition(did: DeckId): Int {
        var position = 0
        val adapter = binding.deckSelector.adapter as DeckDisplayAdapter
        while (position < adapter.count) {
            if (adapter.getItem(position).id == did) {
                return position
            }
            position++
        }
        return if (position >= adapter.count) 0 else position
    }

    /**
     * Asynchronously initializes the decks selector. Expects to be called after the views were
     * initialized.
     *
     * @param selectedDeck the id of deck to select from the list of decks
     */
    private fun initializeDecks(selectedDeck: DeckId? = null) {
        lifecycleScope.launch {
            binding.deckSelector.isEnabled = false
            // add "All decks" option on first position to replicate desktop
            val allDecks =
                mutableListOf(
                    DeckNameId(
                        requireActivity().getString(R.string.card_browser_all_decks),
                        ALL_DECKS_ID,
                    ),
                )
            allDecks.addAll(withCol { decks.allNamesAndIds(false) })
            binding.deckSelector.adapter =
                DeckDisplayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    allDecks,
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
            if (selectedDeck != null) {
                binding.deckSelector.setSelection(findDeckPosition(selectedDeck))
            }
            binding.loadingDecksIndicator.isVisible = false
            binding.deckSelector.isEnabled = true
        }
    }

    private fun DialogExportOptionsBinding.initializeCommonUi(): Unit =
        with(CollectionManager.TR) {
            // parse the backend text for these labels as html because they contain html bold tags
            exportLabelType.text =
                HtmlCompat.fromHtml(exportingExportFormat(), HtmlCompat.FROM_HTML_MODE_LEGACY)
            exportLabelInclude.text =
                HtmlCompat.fromHtml(exportingInclude(), HtmlCompat.FROM_HTML_MODE_LEGACY)
            exportTypeSelector.apply {
                val exportTypesAdapter =
                    ArrayAdapter(
                        requireActivity(),
                        android.R.layout.simple_spinner_item,
                        listOf(
                            "${exportingAnkiCollectionPackage()} (.colpkg)",
                            "${exportingAnkiDeckPackage()} (.apkg)",
                            "${exportingNotesInPlainText()} (.txt)",
                            "${exportingCardsInPlainText()} (.txt)",
                        ),
                    ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                adapter = exportTypesAdapter
                onItemSelectedListener =
                    BasicItemSelectedListener { position, _ ->
                        showExtrasOptionsFor(ExportConfiguration.from(position))
                    }
            }
            selectedLabel.text = exportingSelectedNotes()
        }

    /**
     * Initializes the views representing the extra options available when exporting a collection.
     */
    private fun DialogExportOptionsBinding.initializeCollectionExportUi() =
        with(CollectionManager.TR) {
            collectionIncludeMedia.text = exportingIncludeMedia()
            collectionExportLegacy.text = exportingSupportOlderAnkiVersions()
        }

    /**
     * Initializes the views representing the extra options available when exporting an Anki package.
     */
    private fun DialogExportOptionsBinding.initializeApkgExportUi() =
        with(CollectionManager.TR) {
            apkgIncludeMedia.text = exportingIncludeMedia()
            apkgIncludeDeckConfigs.text = exportingIncludeDeckConfigs()
            apkgIncludeSchedule.text = exportingIncludeSchedulingInformation()
            apkgExportLegacy.text = exportingSupportOlderAnkiVersions()
        }

    /**
     * Initializes the views representing the extra options available when exporting notes.
     */
    private fun DialogExportOptionsBinding.initializeNotesExportUi() =
        with(CollectionManager.TR) {
            notesIncludeHtml.text = exportingIncludeHtmlAndMediaReferences()
            notesIncludeTags.text = exportingIncludeTags()
            notesIncludeDeckName.text = exportingIncludeDeck()
            notesIncludeNotetypeName.text = exportingIncludeNotetype()
            notesIncludeUniqueIdentifier.text = exportingIncludeGuid()
        }

    /**
     * Initializes the views representing the extra options available when exporting cards.
     */
    private fun DialogExportOptionsBinding.initializeCardsExportUi() =
        with(CollectionManager.TR) {
            cardsIncludeHtml.text = exportingIncludeHtmlAndMediaReferences()
        }

    /**
     * Displays the view containing the export extra options for the requested export type.
     */
    private fun showExtrasOptionsFor(targetConfig: ExportConfiguration) {
        // if we export as collection there's no deck/selected items to choose from
        if (targetConfig.layoutId == R.id.export_extras_collection) {
            binding.decksSelectorContainer.isVisible = false
            binding.selectedLabel.isVisible = false
        } else {
            if (arguments?.getSerializableCompat<ExportType>(ARG_TYPE) != null) {
                binding.decksSelectorContainer.isVisible = false
                binding.selectedLabel.isVisible = true
            } else {
                binding.decksSelectorContainer.isVisible = true
                binding.selectedLabel.isVisible = false
            }
        }
        ExportConfiguration.entries.forEach { config ->
            binding.root.findViewById<View>(config.layoutId).isVisible = config.layoutId == targetConfig.layoutId
        }
    }

    private fun handleCollectionExport() {
        val includeMedia = binding.collectionIncludeMedia.isChecked
        val legacy = binding.collectionExportLegacy.isChecked
        val exportPath =
            File(
                getExportRootFile(),
                "${CollectionManager.TR.exportingCollection()}-${getTimestamp(TimeManager.time)}.colpkg",
            ).path
        requireAnkiActivity().exportCollectionPackage(exportPath, includeMedia, legacy)
    }

    private fun handleAnkiPackageExport() {
        val limits = buildExportLimit()
        var packagePrefix = getNonCollectionNamePrefix()
        // files can't have `/` in their names
        packagePrefix = packagePrefix.replace("/", "_")
        val exportPath =
            File(
                getExportRootFile(),
                "$packagePrefix-${getTimestamp(TimeManager.time)}.apkg",
            ).path
        requireAnkiActivity().exportApkgPackage(
            exportPath = exportPath,
            withScheduling = binding.apkgIncludeSchedule.isChecked,
            withDeckConfigs = binding.apkgIncludeDeckConfigs.isChecked,
            withMedia = binding.apkgIncludeMedia.isChecked,
            limit = limits,
            legacy = binding.apkgExportLegacy.isChecked,
        )
    }

    /**
     * Builds the prefix for the name of the exported file. This will be  either a deck's name or a
     * localized "SelectedNotes" text.
     */
    private fun getNonCollectionNamePrefix(): String =
        when (arguments?.getSerializableCompat<ExportType>(ARG_TYPE)) {
            ExportType.Notes, ExportType.Cards -> CollectionManager.TR.exportingSelectedNotes()
            // notes/cards weren't selected so export the chosen deck(s)
            null -> (binding.deckSelector.adapter as DeckDisplayAdapter).getItem(binding.deckSelector.selectedItemPosition).name
        }

    private fun handleNotesInPlainTextExport() {
        val exportLimit = buildExportLimit()
        val exportPath =
            File(
                getExportRootFile(),
                "${getNonCollectionNamePrefix()}-${getTimestamp(TimeManager.time)}.txt",
            ).path
        requireAnkiActivity().exportSelectedNotes(
            exportPath = exportPath,
            withHtml = binding.notesIncludeHtml.isChecked,
            withTags = binding.notesIncludeTags.isChecked,
            withDeck = binding.notesIncludeDeckName.isChecked,
            withNotetype = binding.notesIncludeNotetypeName.isChecked,
            withGuid = binding.notesIncludeUniqueIdentifier.isChecked,
            limit = exportLimit,
        )
    }

    private fun handleCardsInPlainTextExport() {
        val exportLimit = buildExportLimit()
        val exportPath =
            File(
                getExportRootFile(),
                "${getNonCollectionNamePrefix()}-${getTimestamp(TimeManager.time)}.txt",
            ).path
        requireAnkiActivity().exportSelectedCards(
            exportPath = exportPath,
            withHtml = binding.cardsIncludeHtml.isChecked,
            limit = exportLimit,
        )
    }

    /**
     * Builds the [ExportLimit] to be used when exporting. This will either restrict the export to
     * the selected notes/cards or, if those are not present, to the selected
     * deck(or all decks).
     *
     * @return an [ExportLimit] with the export constraints
     */
    private fun buildExportLimit(): ExportLimit =
        when (arguments?.getSerializableCompat<ExportType>(ARG_TYPE)) {
            ExportType.Notes -> {
                val ids = requireArguments().requireParcelable<IdsFile>(ARG_IDS_FILE).getIds()

                exportLimit { noteIds = noteIds { this.noteIds.addAll(ids) } }
            }

            ExportType.Cards -> {
                val ids = requireArguments().requireParcelable<IdsFile>(ARG_IDS_FILE).getIds()

                exportLimit { cardIds = cardIds { this.cids.addAll(ids) } }
            }
            // notes/cards weren't selected so export the chosen decks
            null -> {
                val deckNameId =
                    (binding.deckSelector.adapter as DeckDisplayAdapter)
                        .getItem(binding.deckSelector.selectedItemPosition)
                if (deckNameId.id == ALL_DECKS_ID) {
                    exportLimit { this.wholeCollection = Empty.getDefaultInstance() }
                } else {
                    exportLimit { this.deckId = deckNameId.id }
                }
            }
        }

    /** Attempt to delete the associated [IdsFile] and logs the result */
    private fun removeIdsFile() {
        val idsFile = requireArguments().requireParcelable<IdsFile>(ARG_IDS_FILE)

        idsFile.removeSafely("ExportDialogFragment")
    }

    private fun getExportRootFile() =
        File(requireActivity().externalCacheDir, "export").also {
            it.mkdirs()
        }

    /**
     * An extension of [ArrayAdapter] which handles displaying a list of [DeckNameId] by their names
     * and which can also be queried for the [DeckNameId] for a position through [ArrayAdapter.getItem].
     */
    private class DeckDisplayAdapter(
        context: Context,
        @LayoutRes rowLayout: Int,
        private val decks: List<DeckNameId>,
    ) : ArrayAdapter<DeckNameId>(context, rowLayout, decks) {
        override fun getItem(position: Int): DeckNameId = decks[position]

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View =
            super.getView(position, convertView, parent).apply {
                findViewById<TextView>(android.R.id.text1).text = decks[position].name
            }

        override fun getDropDownView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View =
            super.getDropDownView(position, convertView, parent).apply {
                findViewById<TextView>(android.R.id.text1).text = decks[position].name
            }
    }

    /**
     * Holds information about the type of export.
     *
     * @param index the order of this export type in the list of possible options
     * @param layoutId the extra options views available for this export type
     */
    private enum class ExportConfiguration(
        val index: Int,
        @IdRes val layoutId: Int,
    ) {
        Collection(0, R.id.export_extras_collection),
        Apkg(1, R.id.export_extras_apkg),
        Notes(2, R.id.export_extras_notes),
        Cards(3, R.id.export_extras_cards),
        ;

        companion object {
            fun from(index: Int) = entries.first { it.index == index }
        }
    }

    /**
     * Identifier for the list of ids that can be passed to [ExportDialogFragment]. Currently either
     * notes or cards.
     */
    enum class ExportType {
        Notes,
        Cards,
    }

    companion object {
        private const val ARG_DECK_ID = "arg_deck_id"
        private const val ARG_TYPE = "arg_type"
        private const val ARG_IDS_FILE = "arg_ids_file"

        /**
         * Create a new instance of this dialog without any initial constraints(for example when
         * trying to export from [com.ichi2.anki.DeckPicker]'s menu option).
         */
        fun newInstance(): ExportDialogFragment = ExportDialogFragment()

        /**
         * Create a new instance of this dialog targeting a specific deck.
         */
        fun newInstance(did: DeckId) =
            ExportDialogFragment().apply {
                arguments = bundleOf(ARG_DECK_ID to did)
            }

        /**
         * Create a new instance of this dialog targeting a selection of cards or notes for export.
         */
        fun newInstance(
            cacheDir: File,
            type: ExportType,
            ids: List<Long>,
        ) = ExportDialogFragment().apply {
            val idsFile = IdsFile(cacheDir, ids, "export")

            arguments =
                Bundle().apply {
                    putSerializable(ARG_TYPE, type)
                    putParcelable(ARG_IDS_FILE, idsFile)
                }
        }
    }
}
