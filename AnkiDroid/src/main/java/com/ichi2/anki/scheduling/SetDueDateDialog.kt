/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.scheduling

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.annotation.CheckResult
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.asyncCatching
import com.ichi2.anki.databinding.DialogSetDueDateBinding
import com.ichi2.anki.databinding.FragmentSetDueDateRangeBinding
import com.ichi2.anki.databinding.FragmentSetDueDateSingleBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.anki.servicelayer.getFSRSStatus
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.doOnImeHidden
import com.ichi2.anki.utils.ext.requireBoolean
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.utils.AndroidUiUtils
import com.ichi2.utils.create
import com.ichi2.utils.dp
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import com.ichi2.utils.titleWithHelpIcon
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.min

/**
 * Dialog for [Scheduler.setDueDate], containing two tabs: [Tab.SINGLE_DAY] and [Tab.DATE_RANGE]
 *
 * @see SetDueDateViewModel
 */
class SetDueDateDialog : DialogFragment() {
    // We explicitly do not use calendar controls in this class
    // User feedback:
    // (1) Don't have to think about what today is in order to use it,
    // (2) looking at a calendar makes the future date too concrete
    //  (... easier to consider as a nebulous range than a deadline)
    // (3) If the interval is changed, it will be set to a number of days, not a date.
    // (4) Inconsistent with Anki Desktop
    // TODO: This does not handle configuration changes on some EditTexts [screen rotate/night mode]

    val viewModel: SetDueDateViewModel by activityViewModels<SetDueDateViewModel>()

    private lateinit var binding: DialogSetDueDateBinding

    // used to determine if a rotation has taken place
    private var initialRotation: Int = 0

    val cardIds: LongArray
        get() = requireNotNull(requireArguments().getLongArray(ARG_CARD_IDS)) { ARG_CARD_IDS }

    val fsrsEnabled: Boolean
        get() = requireArguments().requireBoolean(ARG_FSRS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.init(cardIds, fsrsEnabled)
        Timber.d("Set due date dialog: %d card(s)", cardIds.size)
        this.initialRotation = getScreenRotation()

        childFragmentManager.setFragmentResultListener(RESULT_SUBMIT_DUE_DATE, this) { _, _ ->
            Timber.i(RESULT_SUBMIT_DUE_DATE)
            requireActivity().launchCatchingTask {
                if (launchUpdateDueDate(showError = false).await() == null) {
                    Timber.w("unsuccessful updating dates; not dismissing dialog")
                    return@launchCatchingTask
                }
                dismiss()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // HACK: After significant effort, I was unable to properly handle the interaction
        // between the ViewPager and `android:configChanges="orientation"`
        // (the window size & position was incorrectly calculated)

        // There was a minor bug in Reviewer (timer is reset), which meant that
        // generally we could not remove the configChanges
        // For now, only recreate the activity if this dialog is open
        if (getScreenRotation() != initialRotation) {
            Timber.d("recreating activity: orientation changed with 'Set due date' open")
            requireAnkiActivity().recreate()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogSetDueDateBinding.inflate(layoutInflater)
        return MaterialAlertDialogBuilder(requireContext())
            .create {
                titleWithHelpIcon(
                    text = TR.sentenceCase.setDueDate,
                ) {
                    openUrl(R.string.link_set_due_date_help)
                }
                title(text = TR.sentenceCase.setDueDate)
                positiveButton(R.string.dialog_ok) { launchUpdateDueDate() }
                negativeButton(R.string.dialog_cancel)
                setView(binding.root)
            }.apply {
                show()

                lifecycleScope.launch {
                    viewModel.isValidFlow.collect { isValid -> positiveButton.isEnabled = isValid }
                }
                // setup viewpager + tabs
                binding.setDueDatePager.adapter = DueDateStateAdapter(this@SetDueDateDialog)
                TabLayoutMediator(
                    binding.tabLayout,
                    binding.setDueDatePager,
                ) { tab: TabLayout.Tab, position: Int ->
                    SetDueDateViewModel.Tab.entries
                        .first { it.position == position }
                        .let { selectedTab ->
                            tab.setIcon(selectedTab.icon)
                            tab.setText(selectedTab.text)
                        }
                }.attach()
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))

                binding.setDueDatePager.registerOnPageChangeCallback(
                    object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            SetDueDateViewModel.Tab.entries
                                .first { it.position == position }
                                .let { selectedTab ->
                                    viewModel.currentTab = selectedTab
                                }
                            super.onPageSelected(position)
                        }
                    },
                )

                // setup 'set interval to same value' checkbox
                binding.changeInterval.also { cb ->
                    // `.also` is used as .isVisible is an extension, so Kotlin prefers
                    // incorrectly setting Fragment.isVisible
                    cb.isVisible = viewModel.canSetUpdateIntervalToMatchDueDate
                    cb.isChecked = viewModel.updateIntervalToMatchDueDate
                    cb.setOnCheckedChangeListener { _, isChecked ->
                        viewModel.updateIntervalToMatchDueDate = isChecked
                    }
                }

                lifecycleScope.launch {
                    viewModel.currentInterval.collect { currentInterval ->
                        binding.currentIntervalText.also { tv ->
                            // Current interval is set to null when multiple cards are selected
                            if (currentInterval != null) {
                                tv.isVisible = true
                                tv.text =
                                    resources.getQuantityString(
                                        R.plurals.set_due_date_current_interval,
                                        currentInterval,
                                        currentInterval,
                                    )
                            } else {
                                tv.isVisible = false
                            }
                        }
                    }
                }
            }
    }

    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)
        // this is required for the keyboard to appear: https://stackoverflow.com/a/10133603/
        dialog.window?.clearFlags(FLAG_NOT_FOCUSABLE or FLAG_ALT_FOCUSABLE_IM)

        // The dialog is too wide on tablets
        // Select either 450dp (tablets)
        // or 100% of the screen width (smaller phones)
        val intendedWidth =
            min(MAX_WIDTH_DP.dp.toPx(this.requireContext()), resources.displayMetrics.widthPixels)
        Timber.d("updating width to %d", intendedWidth)
        this.dialog?.window?.setLayout(
            intendedWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun getScreenRotation() = ContextCompat.getDisplayOrDefault(requireContext()).rotation

    private fun launchUpdateDueDate(showError: Boolean = true) = requireAnkiActivity().updateDueDate(viewModel, showError)

    companion object {
        const val ARG_CARD_IDS = "ARGS_CARD_IDS"
        const val ARG_FSRS = "ARGS_FSRS"
        const val MAX_WIDTH_DP = 450f

        private const val RESULT_SUBMIT_DUE_DATE = "SubmitDueDate"

        @CheckResult
        suspend fun newInstance(cardIds: List<CardId>) =
            SetDueDateDialog().apply {
                arguments =
                    bundleOf(
                        ARG_CARD_IDS to cardIds.toLongArray(),
                        ARG_FSRS to (
                            getFSRSStatus()
                                ?: false.also { Timber.w("FSRS Status error") }
                        ),
                    )
                Timber.i("Showing 'set due date' dialog for %d cards", cardIds.size)
            }
    }

    class DueDateStateAdapter(
        fragment: Fragment,
    ) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment =
            when (position) {
                0 -> SelectSingleDateFragment()
                1 -> SelectDateRangeFragment()
                else -> throw IllegalStateException("invalid position: $position")
            }

        override fun getItemCount() = 2
    }

    class SelectSingleDateFragment : Fragment(R.layout.fragment_set_due_date_single) {
        private val viewModel: SetDueDateViewModel by activityViewModels<SetDueDateViewModel>()

        private val binding by viewBinding(FragmentSetDueDateSingleBinding::bind)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)
            binding.setDueDateSingleDayInputLayout.apply {
                editText!!.apply {
                    filters = arrayOf(InputFilter.LengthFilter(5))

                    viewModel.nextSingleDayDueDate?.let { value -> setText(value.toString()) }
                    doOnTextChanged { text, _, _, _ ->
                        val currentValue = text?.toString()?.toIntOrNull()
                        viewModel.nextSingleDayDueDate = currentValue
                        suffixText =
                            resources.getQuantityString(
                                R.plurals.set_due_date_label_suffix,
                                currentValue ?: 0,
                            )
                    }
                    suffixText = resources.getQuantityString(R.plurals.set_due_date_label_suffix, 0)
                    helperText =
                        getString(
                            R.string.set_due_date_hintText,
                            // 0 days
                            resources.getQuantityString(R.plurals.set_due_date_label_suffix, 0),
                            // 1 day
                            resources.getQuantityString(R.plurals.set_due_date_label_suffix, 1),
                        )
                    setOnEditorActionListener { _, actionId, event ->
                        return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE ||
                            event?.keyCode == KeyEvent.KEYCODE_ENTER
                        ) {
                            parentFragmentManager.setFragmentResult(
                                RESULT_SUBMIT_DUE_DATE,
                                bundleOf(),
                            )
                            true
                        } else {
                            false
                        }
                    }
                    selectAllWhenFocused()
                }
            }
            binding.dateSingleLabel.text =
                resources.getQuantityString(
                    R.plurals.set_due_date_single_day_label,
                    viewModel.cardCount,
                )
        }

        override fun onResume() {
            super.onResume()
            this.requireView().requestLayout() // update the height of the ViewPager
            AndroidUiUtils.setFocusAndOpenKeyboard(binding.setDueDateSingleDayEditText)
        }
    }

    /**
     * Allows a user to select a start and end date
     */
    class SelectDateRangeFragment : Fragment(R.layout.fragment_set_due_date_range) {
        private val viewModel: SetDueDateViewModel by activityViewModels<SetDueDateViewModel>()

        private val binding by viewBinding(FragmentSetDueDateRangeBinding::bind)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)
            binding.dateRangeStartLayout.apply {
                editText!!.apply {
                    filters = arrayOf(InputFilter.LengthFilter(5))

                    viewModel.dateRange.start?.let { start -> setText(start.toString()) }
                    doOnTextChanged { text, _, _, _ ->
                        val value = text.toString().toIntOrNull()
                        viewModel.setNextDateRangeStart(value)
                        suffixText =
                            resources.getQuantityString(
                                R.plurals.set_due_date_label_suffix,
                                value ?: 0,
                            )
                    }
                    suffixText = resources.getQuantityString(R.plurals.set_due_date_label_suffix, 0)
                    selectAllWhenFocused()
                }
            }
            binding.dateRangeEndLayout.apply {
                editText!!.apply {
                    filters = arrayOf(InputFilter.LengthFilter(5))
                    doOnTextChanged { text, _, _, _ ->
                        val value = text.toString().toIntOrNull()
                        viewModel.setNextDateRangeEnd(value)
                        suffixText =
                            resources.getQuantityString(
                                R.plurals.set_due_date_label_suffix,
                                value ?: 0,
                            )
                    }
                    suffixText = resources.getQuantityString(R.plurals.set_due_date_label_suffix, 0)
                    viewModel.dateRange.end?.let { end -> setText(end.toString()) }
                    setOnEditorActionListener { _, actionId, event ->
                        return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE ||
                            event?.keyCode == KeyEvent.KEYCODE_ENTER
                        ) {
                            parentFragmentManager.setFragmentResult(
                                RESULT_SUBMIT_DUE_DATE,
                                bundleOf(),
                            )
                            true
                        } else {
                            false
                        }
                    }
                    selectAllWhenFocused()
                }
            }
            binding.dateRangeLabel.text =
                resources.getQuantityString(R.plurals.set_due_date_range_label, viewModel.cardCount)
        }

        override fun onResume() {
            super.onResume()
            this.requireView().requestLayout() // update the height of the ViewPager

            AndroidUiUtils.setFocusAndOpenKeyboard(binding.dateRangeStartEditText)
        }
    }
}

// this can outlive the lifetime of the fragment
private fun AnkiActivity.updateDueDate(
    viewModel: SetDueDateViewModel,
    showError: Boolean,
): Deferred<Int?> =
    this.asyncCatching {
        // NICE_TO_HAVE: Display a snackbar if the activity is recreated while this executes
        val cardsUpdated =
            withProgress {
                // this is async as it should be run on the viewModel
                viewModel.updateDueDateAsync().await()
            }

        if (cardsUpdated == null) {
            Timber.w("unable to update due date")
            if (showError) {
                showThemedToast(this@updateDueDate, R.string.something_wrong, true)
            }
            return@asyncCatching null
        }
        Timber.d("updated %d cards", cardsUpdated)
        // Ensure the snackbar doesn't appear in the middle of the screen
        doOnImeHidden {
            showSnackbar(TR.schedulingSetDueDateDone(cardsUpdated), Snackbar.LENGTH_SHORT)
        }
        return@asyncCatching cardsUpdated
    }

private fun EditText.selectAllWhenFocused() {
    setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            selectAll()
        }
    }
}
