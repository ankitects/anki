// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import anki.cards.cardIds
import anki.generic.Empty
import anki.import_export.ExportLimit
import anki.import_export.exportLimit
import anki.notes.noteIds
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.browser.removeSafely
import com.ichi2.anki.common.ALL_DECKS_ID
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.databinding.DialogExportOptionsBinding
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.registerChildDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.exportApkgPackage
import com.ichi2.anki.exportCollectionPackage
import com.ichi2.anki.exportSelectedCards
import com.ichi2.anki.exportSelectedNotes
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.getLongOrNull
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Shows the possible options for exporting(collection, decks or notes/card selection).
 * Intended to replicate the desktop UI.
 */
class ExportDialogFragment : AnalyticsDialogFragment() {
    @VisibleForTesting
    internal lateinit var binding: DialogExportOptionsBinding
        private set

    private var selectedDeck: SelectableDeck? = null

    private var deckSelection: Job? = null

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // onDismiss is also called on a configuration change (from onDestroyView), in which case
        // the dialog is recreated with the same arguments and still needs the ids file
        if (activity?.isChangingConfigurations == true) return
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
        initializeDecks(savedInstanceState?.getLongOrNull(STATE_SELECTED_DECK_ID) ?: extraDid)
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
            .setTitle(TR.actionsExport())
            .setView(binding.root)
            .negativeButton(CommonString.dialog_cancel)
            .positiveButton(text = TR.actionsExport()) {
                val selectedIndex = binding.exportTypeSelector.selectedItemPosition
                // just to be safe, if not exporting a collection and the deck selector is not
                // enabled(the user was really fast or fetching the decks is delayed for some
                // reason) then simply return
                if (selectedIndex != 0 && !binding.deckSelector.isEnabled) return@positiveButton
                when (ExportConfiguration.from(selectedIndex)) {
                    ExportConfiguration.Collection -> handleCollectionExport()
                    ExportConfiguration.Apkg -> withExportLimit(::handleAnkiPackageExport)
                    ExportConfiguration.Notes -> withExportLimit(::handleNotesInPlainTextExport)
                    ExportConfiguration.Cards -> withExportLimit(::handleCardsInPlainTextExport)
                }
            }.create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedDeck?.let { outState.putLong(STATE_SELECTED_DECK_ID, (it as? SelectableDeck.Deck)?.deckId ?: ALL_DECKS_ID) }
    }

    /**
     * Asynchronously initializes the decks selector. Expects to be called after the views were
     * initialized.
     *
     * @param deckId the id of the deck to select, "All decks" is selected if there is no such deck
     */
    private fun initializeDecks(deckId: DeckId?) {
        registerChildDeckSelectedHandler(REQUEST_EXPORT_DECK) { it?.let(::showSelectedDeck) }
        binding.deckSelector.setOnClickListener {
            childFragmentManager.executePendingTransactions()
            if (deckSelection?.isActive == true || childFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG) != null) {
                return@setOnClickListener
            }
            deckSelection = startDeckSelection(asChild = true, requestKey = REQUEST_EXPORT_DECK)
        }
        ViewCompat.setAccessibilityDelegate(
            binding.deckSelector,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Spinner::class.java.name
                    info.setCanOpenPopup(true)
                }
            },
        )
        lifecycleScope.launch {
            binding.deckSelector.isEnabled = false
            val deck =
                withCol { decks.get(deckId, default = false) }
                    ?.let { SelectableDeck.Deck(it.id, it.name) }
                    ?: SelectableDeck.AllDecks
            showSelectedDeck(deck)
            binding.loadingDecksIndicator.isVisible = false
            binding.deckSelector.isEnabled = true
        }
    }

    private fun showSelectedDeck(deck: SelectableDeck) {
        selectedDeck = deck
        binding.deckSelector.text = deck.getFullDisplayName(requireContext())
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
                            "${sentenceCase.ankiCollectionPackage} (.colpkg)",
                            "${sentenceCase.ankiDeckPackage} (.apkg)",
                            "${sentenceCase.notesInPlainText} (.txt)",
                            "${sentenceCase.cardsInPlainText} (.txt)",
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

    private fun withExportLimit(export: (ExportLimit) -> Unit) {
        val exportLimit =
            try {
                buildExportLimit()
            } catch (e: IOException) {
                Timber.w(e, "Failed to read export IDs")
                null
            }
        if (exportLimit == null) {
            showThemedToast(requireContext(), CommonString.something_wrong, false)
            return
        }
        export(exportLimit)
    }

    private fun handleAnkiPackageExport(exportLimit: ExportLimit) {
        val exportPath =
            File(
                getExportRootFile(),
                "${getNonCollectionNamePrefix()}-${getTimestamp(TimeManager.time)}.apkg",
            ).path
        requireAnkiActivity().exportApkgPackage(
            exportPath = exportPath,
            withScheduling = binding.apkgIncludeSchedule.isChecked,
            withDeckConfigs = binding.apkgIncludeDeckConfigs.isChecked,
            withMedia = binding.apkgIncludeMedia.isChecked,
            limit = exportLimit,
            legacy = binding.apkgExportLegacy.isChecked,
        )
    }

    /**
     * Builds the prefix for the name of the exported file. This will be  either a deck's name or a
     * localized "SelectedNotes" text.
     */
    // TODO: return [Filename] once the construction of export paths is refactored
    private fun getNonCollectionNamePrefix(): String {
        val filename =
            Filename.sanitize(
                when (arguments?.getSerializableCompat<ExportType>(ARG_TYPE)) {
                    ExportType.Notes, ExportType.Cards -> CollectionManager.TR.exportingSelectedNotes()
                    // notes/cards weren't selected so export the chosen deck(s)
                    null -> requireNotNull(selectedDeck).getFullDisplayName(requireContext())
                },
            )
        return filename.value
    }

    private fun handleNotesInPlainTextExport(exportLimit: ExportLimit) {
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

    private fun handleCardsInPlainTextExport(exportLimit: ExportLimit) {
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
     * @return an [ExportLimit] with the export constraints, or null if the selection is unavailable
     */
    private fun buildExportLimit(): ExportLimit? {
        return when (arguments?.getSerializableCompat<ExportType>(ARG_TYPE)) {
            ExportType.Notes -> {
                val ids = requireArguments().requireParcelable<IdsFile>(ARG_IDS_FILE).getIdsOrNull() ?: return null

                exportLimit { noteIds = noteIds { this.noteIds.addAll(ids) } }
            }

            ExportType.Cards -> {
                val ids = requireArguments().requireParcelable<IdsFile>(ARG_IDS_FILE).getIdsOrNull() ?: return null

                exportLimit { cardIds = cardIds { this.cids.addAll(ids) } }
            }
            // notes/cards weren't selected so export the chosen decks
            null ->
                when (val deck = requireNotNull(selectedDeck)) {
                    SelectableDeck.AllDecks -> exportLimit { this.wholeCollection = Empty.getDefaultInstance() }
                    is SelectableDeck.Deck -> exportLimit { this.deckId = deck.deckId }
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
        private const val REQUEST_EXPORT_DECK = "request_export_deck"
        private const val STATE_SELECTED_DECK_ID = "selected_deck_id"

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
                arguments = Bundle().apply { putLong(ARG_DECK_ID, did) }
            }

        /**
         * Create a new instance of this dialog targeting a selection of cards or notes for export.
         */
        fun newInstance(
            idsFileDirectory: File,
            type: ExportType,
            ids: List<Long>,
        ) = ExportDialogFragment().apply {
            val idsFile = IdsFile(idsFileDirectory, ids, IdsFile.Purpose.EXPORT)

            arguments =
                Bundle().apply {
                    putSerializable(ARG_TYPE, type)
                    putParcelable(ARG_IDS_FILE, idsFile)
                }
        }
    }
}
