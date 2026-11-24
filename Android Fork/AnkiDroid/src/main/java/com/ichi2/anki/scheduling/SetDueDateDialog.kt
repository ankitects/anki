// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import android.app.Dialog
import android.content.DialogInterface
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
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.asyncCatching
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.browser.removeSafely
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.DialogSetDueDateBinding
import com.ichi2.anki.databinding.FragmentSetDueDateRangeBinding
import com.ichi2.anki.databinding.FragmentSetDueDateSingleBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.doOnImeHidden
import com.ichi2.anki.utils.ext.collectInLifecycle
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.withProgress
import com.ichi2.utils.AndroidUiUtils
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import com.ichi2.utils.titleWithHelpIcon
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.math.min

/**
 * Dialog for [Scheduler.setDueDate], containing two tabs: [Tab.SINGLE_DAY] and [Tab.DATE_RANGE]
 *
 * @see SetDueDateViewModel
 */
class SetDueDateDialog : AnalyticsDialogFragment() {
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

    val cardIds: List<Long>?
        get() = requireArguments().requireParcelable<IdsFile>(ARG_IDS_FILE).getIdsOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cardIds =
            try {
                this.cardIds
            } catch (e: IOException) {
                Timber.w(e, "Failed to read cardIds")
                null
            }
        if (cardIds == null) {
            showThemedToast(requireContext(), CommonString.something_wrong, false)
            dismiss()
            return
        }

        viewModel.init(cardIds)
        Timber.d("Set due date dialog: %d card(s)", cardIds.size)
        this.initialRotation = getScreenRotation()

        childFragmentManager.setFragmentResultListener(REQUEST_SUBMIT_DUE_DATE, this) { _, _ ->
            Timber.i(REQUEST_SUBMIT_DUE_DATE)
            requireActivity().launchCatchingTask {
                if (launchUpdateDueDate(showError = false).await() == null) {
                    Timber.w("unsuccessful updating dates; not dismissing dialog")
                    return@launchCatchingTask
                }
                dismiss()
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        // DialogFragment also calls this from onDestroyView, so without this check a rotation
        // would delete the file which the recreated dialog still needs
        if (activity?.isChangingConfigurations == true) {
            Timber.d("not removing IdsFile: dialog is being recreated")
            return
        }

        if (arguments?.containsKey(ARG_IDS_FILE) == true) {
            requireArguments()
                .requireParcelable<IdsFile>(ARG_IDS_FILE)
                .removeSafely("SetDueDateDialog")
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
            requireActivity().recreate()
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
                positiveButton(CommonString.dialog_ok) { launchUpdateDueDate() }
                negativeButton(CommonString.dialog_cancel)
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
                    viewModel.fsrsEnabled.collectInLifecycle {
                        cb.isVisible = viewModel.canSetUpdateIntervalToMatchDueDate
                        cb.isChecked = viewModel.updateIntervalToMatchDueDate
                    }
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
                                        CommonPlurals.set_due_date_current_interval,
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

    private fun launchUpdateDueDate(showError: Boolean = true) = requireActivity().updateDueDate(viewModel, showError)

    companion object {
        const val ARG_IDS_FILE = "ARGS_IDS_FILE"
        const val MAX_WIDTH_DP = 450f

        private const val REQUEST_SUBMIT_DUE_DATE = "SubmitDueDate"

        /** Keeps the existing dialog and its input when another request arrives. */
        @MainThread
        fun show(
            activity: FragmentActivity,
            cardIds: List<CardId>,
        ) {
            if (activity.supportFragmentManager.fragments.any { it is SetDueDateDialog }) {
                Timber.d("Ignoring 'set due date' request: dialog is already open")
                return
            }
            val dialog = newInstance(IdsFile.getDirectory(), cardIds)
            activity.showDialogFragment(dialog)
        }

        @VisibleForTesting
        @CheckResult
        fun newInstance(
            idsFileDirectory: File,
            cardIds: List<CardId>,
        ): SetDueDateDialog =
            SetDueDateDialog().apply {
                arguments =
                    Bundle().apply {
                        putParcelable(ARG_IDS_FILE, IdsFile(idsFileDirectory, cardIds, IdsFile.Purpose.SET_DUE_DATE))
                    }
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
                                CommonPlurals.set_due_date_label_suffix,
                                currentValue ?: 0,
                            )
                    }
                    suffixText = resources.getQuantityString(CommonPlurals.set_due_date_label_suffix, 0)
                    helperText =
                        getString(
                            CommonString.set_due_date_hintText,
                            // 0 days
                            resources.getQuantityString(CommonPlurals.set_due_date_label_suffix, 0),
                            // 1 day
                            resources.getQuantityString(CommonPlurals.set_due_date_label_suffix, 1),
                        )
                    setOnEditorActionListener { _, actionId, event ->
                        return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE ||
                            event?.keyCode == KeyEvent.KEYCODE_ENTER
                        ) {
                            parentFragmentManager.setFragmentResult(
                                REQUEST_SUBMIT_DUE_DATE,
                                Bundle(),
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
                    CommonPlurals.set_due_date_single_day_label,
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
                                CommonPlurals.set_due_date_label_suffix,
                                value ?: 0,
                            )
                    }
                    suffixText = resources.getQuantityString(CommonPlurals.set_due_date_label_suffix, 0)
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
                                CommonPlurals.set_due_date_label_suffix,
                                value ?: 0,
                            )
                    }
                    suffixText = resources.getQuantityString(CommonPlurals.set_due_date_label_suffix, 0)
                    viewModel.dateRange.end?.let { end -> setText(end.toString()) }
                    setOnEditorActionListener { _, actionId, event ->
                        return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE ||
                            event?.keyCode == KeyEvent.KEYCODE_ENTER
                        ) {
                            parentFragmentManager.setFragmentResult(
                                REQUEST_SUBMIT_DUE_DATE,
                                Bundle(),
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
                resources.getQuantityString(CommonPlurals.set_due_date_range_label, viewModel.cardCount)
        }

        override fun onResume() {
            super.onResume()
            this.requireView().requestLayout() // update the height of the ViewPager

            AndroidUiUtils.setFocusAndOpenKeyboard(binding.dateRangeStartEditText)
        }
    }
}

// this can outlive the lifetime of the fragment
private fun FragmentActivity.updateDueDate(
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
                showThemedToast(this@updateDueDate, CommonString.something_wrong, true)
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
