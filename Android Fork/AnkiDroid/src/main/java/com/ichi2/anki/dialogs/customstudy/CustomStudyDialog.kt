// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs.customstudy

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Resources
import android.os.Bundle
import android.os.Parcelable
import android.text.InputFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import anki.scheduler.CustomStudyDefaultsResponse
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import anki.scheduler.copy
import anki.scheduler.customStudyRequest
import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.asyncIO
import com.ichi2.anki.browser.search.CardState
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.DialogCustomStudyMenuBinding
import com.ichi2.anki.databinding.FragmentCustomStudyBinding
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.EXTEND_NEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.EXTEND_REV
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_AHEAD
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_FORGOT
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_PREVIEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption.STUDY_TAGS
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.REQUEST_SELECT_TAGS
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.RESULT_SELECTED_TAGS
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.requireActivity
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.getIntOrNull
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.withProgress
import com.ichi2.utils.NonLeadingZeroInputFilter
import com.ichi2.utils.coMeasureTime
import com.ichi2.utils.customView
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.textAsIntOrNull
import com.ichi2.utils.title
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/**
 * Implements custom studying either by:
 * 1. modifying the limits of the current selected deck for when the user has reached the daily
 *    deck limits and wishes to study more
 * 2. creating a filtered "Custom study deck" where a user can study outside the typical schedule
 *
 *
 * ## UI
 * [CustomStudyDialog] represents either:
 * * A [main menu][buildContextMenu], offering [methods of custom study][ContextMenuOption]
 * * An [input dialog][buildInputDialog] to input additional constraints for a [ContextMenuOption]
 *    * Example: changing the number of new cards
 *
 * Note: when studying by tags the input dialog will also display a state selector and on user
 * action will show one extra dialog(for actual tag selection, see [TagLimitFragment])
 *
 * ## Nomenclature
 * Filtered decks were previously known as 'dynamic' decks, and before that: 'cram' decks
 *
 * ## Links
 * * [https://docs.ankiweb.net/filtered-decks.html#custom-study](https://docs.ankiweb.net/filtered-decks.html#custom-study)
 * * [com.ichi2.anki.libanki.sched.Scheduler.customStudyDefaults]
 *     * [sched.proto: CustomStudyDefaultsResponse](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyDefaultsResponse+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
 * * [com.ichi2.anki.libanki.sched.Scheduler.customStudy]
 *     * [sched.proto: CustomStudyRequest](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyRequest+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
 * * [https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py](https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py)
 * * [https://github.com/ankitects/anki/blob/main/qt/aqt/taglimit](https://github.com/ankitects/anki/blob/main/qt/aqt/taglimit.py)
 *
 * @see TagLimitFragment
 */
class CustomStudyDialog : AnalyticsDialogFragment() {
    @VisibleForTesting(otherwise = PRIVATE)
    lateinit var binding: FragmentCustomStudyBinding

    @VisibleForTesting(otherwise = PRIVATE)
    val viewModel by viewModels<CustomStudyViewModel>()

    /**
     * `null` initially when the main view is shown
     * otherwise, the [ContextMenuOption] representing the current sub-dialog
     */
    private val selectedSubDialog: ContextMenuOption?
        get() = requireArguments().getIntOrNull(ARG_SUB_DIALOG_ID)?.let { ContextMenuOption.entries[it] }

    private val userInputValue: Int?
        get() = binding.detailsEditText2.textAsIntOrNull()

    /** The search for cards to review ahead. Cancelled when the input changes, so only the latest input counts */
    private var searchJob: Job? = null

    /** Whether a menu availability check or transition is already in progress. */
    private var menuSelectionInProgress = false

    /** Defaults captured for this input dialog when its main menu finishes loading. */
    private lateinit var defaults: CustomStudyDefaults

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val option = selectedSubDialog ?: return
        parentFragmentManager.setFragmentResultListener(REQUEST_SELECT_TAGS, this) { _, bundle ->
            val tagsToInclude = bundle.getStringArrayList(RESULT_SELECTED_TAGS) ?: emptyList<String>()
            val selectedCardStateIndex = viewModel.selectedCardStateIndex
            if (selectedCardStateIndex == AdapterView.INVALID_POSITION) return@setFragmentResultListener
            val kind = viewModel.selectedKind
            val cardsAmount = userInputValue ?: 100 // the default value
            launchCustomStudy(option, cardsAmount, kind, tagsToInclude, emptyList())
        }
    }

    /** @see customStudy */
    private fun launchCustomStudy(
        option: ContextMenuOption,
        cardsAmount: Int,
        kind: CramKind = CramKind.CRAM_KIND_NEW,
        tagsToInclude: List<String> = emptyList(),
        tagsToExclude: List<String> = emptyList(),
    ) {
        // The activity owns this operation, which can outlive the dialog.
        val activity = requireActivity(simpleName = true)
        val fragmentManager = parentFragmentManager
        val deckId = viewModel.deckId
        val preferences = activity.sharedPrefs()
        activity.launchCatchingTask(
            // net.ankiweb.rsdroid.BackendException: No cards matched the criteria you provided.
            // TODO (Backend#256: make this BackendCustomStudyException)
            skipCrashReport = { it is BackendException },
        ) {
            activity.withProgress {
                try {
                    val action = customStudy(deckId, option, cardsAmount, kind, tagsToInclude, tagsToExclude)
                    fragmentManager.setFragmentResult(
                        CustomStudyAction.REQUEST_KEY,
                        Bundle().apply { putInt(CustomStudyAction.RESULT_CUSTOM_STUDY_ACTION, action.ordinal) },
                    )

                    // Save the default values (not in upstream).
                    when (option) {
                        STUDY_FORGOT -> preferences.edit { putInt("forgottenDays", cardsAmount) }
                        STUDY_AHEAD -> preferences.edit { putInt("aheadDays", cardsAmount) }
                        STUDY_PREVIEW -> preferences.edit { putInt("previewDays", cardsAmount) }
                        STUDY_TAGS -> preferences.edit { putInt("amountOfCards", cardsAmount) }
                        EXTEND_NEW, EXTEND_REV -> {
                            // The backend provides the default value.
                        }
                    }
                } finally {
                    activity.dismissAllDialogFragments()
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val option = selectedSubDialog
        val ready = readyInitialization()
        return if (option == null || (option.checkAvailability != null && ready?.deckId != viewModel.deckId)) {
            Timber.i("Showing Custom Study main menu")
            val initialization = loadInitialization()
            deferredInitialization = initialization
            buildContextMenu(initialization)
        } else {
            Timber.i("Showing Custom Study dialog: $option")
            if (ready?.deckId == viewModel.deckId) {
                defaults = ready.defaults
            }
            buildInputDialog(option)
        }
    }

    /**
     * Continues the custom study process by showing an input dialog where the user can enter an
     * amount specific to that type of custom study(eg. cards, days etc).
     */
    private suspend fun onMenuItemSelected(
        menuDialog: Dialog,
        item: ContextMenuOption,
        defaults: CustomStudyDefaults,
        onEmptyDeck: () -> Unit,
    ) {
        if (menuSelectionInProgress) return
        menuSelectionInProgress = true
        try {
            // The deck may have changed since this menu loaded.
            val isUnavailable = withProgress { viewModel.isDeckEmptyOrHidden() }
            // The fragment can outlive this dialog and recreate it while the check is queued.
            if (
                this.dialog !== menuDialog ||
                !menuDialog.isShowing ||
                !isResumed ||
                requireActivity().isFinishing ||
                parentFragmentManager.isStateSaved
            ) {
                return
            }
            if (isUnavailable) {
                onEmptyDeck()
                return
            }
            if (item.checkAvailability?.invoke(defaults) == false) {
                showSnackbar(getString(CommonString.studyoptions_no_cards_due))
                return
            }

            val dialog: CustomStudyDialog = createSubDialog(viewModel.deckId, item)
            requireActivity().showDialogFragment(dialog)
        } finally {
            menuSelectionInProgress = false
        }
    }

    private fun buildContextMenu(initialization: Deferred<CustomStudyInitialization>): AlertDialog {
        val dialog =
            object : AlertDialog(requireActivity()) {
                override fun onStop() {
                    // Native dismissal happens before DialogFragment receives its queued callback.
                    initialization.cancel()
                    super.onStop()
                }
            }
        val inflater = LayoutInflater.from(dialog.context)
        val menuBinding = DialogCustomStudyMenuBinding.inflate(inflater)
        val container = menuBinding.menuItems
        dialog.setTitle(TR.sentenceCase.customStudy)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setView(
            FrameLayout(dialog.context).apply {
                addView(
                    menuBinding.root,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )

        val ta = TypedValue()
        dialog.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ta, true)

        fun buildMenuItems(
            defaults: CustomStudyDefaults,
            onEmptyDeck: () -> Unit,
        ) {
            ContextMenuOption.entries
                .map { option ->
                    Pair(
                        option,
                        option.checkAvailability?.invoke(defaults) != false,
                    )
                }.forEach { (menuItem, isItemEnabled) ->
                    (inflater.inflate(android.R.layout.simple_list_item_1, container, false) as TextView)
                        .apply {
                            updatePadding(
                                top = 12.dp.toPx(requireContext()),
                                bottom = 12.dp.toPx(requireContext()),
                            )
                            text = menuItem.getTitle(requireContext().resources)
                            isEnabled = isItemEnabled
                            setBackgroundResource(ta.resourceId)
                            setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                            setOnClickListener {
                                launchCatchingTask {
                                    onMenuItemSelected(dialog, menuItem, defaults, onEmptyDeck)
                                }
                            }
                        }.also { container.addView(it) }
                }
        }

        fun render(state: CustomStudyInitialization) {
            container.removeAllViews()
            menuBinding.progressBar.isVisible = state is CustomStudyInitialization.Loading
            menuBinding.menuItems.isVisible = state is CustomStudyInitialization.Ready
            when (state) {
                CustomStudyInitialization.Loading -> Unit
                CustomStudyInitialization.Empty ->
                    lifecycleScope.launch {
                        lifecycle.withResumed {
                            val host = activity ?: return@withResumed
                            if (!dialog.isShowing || host.isFinishing || parentFragmentManager.isStateSaved) return@withResumed
                            dismiss()
                            host.showSnackbar(CommonString.empty_deck)
                        }
                    }
                is CustomStudyInitialization.Ready -> buildMenuItems(state.defaults) { render(CustomStudyInitialization.Empty) }
            }
        }

        render(CustomStudyInitialization.Loading)
        dialog.setOnShowListener {
            if (!isAdded) return@setOnShowListener
            launchCatchingTask {
                render(initialization.await())
            }
        }

        return dialog
    }

    /**
     * Build an input dialog that is used to get a parameter related to custom study from the user
     * @param contextMenuOption the option of the dialog
     */
    @NeedsTest("17757: fragment not dismissed before result is output")
    private fun buildInputDialog(contextMenuOption: ContextMenuOption): AlertDialog {
        /*
            TODO: Try to change to a standard input dialog (currently the thing holding us back is having the extra
            TODO: hint line for the number of cards available, and having the pre-filled text selected by default)
         */
        // Input dialogs
        // Show input dialog for an individual custom study dialog
        @SuppressLint("InflateParams")
        binding = FragmentCustomStudyBinding.inflate(requireActivity().layoutInflater)

        val isExtendLimits = contextMenuOption == EXTEND_NEW || contextMenuOption == EXTEND_REV
        // 'review ahead' has a dialog title, so the empty label would only add a blank line
        binding.detailsText1.isVisible =
            contextMenuOption != STUDY_AHEAD &&
            contextMenuOption != STUDY_PREVIEW &&
            !isExtendLimits
        binding.detailsText2.text = text2
        binding.detailsAvailableCards.text = text1
        binding.detailsAvailableCards.isVisible = isExtendLimits
        binding.detailsDecrementButton.isVisible = isExtendLimits
        binding.detailsIncrementButton.isVisible = isExtendLimits
        binding.detailsDecrementButton.setOnClickListener { stepUserInputValue(-1) }
        binding.detailsIncrementButton.setOnClickListener { stepUserInputValue(1) }

        binding.cardsStateSelectorLayout.isVisible = contextMenuOption == STUDY_TAGS
        binding.cardsStateSelector.apply {
            fun setAdapterAndSelection(
                entries: List<String>,
                selectedIndex: Int,
            ) {
                setAdapter(
                    ArrayAdapter(
                        requireActivity(),
                        R.layout.item_multiline_spinner,
                        entries,
                    ).apply { setDropDownViewResource(R.layout.item_multiline_spinner) },
                )
                setText(entries[selectedIndex], false)
            }
            val cardStates = CustomStudyCardState.entries.map { it.labelProducer() }
            onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    viewModel.selectedCardStateIndex = position
                    setAdapterAndSelection(cardStates, viewModel.selectedCardStateIndex)
                }
            // set the first item as automatically selected if we don't already have a valid
            // position stored in the ViewModel
            if (viewModel.selectedCardStateIndex == AdapterView.INVALID_POSITION) {
                viewModel.selectedCardStateIndex = 0
            }
            setAdapterAndSelection(cardStates, viewModel.selectedCardStateIndex)
        }
        binding.detailsEditText2.apply {
            setText(viewModel.userInputText ?: defaultValue)
            // Give EditText focus and show keyboard
            setSelectAllOnFocus(true)
            requestFocus()
            // a user may enter a negative value when extending limits
            if (isExtendLimits) {
                inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_SIGNED
                gravity = Gravity.CENTER
            }
            if (contextMenuOption == STUDY_AHEAD || contextMenuOption == STUDY_PREVIEW) {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                filters += arrayOf(InputFilter.LengthFilter(5), NonLeadingZeroInputFilter)
                setSuffixText(userInputValue ?: defaultValue.toInt())
            }
        }
        val positiveBtnLabel =
            if (contextMenuOption == STUDY_TAGS) {
                TR.sentenceCase.chooseTags
            } else if (contextMenuOption == STUDY_AHEAD || contextMenuOption == STUDY_PREVIEW) {
                getString(CommonString.dialog_positive_create)
            } else if (isExtendLimits) {
                getString(CommonString.custom_study_increase)
            } else {
                getString(CommonString.dialog_ok)
            }

        // Set material dialog parameters
        @Suppress("RedundantValueArgument") // click = null
        val horizontalPadding = 32.dp.toPx(requireContext())
        val verticalPadding = 16.dp.toPx(requireContext())
        val dialog =
            AlertDialog
                .Builder(requireActivity())
                .apply {
                    if (contextMenuOption == STUDY_AHEAD || contextMenuOption == STUDY_PREVIEW) {
                        title(text = contextMenuOption.getTitle(resources))
                    } else if (isExtendLimits) {
                        title(text = getString(CommonString.custom_study_extend_limits_title))
                    }
                }.customView(
                    view = binding.root,
                    paddingStart = horizontalPadding,
                    paddingEnd = horizontalPadding,
                    paddingTop = verticalPadding,
                    paddingBottom = verticalPadding,
                ).positiveButton(text = positiveBtnLabel, click = null)
                .negativeButton(CommonString.dialog_cancel) {
                    requireActivity().dismissAllDialogFragments()
                }.create()

        var allowSubmit = true
        // we set the listener here so 'ok' doesn't immediately close the dialog.
        // if it did, we would not have had time to execute the method, and would not be
        // able to output a fragment result
        dialog.setOnShowListener {
            dialog.positiveButton.setOnClickListener {
                // prevent race conditions
                if (!allowSubmit) return@setOnClickListener
                allowSubmit = false

                // Get the value selected by user
                val n =
                    userInputValue ?: run {
                        Timber.w("Non-numeric user input was provided")
                        Timber.d("value: %s", binding.detailsEditText2.text.toString())
                        allowSubmit = true
                        return@setOnClickListener
                    }
                if (contextMenuOption == STUDY_TAGS) {
                    // mark allowSubmit as true because, if the user cancels TagLimitFragment, when
                    // we come back we wouldn't be able to trigger again TagLimitFragment
                    allowSubmit = true
                    launchCatchingTask {
                        val nids =
                            withCol {
                                val currentDeckName = decks.name(viewModel.deckId)
                                // this allows us to skip the tag selection dialog entirely
                                // if there are no tags available to choose from in this deck
                                val searchNodes =
                                    buildList {
                                        add(SearchNode.newBuilder().setDeck(currentDeckName).build())
                                        add(
                                            SearchNode
                                                .newBuilder()
                                                .setNegated(SearchNode.newBuilder().setTag("none").build())
                                                .build(),
                                        )
                                    }
                                findNotes(buildSearchString(searchNodes))
                            }
                        // skip tag selection if there's no tags to select
                        if (nids.isEmpty()) {
                            launchCustomStudy(contextMenuOption, n, viewModel.selectedKind)
                            return@launchCatchingTask
                        }
                        if (isAdded) {
                            TagsDialog()
                                .withArguments(
                                    requireContext(),
                                    TagsDialog.DialogType.CUSTOM_STUDY,
                                    nids,
                                ).show(parentFragmentManager, "TagsDialog")
                        }
                    }
                    return@setOnClickListener
                }
                launchCustomStudy(contextMenuOption, n)
            }
            validateInput(dialog, contextMenuOption)
        }

        binding.detailsEditText2.doAfterTextChanged {
            viewModel.userInputText = it?.toString()
            validateInput(dialog, contextMenuOption)
        }

        // Show soft keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    private fun validateInput(
        dialog: AlertDialog,
        contextMenuOption: ContextMenuOption,
    ) {
        val rawText = binding.detailsEditText2.text?.toString() ?: ""
        val value = userInputValue
        val range = contextMenuOption.validRange

        if (contextMenuOption == STUDY_AHEAD || contextMenuOption == STUDY_PREVIEW) {
            value?.let { setSuffixText(it) }
            searchJob?.cancel()
            searchJob = launchCatchingTask { updateCreateButtonState(dialog, value) }
            return
        }

        val isValid = value != null && value != 0 && value in range
        dialog.positiveButton.isEnabled = isValid

        binding.detailsEditText2Layout.error =
            when {
                rawText.isEmpty() || rawText == "-" -> null
                value == null || value > range.last -> getString(CommonString.maximum_value_is, range.last)
                value < range.first -> getString(CommonString.minimum_value_is, range.first)
                else -> null
            }
    }

    /** Sets the suffix of the days input: `[1] day`, `[3] days` */
    private fun setSuffixText(days: Int) {
        binding.detailsEditText2Layout.suffixText = resources.getQuantityString(CommonPlurals.set_due_date_label_suffix, days)
    }

    private fun stepUserInputValue(delta: Int) {
        val range = selectedSubDialog?.validRange ?: return
        val newValue = ((userInputValue ?: 0) + delta).coerceIn(range)
        binding.detailsEditText2.setText(newValue.toString())
        binding.detailsEditText2.moveCursorToEnd()
    }

    /**
     * Whether there are new cards added within [days] days in the deck for previewing.
     *
     * Mirrors the upstream logic:
     * [https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py](https://github.com/ankitects/anki/blob/main/qt/aqt/customstudy.py)
     */
    private suspend fun hasPreviewCards(days: Int): Boolean =
        withCol {
            val search =
                listOf(
                    searchNode { deck = decks.name(viewModel.deckId) },
                    CardState.New.toSearchNode(),
                    searchNode { addedInDays = days },
                )
            findCards(buildSearchString(search)).isNotEmpty()
        }

    /** Enables 'Create' only if some cards would be reviewed ahead/previewed by [days] */
    private suspend fun updateCreateButtonState(
        dialog: AlertDialog,
        days: Int?,
    ) {
        val range = selectedSubDialog?.validRange ?: ContextMenuOption.STUDY_AHEAD.validRange
        val rawText = binding.detailsEditText2.text?.toString() ?: ""
        if (days == null || days !in range) {
            binding.detailsEditText2Layout.error =
                when {
                    rawText.isEmpty() -> null
                    days == null || days > range.last -> getString(CommonString.maximum_value_is, range.last)
                    days < range.first -> getString(CommonString.minimum_value_is, range.first)
                    else -> null
                }
            dialog.positiveButton.isEnabled = false
            return
        }
        val hasCards =
            when (selectedSubDialog) {
                STUDY_PREVIEW -> hasPreviewCards(days)
                else -> hasCardsDueWithin(days)
            }
        binding.detailsEditText2Layout.error = if (hasCards) null else TR.customStudyNoCardsMatchedTheCriteriaYou()
        dialog.positiveButton.isEnabled = hasCards
    }

    /** Whether the deck has cards due in the next [days] days, as 'review ahead' would select them */
    private suspend fun hasCardsDueWithin(days: Int): Boolean =
        withCol {
            val search =
                listOf(
                    SearchNode.newBuilder().setDeck(decks.name(viewModel.deckId)).build(),
                    // prop:due<=days
                    SearchNode.newBuilder().setDueInDays(days).build(),
                )
            findCards(buildSearchString(search)).isNotEmpty()
        }

    // TODO cram kind and the included/excluded tags lists are only relevant for STUDY_TAGS and
    //  should be included in the option to not leak in the method's api
    private suspend fun customStudy(
        deckId: DeckId,
        contextMenuOption: ContextMenuOption,
        userEntry: Int,
        cramKind: CramKind,
        tagsSelectedForInclude: List<String>,
        tagsSelectedForExclude: List<String>,
    ): CustomStudyAction {
        Timber.i("Custom study: $contextMenuOption; input = $userEntry")

        val request =
            customStudyRequest {
                this.deckId = deckId
                when (contextMenuOption) {
                    EXTEND_NEW -> newLimitDelta = userEntry
                    EXTEND_REV -> reviewLimitDelta = userEntry
                    STUDY_FORGOT -> forgotDays = userEntry
                    STUDY_AHEAD -> reviewAheadDays = userEntry
                    STUDY_PREVIEW -> previewDays = userEntry
                    STUDY_TAGS -> {
                        // https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/customstudy.py#L169-L177
                        cram =
                            cram.copy {
                                kind = cramKind
                                cardLimit = userEntry
                                tagsToInclude.addAll(tagsSelectedForInclude)
                                tagsToExclude.addAll(tagsSelectedForExclude)
                            }
                    }
                }
            }

        undoableOp { sched.customStudy(request) }
        return when (contextMenuOption) {
            EXTEND_NEW, EXTEND_REV -> CustomStudyAction.EXTEND_STUDY_LIMITS
            STUDY_FORGOT, STUDY_AHEAD, STUDY_PREVIEW, STUDY_TAGS -> CustomStudyAction.CUSTOM_STUDY_SESSION
        }
    }

    /**
     * Loads [CustomStudyDefaults] from the backend
     *
     * This method may be slow (> 1s)
     */
    private fun loadInitialization(): Deferred<CustomStudyInitialization> {
        val host = requireActivity()
        return lifecycleScope.asyncIO {
            coMeasureTime("loadCustomStudyDefaults") {
                viewModel.initialize { !host.isFinishing }
            }
        }
    }

    /**
     * Line 1 of the number entry dialog
     *
     * e.g. "Review forgotten cards"
     *
     * Requires [ContextMenuOption.checkAvailability] to be null/return true
     */
    private val text1: String
        get() =
            when (selectedSubDialog) {
                EXTEND_NEW -> defaults.labelForNewQueueAvailable()
                EXTEND_REV -> defaults.labelForReviewQueueAvailable()
                STUDY_FORGOT,
                STUDY_AHEAD,
                STUDY_PREVIEW,
                STUDY_TAGS,
                null,
                -> ""
            }

    /** Line 2 of the number entry dialog */
    private val text2: String
        get() {
            val res = resources
            return when (selectedSubDialog) {
                EXTEND_NEW -> TR.customStudyIncreaseTodaysNewCardLimitBy()
                EXTEND_REV -> TR.customStudyIncreaseTodaysReviewLimitBy()
                STUDY_FORGOT -> res.getString(CommonString.custom_study_forgotten)
                STUDY_AHEAD -> res.getString(CommonString.custom_study_ahead_description)
                STUDY_PREVIEW -> res.getString(CommonString.custom_study_preview)
                STUDY_TAGS -> res.getString(CommonString.custom_study_tags)
                null -> ""
            }
        }

    /**
     * Initial value of the number entry dialog
     *
     * Requires [ContextMenuOption.checkAvailability] to be null/return true
     */
    private val defaultValue: String
        get() {
            val prefs = requireActivity().sharedPrefs()
            return when (selectedSubDialog) {
                EXTEND_NEW -> defaults.extendNew.initialValue.toString()
                EXTEND_REV -> defaults.extendReview.initialValue.toString()
                STUDY_FORGOT -> prefs.getInt("forgottenDays", 1).toString()
                STUDY_AHEAD -> prefs.getInt("aheadDays", 1).toString()
                STUDY_PREVIEW -> prefs.getInt("previewDays", 1).toString()
                // currently(as of Anki 25.02) not upstream
                STUDY_TAGS -> prefs.getInt("amountOfCards", 100).toString()
                null,
                -> ""
            }
        }

    /**
     * Represents actions for managing custom study sessions and extending study limits.
     * These actions are passed between fragments and activities via the FragmentResult API.
     */
    enum class CustomStudyAction {
        EXTEND_STUDY_LIMITS,
        CUSTOM_STUDY_SESSION,
        ;

        companion object {
            const val REQUEST_KEY = "CustomStudyDialog"
            const val RESULT_CUSTOM_STUDY_ACTION = "action"

            /** Extracts a [CustomStudyAction] from a [Bundle] */
            fun fromBundle(bundle: Bundle): CustomStudyAction =
                bundle.getInt(CustomStudyAction.RESULT_CUSTOM_STUDY_ACTION).let { actionOrdinal ->
                    entries.first { it.ordinal == actionOrdinal }
                }
        }
    }

    /**
     * Context menu options shown in the custom study dialog.
     *
     * @param checkAvailability Whether the menu option is available
     */
    @VisibleForTesting(otherwise = PRIVATE)
    enum class ContextMenuOption(
        val getTitle: Resources.() -> String,
        val validRange: IntRange,
        val checkAvailability: ((CustomStudyDefaults) -> Boolean)? = null,
    ) {
        /** Increase today's new card limit */
        EXTEND_NEW(
            { TR.customStudyIncreaseTodaysNewCardLimit() },
            validRange = -Consts.DYN_MAX_SIZE..Consts.DYN_MAX_SIZE,
            checkAvailability = { it.extendNew.isUsable },
        ),

        /** Increase today's review card limit */
        EXTEND_REV(
            { TR.customStudyIncreaseTodaysReviewCardLimit() },
            validRange = -Consts.DYN_MAX_SIZE..Consts.DYN_MAX_SIZE,
            checkAvailability = { it.extendReview.isUsable },
        ),

        /** Review forgotten cards */
        STUDY_FORGOT({ TR.customStudyReviewForgottenCards() }, validRange = 1..FORGOT_MAX_DAYS),

        /** Review ahead */
        STUDY_AHEAD({ TR.customStudyReviewAhead() }, validRange = 1..Consts.DYN_MAX_SIZE),

        /** Preview new cards */
        STUDY_PREVIEW({ TR.customStudyPreviewNewCards() }, validRange = 1..Consts.DYN_MAX_SIZE),

        /** Limit to particular tags */
        STUDY_TAGS({ TR.customStudyStudyByCardStateOrTag() }, validRange = 1..Consts.DYN_MAX_SIZE),
    }

    @Parcelize
    enum class CustomStudyCardState(
        val labelProducer: () -> String,
        val kind: CramKind,
    ) : Parcelable {
        NewCardsOnly({ TR.customStudyNewCardsOnly() }, CramKind.CRAM_KIND_NEW),
        DueCardsOnly({ TR.customStudyDueCardsOnly() }, CramKind.CRAM_KIND_DUE),
        ReviewCardsRandom({ TR.customStudyAllReviewCardsInRandomOrder() }, CramKind.CRAM_KIND_REVIEW),
        AllCardsRandom({ TR.customStudyAllCardsInRandomOrderDont() }, CramKind.CRAM_KIND_ALL),
    }

    /**
     * Default values for extending deck limits, and default tag selection
     *
     * Adapter which documents [anki.scheduler.CustomStudyDefaultsResponse]
     *
     * Upstream: [sched.proto: CustomStudyDefaultsResponse](https://github.com/search?q=repo%3Aankitects%2Fanki+CustomStudyDefaultsResponse+language%3A%22Protocol+Buffer%22&type=code&l=Protocol+Buffer)
     */
    @VisibleForTesting
    class CustomStudyDefaults(
        val extendNew: ExtendLimits,
        val extendReview: ExtendLimits,
        @Suppress("unused")
        val tags: List<CustomStudyDefaultsResponse.Tag>,
    ) {
        /** Available new cards: 1 (2 in subdecks) */
        fun labelForNewQueueAvailable(): String = TR.customStudyAvailableNewCards2(extendNew.labelForCountWithChildren())

        /** Available review cards: 1 (2 in subdecks) */
        fun labelForReviewQueueAvailable(): String = TR.customStudyAvailableReviewCards2(extendReview.labelForCountWithChildren())

        /**
         * Data displayed to a user wanting to temporarily extend the daily limits of
         * either new/review cards for a deck
         *
         * Displays `Available new cards: 1 (2 in subdecks)` when a limit is reached with remaining cards:
         * ```
         * Deck (1)
         *   Deck::Child1 (1)
         *   Deck::Child2 (1)
         * ```
         */
        class ExtendLimits(
            /** The initial value to display in the input dialog */
            val initialValue: Int,
            /**
             * The number of pending cards in only the parent deck
             *
             * **Example**
             * Returns **1** when a limit is reached with remaining cards:
             * ```
             * Deck (1)
             *   Deck::Child1 (1)
             *   Deck::Child2 (1)
             * ```
             */
            val available: Int,
            /**
             * The sum of cards in only the child decks
             *
             * **Example**
             * * Returns **2** when a limit is reached  with remaining cards:
             * ```
             * Deck (1)
             *   Deck::Child1 (1)
             *   Deck::Child2 (1)
             * ```
             */
            val availableInChildren: Int,
        ) {
            /**
             * **Temporarily Disabled** - logic may be incorrect
             *
             * "Extend" only has an effect if there are pending cards in the target deck
             *
             * The number of pending cards in child decks is only informative
             */
            val isUsable
                get() = true // TODO: Confirm `available > 0` is correct; user feedback states subdecks are taken into account

            /**
             * A string representing the count of cards which have exceeded a deck limit
             *
             * `123 (456 in subdecks)` or `123`
             *
             * For use in either
             * [net.ankiweb.rsdroid.Translations.customStudyAvailableReviewCards2] or
             * [net.ankiweb.rsdroid.Translations.customStudyAvailableNewCards2]
             *
             */
            fun labelForCountWithChildren(): String =
                if (availableInChildren == 0) {
                    available.toString()
                } else {
                    "$available ${TR.customStudyAvailableChildCount(availableInChildren)}"
                }
        }

        companion object {
            fun CustomStudyDefaultsResponse.toDomainModel(): CustomStudyDefaults =
                CustomStudyDefaults(
                    extendNew =
                        ExtendLimits(
                            initialValue = extendNew,
                            available = availableNew,
                            availableInChildren = availableNewInChildren,
                        ),
                    extendReview =
                        ExtendLimits(
                            initialValue = extendReview,
                            available = availableReview,
                            availableInChildren = availableReviewInChildren,
                        ),
                    tags = this.tagsList,
                )
        }
    }

    companion object {
        /**
         * @see CustomStudyDefaults
         *
         * Singleton; initialized when the main screen is loaded
         * This exists so we don't need to pass an unbounded object between fragments
         */
        private lateinit var deferredInitialization: Deferred<CustomStudyInitialization>

        /**
         * Completed defaults shared with subdialogs. Restore the main menu if the loading
         * dialog was cancelled or its process died.
         */
        private fun readyInitialization(): CustomStudyInitialization.Ready? {
            if (!::deferredInitialization.isInitialized || !deferredInitialization.isCompleted ||
                deferredInitialization.isCancelled
            ) {
                return null
            }
            return deferredInitialization.getCompleted() as? CustomStudyInitialization.Ready
        }

        /**
         * Creates an instance of the Custom Study Dialog: a user can select a custom study type
         */
        fun createInstance(deckId: DeckId): CustomStudyDialog =
            CustomStudyDialog().apply {
                arguments =
                    Bundle().apply {
                        putLong(CustomStudyViewModel.ARG_DECK_ID, deckId)
                    }
            }

        /**
         * Creates an instance of the Custom Study sub-dialog for a user to configure
         * a selected custom study type
         *
         * e.g. After selecting "Study Ahead", entering the number of days to study ahead by
         */
        fun createSubDialog(
            deckId: DeckId,
            contextMenuAttribute: ContextMenuOption,
        ): CustomStudyDialog =
            CustomStudyDialog().apply {
                arguments =
                    Bundle().apply {
                        putLong(CustomStudyViewModel.ARG_DECK_ID, deckId)
                        putInt(ARG_SUB_DIALOG_ID, contextMenuAttribute.ordinal)
                    }
            }

        const val FORGOT_MAX_DAYS = 30

        /**
         * (optional) Key for the ordinal of the [ContextMenuOption] to display.
         * @see CustomStudyDialog.selectedSubDialog
         */
        private const val ARG_SUB_DIALOG_ID = "subDialogId"
    }
}
