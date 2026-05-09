/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.databinding.DialogChangeNoteTypeBinding
import com.ichi2.anki.databinding.DialogFieldsBinding
import com.ichi2.anki.databinding.DialogTemplatesBinding
import com.ichi2.anki.databinding.ViewTabLayoutIconOnEndBinding
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.SelectTemplateFragment.Layout.Standard
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.SelectTemplateFragment.Layout.WithWarning
import com.ichi2.anki.dialogs.ConversionType.CLOZE_TO_CLOZE
import com.ichi2.anki.dialogs.ConversionType.CLOZE_TO_REGULAR
import com.ichi2.anki.dialogs.ConversionType.REGULAR_TO_CLOZE
import com.ichi2.anki.dialogs.ConversionType.REGULAR_TO_REGULAR
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.showError
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.sync.launchCatchingRequiringOneWaySync
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.InitStatus
import com.ichi2.anki.utils.ext.launchCollectionInLifecycleScope
import com.ichi2.anki.withProgress
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.boldList
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Supports bulk remapping of notes a different note type, remapping fields and templates
 *
 * A full sync is required for this operation
 *
 * input:
 * - a distinct list of notes, which all have the same note type
 *
 * state:
 * - output note type
 * - a map of input field to output field
 * - a map of input template to output template
 *     - only if both input and output note types are non-cloze
 *
 * For maps: a user selects each field/template of the **input note type** which maps to the output
 *
 * @see ChangeNoteTypeViewModel
 */
class ChangeNoteTypeDialog : AnalyticsDialogFragment(R.layout.dialog_change_note_type) {
    private val viewModel: ChangeNoteTypeViewModel by viewModels { defaultViewModelProviderFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.ThemeOverlay_AnkiDroid_AlertDialog_FullScreen)
        setupFlows()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val binding = DialogChangeNoteTypeBinding.bind(view)

        binding.toolbar.title = TR.sentenceCase.changeNoteType
        binding.toolbar.setNavigationOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener {
            requireAnkiActivity().changeNoteType(viewModel)
            // dismiss() is handled via closeDialogFlow
        }

        launchCatchingTask {
            viewModel.flowOfInitStatus.collect {
                Timber.i("dialog init: %s", it)
                when (it) {
                    InitStatus.Pending, InitStatus.InProgress -> {
                        binding.changeNoteTypeLayout.isVisible = false
                        binding.changeNoteTypeLoadingLayout.isVisible = true
                        binding.btnSave.isVisible = false
                    }
                    InitStatus.Completed -> {
                        binding.changeNoteTypeLayout.isVisible = true
                        binding.changeNoteTypeLoadingLayout.isVisible = false
                        binding.btnSave.isVisible = true
                        setupChangeNoteTypeDialog(binding = binding)
                    }
                    is InitStatus.Failed -> {
                        requireContext().showError(it.exception.toString(), it.exception.toCrashReportData(requireContext()))
                        dismiss()
                    }
                }
            }
        }
    }

    private fun setupFlows() {
        launchCatchingTask {
            viewModel.closeDialogFlow.filterNotNull().collect {
                Timber.i("Dismissing dialog")
                parentFragmentManager.setFragmentResult(REQUEST_KEY_NOTE_TYPE_CHANGED, bundleOf())
                dismiss()
            }
        }
    }

    private fun setupChangeNoteTypeDialog(binding: DialogChangeNoteTypeBinding) {
        Timber.d("setting up dialog")
        setupNoteTypeSpinner(binding)
        setupViewPagerAndTabs(binding)
        bindSaveButtonState(binding)
    }

    private fun bindSaveButtonState(binding: DialogChangeNoteTypeBinding) {
        // disabled by default until hasChangesFlow emits true
        binding.btnSave.isEnabled = false
        viewModel.hasChangesFlow.launchCollectionInLifecycleScope {
            binding.btnSave.isEnabled = it
        }
    }

    private fun setupNoteTypeSpinner(binding: DialogChangeNoteTypeBinding) {
        binding.destNoteTypeSpinner.apply {
            adapter = createNoteTypeAdapter()

            val position = viewModel.availableNoteTypes.indexOfFirst { it.id == viewModel.outputNoteType.id }
            setSelection(position, false)

            onItemSelectedListener =
                BasicItemSelectedListener { position, id: NoteTypeId ->
                    viewModel.setOutputNoteTypeId(id)
                }
        }
    }

    private fun createNoteTypeAdapter(): ArrayAdapter<DisplayNoteType> {
        val noteTypes = viewModel.availableNoteTypes.map { DisplayNoteType(it.name, it.isCloze) }

        return object : ArrayAdapter<DisplayNoteType>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            noteTypes,
        ) {
            var clozeColor = MaterialColors.getColor(context, R.attr.clozeColor, Color.BLUE)

            private val defaultViewTextColor: Int by lazy {
                (super.getView(0, null, Spinner(context)) as CheckedTextView).currentTextColor
            }

            private val defaultDropDownViewTextColor: Int by lazy {
                (super.getDropDownView(0, null, Spinner(context)) as CheckedTextView).currentTextColor
            }

            override fun getItemId(position: Int): Long = viewModel.availableNoteTypes[position].id

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ) = (super.getView(position, convertView, parent) as CheckedTextView).apply {
                val noteType = getItem(position)!!
                text = noteType.name
                setTextColor(if (noteType.isCloze) clozeColor else defaultViewTextColor)
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ) = (super.getDropDownView(position, convertView, parent) as CheckedTextView).apply {
                val noteType = getItem(position)!!
                text = noteType.name
                setTextColor(if (noteType.isCloze) clozeColor else defaultDropDownViewTextColor)
            }

            override fun hasStableIds() = true
        }.apply {
            // The resource passed to the constructor is normally used for both the spinner view
            // and the dropdown list. This keeps the former and overrides the latter.
            setDropDownViewResource(R.layout.item_spinner_dropdown_with_radio)
        }
    }

    private fun setupViewPagerAndTabs(binding: DialogChangeNoteTypeBinding) {
        val viewPager = binding.changeNoteTypePager
        viewPager.adapter = ChangeNoteTypeStateAdapter(this@ChangeNoteTypeDialog)
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    viewModel.selectTabByPosition(position)
                    super.onPageSelected(position)
                }
            },
        )

        val tabLayout = binding.changeNoteTypeTabLayout
        createTabMediator(tabLayout, viewPager).attach()
        // Explicitly set initial tab in ViewModel to match UI
        viewModel.selectTabByPosition(0)
        tabLayout.selectTab(tabLayout.getTabAt(0))
    }

    private fun createTabMediator(
        tabLayout: TabLayout,
        viewPager: ViewPager2,
    ): TabLayoutMediator =
        TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
            val binding = ViewTabLayoutIconOnEndBinding.inflate(LayoutInflater.from(tabLayout.context), tabLayout, false)
            when (position) {
                0 -> {
                    binding.tabIcon.setImageResource(R.drawable.ic_mode_edit_white)
                    binding.tabText.text = TR.changeNotetypeFields()
                    tab.text = TR.changeNotetypeFields()
                }
                1 -> {
                    binding.tabIcon.setImageResource(R.drawable.ic_card_question)
                    binding.tabText.text = TR.changeNotetypeTemplates()
                    tab.text = TR.changeNotetypeTemplates()
                }
                else -> throw IllegalStateException("invalid position: $position")
            }
            tab.customView = binding.root
        }

    /** Display model for note types in the spinner */
    private data class DisplayNoteType(
        val name: String,
        val isCloze: Boolean,
    )

    companion object {
        const val ARG_NOTE_IDS = "ARG_NOTE_IDS"

        /** Result key emitted via `setFragmentResult` when note type change completes successfully */
        const val REQUEST_KEY_NOTE_TYPE_CHANGED = "ChangeNoteTypeDialog::noteTypeChanged"

        @CheckResult
        fun newInstance(noteIds: List<NoteId>) =
            ChangeNoteTypeDialog().apply {
                val ids = noteIds.distinct()
                arguments =
                    bundleOf(
                        ARG_NOTE_IDS to ids.toLongArray(),
                    )
                Timber.i("Showing 'change note type' dialog for %d notes", ids.size)
            }
    }

    class ChangeNoteTypeStateAdapter(
        fragment: Fragment,
    ) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment =
            when (position) {
                0 -> SelectFieldsFragment()
                1 -> SelectTemplateFragment()
                else -> throw IllegalStateException("invalid position: $position")
            }

        override fun getItemCount() = 2
    }

    class SelectFieldsFragment : Fragment(R.layout.dialog_fields) {
        private val viewModel: ChangeNoteTypeViewModel by viewModels({ requireParentFragment() })
        private lateinit var binding: DialogFieldsBinding

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)
            binding = DialogFieldsBinding.bind(view)

            binding.currentFieldLabel.text =
                TR.changeNotetypeCurrent()
            binding.newFieldLabel.text = TR.changeNotetypeNew()

            lifecycleScope.launch {
                viewModel.flowOfInitStatus.collect {
                    if (it is InitStatus.Completed) {
                        setupFlows()
                        createFieldSpinner()
                    }
                }
            }
        }

        fun setupFlows() {
            lifecycleScope.launch {
                Timber.d("setupFlows: collecting outputNoteTypeFlow")
                viewModel.outputNoteTypeFlow.collect {
                    createFieldSpinner()
                }
            }

            lifecycleScope.launch {
                Timber.d("setupFlows: collecting discardedFieldsFlow")
                viewModel.discardedFieldsFlow.collect { fields ->
                    showDiscardedFieldsMessage(fields)
                }
            }
        }

        private fun showDiscardedFieldsMessage(discardedFields: List<String>) {
            binding.fieldRemovalText.isVisible = discardedFields.isNotEmpty()
            if (discardedFields.isEmpty()) {
                return
            }

            binding.fieldRemovalText.text =
                SpannableStringBuilder()
                    .append(TR.changeNotetypeWillDiscardContent() + " ")
                    .boldList(discardedFields, ", ")
        }

        /**
         * Builds the field mapping UI with paired spinners and labels.
         *
         * Creates one row per output field, each containing:
         * - A spinner to select which input field maps to it (or Nothing to discard field content)
         * - A label showing the output field name
         *
         * Automatically selects default mappings from the ViewModel and updates them on user selection.
         */
        private fun createFieldSpinner() {
            binding.fieldsContainer.removeAllViews()

            val inputFieldNames = viewModel.inputNoteType.fieldsNames
            val outputFieldNames = viewModel.outputNoteType.fieldsNames

            fun buildFieldLayout() =
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            MATCH_PARENT,
                            WRAP_CONTENT,
                        )
                }

            fun buildFieldSpinner(spinnerIndex: Int) =
                Spinner(requireContext())
                    .apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    }.apply {
                        val fieldSpinnerOptions = inputFieldNames + TR.changeNotetypeNothing()
                        Timber.d("createTemplateSpinner: %d items + (nothing)", fieldSpinnerOptions.size - 1)
                        this.adapter =
                            ArrayAdapter(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                fieldSpinnerOptions,
                            ).apply {
                                // The resource passed to the constructor is normally used for both the spinner view
                                // and the dropdown list. This keeps the former and overrides the latter.
                                this.setDropDownViewResource(R.layout.item_spinner_dropdown_with_radio)
                            }

                        val selectionIndex = viewModel.fieldChangeMap[spinnerIndex] ?: fieldSpinnerOptions.lastIndex
                        setSelection(selectionIndex, false)

                        // Add an item selection listener to update the field mapping when user changes selection
                        val oldIndex = spinnerIndex
                        onItemSelectedListener =
                            BasicItemSelectedListener { position, id ->
                                // The last index is '(Nothing)'
                                val newMapping =
                                    if (position == fieldSpinnerOptions.lastIndex) {
                                        SelectedIndex.NOTHING
                                    } else {
                                        SelectedIndex.from(position)
                                    }
                                viewModel.updateFieldMapping(oldIndex, newMapping)
                            }
                    }

            fun buildFieldText(initialText: String) =
                MaterialTextView(requireContext()).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    text = initialText
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
            for (i in outputFieldNames.indices) {
                val fieldLayout =
                    buildFieldLayout().apply {
                        addView(buildFieldSpinner(i))
                        addView(buildFieldText(outputFieldNames[i]))
                    }
                binding.fieldsContainer.addView(fieldLayout)
            }
        }
    }

    class SelectTemplateFragment : Fragment(R.layout.dialog_templates) {
        private val viewModel: ChangeNoteTypeViewModel by viewModels({ requireParentFragment() })

        private lateinit var binding: DialogTemplatesBinding

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)
            binding = DialogTemplatesBinding.bind(view)

            binding.currentTemplateLabel.text = TR.changeNotetypeCurrent()
            binding.newTemplateLabel.text = TR.changeNotetypeNew()

            lifecycleScope.launch {
                viewModel.flowOfInitStatus.collect {
                    if (it is InitStatus.Completed) {
                        setupFlows()
                        createTemplateSpinner()
                    }
                }
            }
        }

        fun setupFlows() {
            // show/hide cloze info layout based on note type
            lifecycleScope.launch {
                viewModel.outputNoteTypeFlow.collect {
                    createTemplateSpinner()
                }
            }

            // Updates to (Nothing) if the map changes
            lifecycleScope.launch {
                viewModel.templateChangeMapFlow.collect {
                    createTemplateSpinner()
                }
            }

            lifecycleScope.launch {
                viewModel.conversionTypeFlow.collect { type ->
                    when (val layout = Layout.fromConversionType(type)) {
                        is Standard -> {
                            binding.clozeInfoLayout.isVisible = false
                        }
                        is WithWarning -> {
                            binding.clozeInfoLayout.isVisible = true
                            binding.clozeInfoText.text = getString(layout.warningRes)
                        }
                    }
                }
            }

            lifecycleScope.launch {
                viewModel.canChangeTemplatesFlow.collect { canChangeTemplates ->
                    binding.templatesContainer.isVisible = canChangeTemplates
                    binding.templatesHeaderLayout.isVisible = canChangeTemplates
                    if (!canChangeTemplates) {
                        binding.templateRemovalText.isVisible = false
                    }
                }
            }

            lifecycleScope.launch {
                viewModel.discardedTemplatesFlow.collect { discarded ->
                    if (viewModel.canChangeTemplatesFlow.value) {
                        showDiscardedTemplatesMessage(discarded)
                    }
                }
            }
        }

        /**
         * Controls the layout and warning display for the template selection tab.
         *
         * - [Standard]: No warning shown, only template mapping spinners visible
         * - [WithWarning]: Shows a warning message above the template mapping spinners
         *
         * The layout type is determined by the [ConversionType] between input and output note types.
         */
        sealed class Layout {
            data object Standard : Layout()

            data class WithWarning(
                @StringRes val warningRes: Int,
            ) : Layout()

            companion object {
                fun fromConversionType(conversionType: ConversionType): Layout =
                    when (conversionType) {
                        REGULAR_TO_REGULAR -> Standard
                        CLOZE_TO_CLOZE, REGULAR_TO_CLOZE ->
                            WithWarning(
                                warningRes = R.string.card_numbers_unchanged,
                            )
                        // Improvement: we could detect this using the max ord of provided notes
                        CLOZE_TO_REGULAR ->
                            WithWarning(
                                warningRes = R.string.extra_cloze_deletions_removed,
                            )
                    }
            }
        }

        /**
         * Show message listing discarded templates when not all templates are mapped to new ones
         */
        private fun showDiscardedTemplatesMessage(discardedTemplateNames: List<String>) {
            val templateRemovalLabel = binding.templateRemovalText
            templateRemovalLabel.isVisible = discardedTemplateNames.isNotEmpty()
            if (discardedTemplateNames.isEmpty()) {
                return
            }

            templateRemovalLabel.text =
                SpannableStringBuilder()
                    .append(TR.changeNotetypeWillDiscardCards())
                    .append(" ")
                    .boldList(discardedTemplateNames, LanguageUtil.getListSeparator(requireContext()))
        }

        private fun createTemplateSpinner() {
            binding.templatesContainer.removeAllViews()

            val inputTemplateNames = viewModel.inputNoteType.templatesNames
            val outputTemplateNames = viewModel.outputNoteType.templatesNames

            fun buildTemplateLayout() =
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams =
                        LinearLayout.LayoutParams(
                            MATCH_PARENT,
                            WRAP_CONTENT,
                        )
                }

            fun buildTemplateSpinner(spinnerIndex: Int) =
                Spinner(requireContext())
                    .apply {
                        layoutParams =
                            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    }.apply {
                        val templateSpinnerOptions = inputTemplateNames + TR.changeNotetypeNothing()
                        Timber.d("createTemplateSpinner: %d items + (nothing)", templateSpinnerOptions.size - 1)
                        adapter =
                            ArrayAdapter(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                templateSpinnerOptions,
                            ).apply {
                                // The resource passed to the constructor is normally used for both the spinner view
                                // and the dropdown list. This keeps the former and overrides the latter.
                                setDropDownViewResource(R.layout.item_spinner_dropdown_with_radio)
                            }

                        val selectionIndex = viewModel.templateChangeMap[spinnerIndex] ?: templateSpinnerOptions.lastIndex
                        setSelection(selectionIndex, false)

                        onItemSelectedListener =
                            BasicItemSelectedListener { position, id ->
                                // The last index is '(Nothing)'
                                val newMapping =
                                    if (position == templateSpinnerOptions.lastIndex) {
                                        SelectedIndex.NOTHING
                                    } else {
                                        SelectedIndex.from(position)
                                    }
                                viewModel.updateTemplateMapping(outputTemplateIndex = spinnerIndex, newMapping)
                            }
                    }

            fun buildTemplateText(templateName: String) =
                MaterialTextView(requireContext()).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    text = templateName
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }

            for ((i, name) in outputTemplateNames.withIndex()) {
                val templateLayout =
                    buildTemplateLayout().apply {
                        addView(
                            buildTemplateSpinner(
                                spinnerIndex = i,
                            ),
                        )
                        addView(buildTemplateText(templateName = name))
                    }
                binding.templatesContainer.addView(templateLayout)
            }
        }
    }
}

/**
 * Changes note type of multiple notes, displaying a message on success
 */
private fun AnkiActivity.changeNoteType(viewModel: ChangeNoteTypeViewModel) =
    this.launchCatchingRequiringOneWaySync {
        try {
            val notesUpdated =
                withProgress {
                    viewModel.executeChangeNoteTypeAsync().await()
                }
            val message = TR.browsingNotesUpdated(notesUpdated)
            showSnackbar(message, Snackbar.LENGTH_SHORT)
        } catch (ex: ChangeNoteTypeException) {
            showError(ex.kind.toString(this), crashReportData = null)
        }
    }

@VisibleForTesting
fun ChangeNoteTypeException.Kind.toString(context: Context): String =
    when (this) {
        ChangeNoteTypeException.Kind.NO_CHANGES -> context.getString(R.string.error_no_changes_to_save)
    }
