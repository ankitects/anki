/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
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

package com.ichi2.anki.filtered

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.databinding.FragmentFilteredDeckOptionsBinding
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.anki.utils.openUrl
import com.ichi2.utils.cancelable
import com.ichi2.utils.configureIconsDirection
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import java.util.Locale.getDefault

/**
 * Represents the screen where a filtered deck can be built or rebuilt after updating its properties.
 *
 * @see FilteredDeckOptionsViewModel
 * @see FilteredDeckOptionsState
 */
class FilteredDeckOptionsFragment : Fragment(R.layout.fragment_filtered_deck_options) {
    private val binding by viewBinding(FragmentFilteredDeckOptionsBinding::bind)
    private val viewModel by viewModels<FilteredDeckOptionsViewModel>()
    private val discardBackHandler =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                DiscardChangesDialog.showDialog(context = requireActivity()) {
                    requireActivity().finish()
                }
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.apply {
            title = "" // properly set in state updates
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_help -> {
                        requireActivity().openUrl(R.string.link_filtered_decks_help)
                        true
                    }

                    else -> true
                }
            }
        }
        // backend allows at most 5 digits
        binding.filterLimitInput.filters = arrayOf(InputFilter.LengthFilter(5))
        binding.secondFilterLimitInput.filters = arrayOf(InputFilter.LengthFilter(5))
        bindLabels()
        bindListeners()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            discardBackHandler,
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.hasUnsavedChanges.collect { status ->
                discardBackHandler.isEnabled = status
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is Initializing -> {
                            if (state.throwable != null) {
                                // we can't recover from an initialization error so just exit
                                AlertDialog.Builder(requireActivity()).show {
                                    setTitle(R.string.import_title_error)
                                    message(text = state.throwable.toString())
                                    cancelable(false)
                                    positiveButton(R.string.dialog_exit) { requireActivity().finish() }
                                }
                            } else {
                                binding.loadingIndicator.isVisible = true
                                binding.scrollView.isVisible = false
                            }
                        }

                        is DeckBuilt -> {
                            // TODO desktop doesn't do this but maybe also show a toast here?
                            // the filtered deck is built/updated so exit the screen
                            requireActivity().finish()
                        }

                        is FilteredDeckOptions -> {
                            setupMenuIfNeeded()
                            // the button is hidden initially until we load the data
                            binding.btnBuild.isVisible = true
                            bindState(state)
                            // show any errors we might have
                            if (state.throwable != null) {
                                AlertDialog.Builder(requireActivity()).show {
                                    title(R.string.import_title_error)
                                    message(text = state.throwable.message)
                                    positiveButton(R.string.dialog_ok)
                                    setOnDismissListener { viewModel.clearError() }
                                }
                                return@collect
                            }
                            if (state.browserQuery != null) {
                                val browserSearchIntent = Intent(context, CardBrowser::class.java)
                                browserSearchIntent.putExtra(CardBrowserViewModel.EXTRA_SEARCH_QUERY, state.browserQuery)
                                startActivity(browserSearchIntent)
                                viewModel.clearSearchInBrowser()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupMenuIfNeeded() {
        val helpMenuItem: MenuItem? = binding.toolbar.menu.findItem(R.id.action_help)
        if (helpMenuItem == null) {
            binding.toolbar.inflateMenu(R.menu.filtered_options)
            binding.toolbar.menu.configureIconsDirection(requireContext())
        }
    }

    private fun bindState(state: FilteredDeckOptions) {
        binding.loadingIndicator.isVisible = false
        binding.btnShowExcludedCards.isVisible = true
        binding.scrollView.isVisible = true
        // vs. desktop we don't use TR.actionsOptionsFor(name) because it doesn't fit (showing just
        // the deck name is also done for the normal deck options screen so we are consistent)
        binding.toolbar.title = state.title
        binding.deckNameInput.setTextIfChanged(state.name)
        binding.deckNameInputLayout.error =
            when (state.nameInputError) {
                FilteredNameInputError.Empty -> getString(R.string.empty_cram_label)
                FilteredNameInputError.AlreadyExists -> getString(R.string.error_name_exists)
                null -> null
            }
        binding.checkBoxAllowEmpty.setCheckedIfChanged(state.allowEmpty)
        binding.btnBuild.text = if (state.id == null) TR.decksBuild() else TR.actionsRebuild()
        binding.btnBuild.isEnabled = state.isBuildingAllowed
        binding.loadingIndicatorBuilding.isVisible = state.isBuildingBrowserSearch
        // setup filter#1
        val filter1State = state.filter1State
        binding.filterSearchInput.setTextIfChanged(filter1State.search)
        binding.filterLimitInput.setTextIfChanged(filter1State.limit)
        binding.filterLimitInputLayout.error =
            when (state.filter1State.error) {
                SearchInputError.Empty -> getString(R.string.empty_cram_label)
                SearchInputError.NotANumber -> TR.errorsInvalidInputEmpty()
                null -> null
            }
        binding.filterCards.setAdapterIfNeeded(state.cardOptions, filter1State.index)
        // rescheduling (done here because in filter 2 setup we might exit early)
        binding.checkBoxReschedule.setCheckedIfChanged(state.shouldReschedule)
        binding.rescheduleDelayAgainInput.setTextIfChanged(state.delayAgain)
        binding.rescheduleDelayAgainLayout.setupRescheduleDelay(state, RescheduleDelay.Again)
        binding.rescheduleDelayHardInput.setTextIfChanged(state.delayHard)
        binding.rescheduleDelayHardLayout.setupRescheduleDelay(state, RescheduleDelay.Hard)
        binding.rescheduleDelayGoodInput.setTextIfChanged(state.delayGood)
        binding.rescheduleDelayGoodLayout.setupRescheduleDelay(state, RescheduleDelay.Good)
        binding.rescheduleInfoLabel.isVisible = !state.shouldReschedule

        // setup filter#2
        if (binding.switchSecondFilter.isChecked != state.isSecondFilterEnabled) {
            binding.switchSecondFilter.isChecked = state.isSecondFilterEnabled
        }
        binding.secondFilterSearchContainer.isVisible = state.isSecondFilterEnabled
        binding.secondFilterLimitInputLayout.isVisible = state.isSecondFilterEnabled
        binding.secondFilterCardsLabel.isVisible = state.isSecondFilterEnabled
        binding.secondFilterCards.isVisible = state.isSecondFilterEnabled
        val filter2State = state.filter2State ?: return
        binding.secondFilterSearchInput.setTextIfChanged(filter2State.search)
        binding.secondFilterLimitInput.setTextIfChanged(filter2State.limit)
        binding.secondFilterLimitInputLayout.error =
            when (state.filter2State.error) {
                SearchInputError.Empty -> getString(R.string.empty_cram_label)
                SearchInputError.NotANumber -> TR.errorsInvalidInputEmpty()
                null -> null
            }
        binding.secondFilterCards.setAdapterIfNeeded(state.cardOptions, filter2State.index)
    }

    private fun TextInputLayout.setupRescheduleDelay(
        state: FilteredDeckOptions,
        target: RescheduleDelay,
    ) {
        val delayAmount =
            when (target) {
                RescheduleDelay.Again -> state.delayAgain
                RescheduleDelay.Hard -> state.delayHard
                RescheduleDelay.Good -> state.delayGood
            }
        suffixText = TR.schedulingSeconds()
        error = if (delayAmount.toIntOrNull() == null) TR.errorsInvalidInputEmpty() else null
        isVisible = !state.shouldReschedule
    }

    private fun bindListeners() {
        binding.deckNameInput.onTextChanged(viewModel::onDeckNameChange)
        // filter#1
        binding.filterSearchInput.onTextChanged { text ->
            viewModel.onSearchChange(FilterIndex.First, text)
        }
        binding.filterSearchInputLayout.setEndIconOnClickListener {
            viewModel.onSearchInBrowser(FilterIndex.First)
        }
        binding.filterLimitInput.onTextChanged { text ->
            viewModel.onLimitChange(FilterIndex.First, text)
        }
        binding.filterCards.bindListener(FilterIndex.First)

        // filter#2
        binding.switchSecondFilter.onCheckedChanged(viewModel::onSecondFilterStatusChange)
        binding.secondFilterSearchInput.onTextChanged { text ->
            viewModel.onSearchChange(FilterIndex.Second, text)
        }
        binding.secondFilterSearchInputLayout.setEndIconOnClickListener {
            viewModel.onSearchInBrowser(FilterIndex.Second)
        }
        binding.secondFilterLimitInput.onTextChanged { text ->
            viewModel.onLimitChange(FilterIndex.Second, text)
        }
        binding.secondFilterCards.bindListener(FilterIndex.Second)

        // reschedule
        binding.checkBoxReschedule.onCheckedChanged(viewModel::onRescheduleChange)
        binding.rescheduleDelayAgainInput.onTextChanged { text ->
            viewModel.onRescheduleDelayChange(RescheduleDelay.Again, text)
        }
        binding.rescheduleDelayHardInput.onTextChanged { text ->
            viewModel.onRescheduleDelayChange(RescheduleDelay.Hard, text)
        }
        binding.rescheduleDelayGoodInput.onTextChanged { text ->
            viewModel.onRescheduleDelayChange(RescheduleDelay.Good, text)
        }
        // other buttons
        binding.checkBoxAllowEmpty.onCheckedChanged(viewModel::onAllowEmptyChange)
        binding.btnBuild.setOnClickListener {
            it.isEnabled = false // try to avoid/limit multiple clicks
            viewModel.build()
        }
        binding.btnShowExcludedCards.setOnClickListener { viewModel.onShowExcludedCards() }
    }

    /** Registers a [doOnTextChanged] to listen for non-null text changes */
    private fun TextInputEditText.onTextChanged(action: (String) -> Unit) {
        doOnTextChanged { text, _, _, _ ->
            if (text == null) return@doOnTextChanged
            action(text.toString())
        }
    }

    /** Registers a [MaterialSwitch.OnCheckedChangeListener] to listen for checked status changes */
    private fun MaterialSwitch.onCheckedChanged(action: (Boolean) -> Unit) {
        setOnCheckedChangeListener { _, isChecked -> action(isChecked) }
    }

    private fun bindLabels() {
        // name
        binding.deckNameInputLayout.hint = TR.deckConfigNamePrompt()
        // misc labels
        binding.filterLabel.text = TR.actionsFilter()
        binding.optionsLabel.text = TR.actionsOptions()
        // first filter
        binding.filterSearchInputLayout.hint = TR.actionsSearch()
        binding.filterLimitInputLayout.hint = TR.decksLimitTo()
        binding.filterCardsLabel.text = TR.decksCardsSelectedBy().capitalize()
        // second filter
        binding.switchSecondFilter.text = TR.decksEnableSecondFilter()
        binding.secondFilterSearchInputLayout.hint = TR.actionsSearch()
        binding.secondFilterLimitInputLayout.hint = TR.decksLimitTo()
        binding.secondFilterCardsLabel.text = TR.decksCardsSelectedBy().capitalize()

        // buttons
        binding.btnShowExcludedCards.text = TR.decksUnmovableCards()
        binding.checkBoxReschedule.text = TR.decksRescheduleCardsBasedOnMyAnswers()
        binding.checkBoxAllowEmpty.text = TR.decksCreateEvenIfEmpty()
        // reschedule options
        binding.rescheduleInfoLabel.text = TR.decksZeroMinutesHint()
        binding.rescheduleDelayAgainLayout.hint = getString(R.string.filtered_option_delay_again)
        binding.rescheduleDelayHardLayout.hint = getString(R.string.filtered_option_delay_hard)
        binding.rescheduleDelayGoodLayout.hint = getString(R.string.filtered_option_delay_good)
    }

    /** Sets text of [TextInputEditText] to [newText] only if it's different from its current text */
    private fun TextInputEditText.setTextIfChanged(newText: String) {
        if (this.text?.toString() != newText) this.setText(newText)
    }

    /** Sets isChecked property of [CheckBox] to [newChecked] only if it's different from its current isChecked value */
    private fun MaterialSwitch.setCheckedIfChanged(newChecked: Boolean) {
        if (this.isChecked != newChecked) this.isChecked = newChecked
    }

    private fun Spinner.bindListener(index: FilterIndex) {
        onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    adapterView: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    viewModel.onCardsOptionsChange(index, position)
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?) {
                    // do nothing
                }
            }
    }

    /** Sets the adapter and selection for cards [Spinner] only if options are available */
    private fun Spinner.setAdapterIfNeeded(
        cardOptions: List<String>,
        selectedPosition: Int,
    ) {
        if (cardOptions.isNotEmpty()) {
            setAdapter(
                ArrayAdapter(
                    requireActivity(),
                    android.R.layout.simple_dropdown_item_1line,
                    cardOptions,
                ),
            )
            setSelection(selectedPosition)
        }
    }

    /**
     * Needed because backend returns the string TR.decksCardsSelectedBy starting with a lowercase.
     * This was the recommended replacement implementation for the now deprecated Kotlin
     * capitalize() method.
     */
    private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }

    companion object {
        const val ARG_DECK_ID = "arg_deck_id"
        const val ARG_SEARCH = "arg_search"
        const val ARG_SEARCH_2 = "arg_search_2"

        /**
         * Starts a [ConfigAwareSingleFragmentActivity] containing this fragment. If [search] or
         * [search2] are provided, they will be used as the default search text.
         * @param did the [DeckId] of a filtered deck. If it's non-zero, load and modify its settings
         * otherwise build a new deck and derive settings from the current deck.
         */
        fun getIntent(
            context: Context,
            did: DeckId = 0,
            search: String? = null,
            search2: String? = null,
        ): Intent =
            ConfigAwareSingleFragmentActivity.getIntent(
                context = context,
                fragmentClass = FilteredDeckOptionsFragment::class,
                arguments =
                    Bundle().apply {
                        putLong(ARG_DECK_ID, did)
                        putString(ARG_SEARCH, search)
                        putString(ARG_SEARCH_2, search2)
                    },
            )
    }
}
