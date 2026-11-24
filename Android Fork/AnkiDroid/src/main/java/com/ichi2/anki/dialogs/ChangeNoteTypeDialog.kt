// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.view.Gravity
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
import androidx.core.text.BidiFormatter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.getResFromAttr
import com.ichi2.anki.compat.setTooltipTextCompat
import com.ichi2.anki.databinding.DialogChangeNoteTypeBinding
import com.ichi2.anki.databinding.DialogFieldsBinding
import com.ichi2.anki.databinding.DialogTemplatesBinding
import com.ichi2.anki.databinding.ViewTabLayoutIconOnStartBinding
import com.ichi2.anki.dialogs.ConversionType.CLOZE_TO_CLOZE
import com.ichi2.anki.dialogs.ConversionType.CLOZE_TO_REGULAR
import com.ichi2.anki.dialogs.ConversionType.REGULAR_TO_CLOZE
import com.ichi2.anki.dialogs.ConversionType.REGULAR_TO_REGULAR
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.showError
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.sync.launchCatchingRequiringOneWaySync
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.InitStatus
import com.ichi2.anki.utils.ext.collectInLifecycle
import com.ichi2.anki.withProgress
import com.ichi2.themes.Themes
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.boldList
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber
import com.ichi2.anki.common.android.R as CommonR

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
@NeedsTest("Screenshot baseline for Change Note Type Dialog UI")
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
        setupEdgeToEdge(binding)

        binding.toolbar.title = TR.sentenceCase.changeNoteType
        binding.toolbar.setNavigationOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener {
            requireActivity().changeNoteType(viewModel)
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

    private fun setupEdgeToEdge(binding: DialogChangeNoteTypeBinding) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val window = dialog?.window ?: return
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !Themes.isNightTheme
        }
    }

    private fun setupFlows() {
        launchCatchingTask {
            viewModel.closeDialogFlow.filterNotNull().collect {
                Timber.i("Dismissing dialog")
                parentFragmentManager.setFragmentResult(REQUEST_KEY_NOTE_TYPE_CHANGED, Bundle())
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
        viewModel.hasChangesFlow.collectInLifecycle {
            binding.btnSave.isEnabled = it
        }
    }

    private fun setupNoteTypeSpinner(binding: DialogChangeNoteTypeBinding) {
        binding.CardEditorModelText.text = BidiFormatter.getInstance().unicodeWrap(viewModel.inputNoteType.name)
        binding.CardEditorModelText.setTooltipTextCompat(viewModel.inputNoteType.name)

        binding.destNoteTypeSpinner.apply {
            adapter = createNoteTypeAdapter()

            val position = viewModel.availableNoteTypes.indexOfFirst { it.id == viewModel.outputNoteType.id }
            setSelection(position, false)

            onItemSelectedListener =
                BasicItemSelectedListener { position, id: NoteTypeId ->
                    viewModel.setOutputNoteTypeId(id)
                }
        }

        binding.toCard.setOnClickListener {
            binding.destNoteTypeSpinner.performClick()
        }
    }

    private fun createNoteTypeAdapter(): ArrayAdapter<DisplayNoteType> {
        val noteTypes = viewModel.availableNoteTypes.map { DisplayNoteType(it.name, it.isCloze) }

        return object : ArrayAdapter<DisplayNoteType>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            noteTypes,
        ) {
            var clozeColor = MaterialColors.getColor(context, CommonR.attr.clozeColor, Color.BLUE)

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
                isSingleLine = false
                // Leave room for the mappings even when the selected note type has a long name.
                maxLines = resources.getInteger(R.integer.change_note_type_name_max_lines)
                ellipsize = TextUtils.TruncateAt.END
                setTooltipTextCompat(noteType.name)
                updateLayoutParams {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                setPaddingRelative(0, paddingTop, paddingEnd, paddingBottom)
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
        // Measure the labels and icons before fixed tabs constrain their width.
        val minimumTabWidth =
            (0 until tabLayout.tabCount).maxOf { position ->
                val tab = requireNotNull(tabLayout.getTabAt(position))
                val content = requireNotNull(tab.customView)
                content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                content.measuredWidth + tab.view.paddingLeft + tab.view.paddingRight
            }
        tabLayout.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val availableWidth = right - left - tabLayout.paddingLeft - tabLayout.paddingRight
            val mode =
                if (minimumTabWidth * tabLayout.tabCount <= availableWidth) TabLayout.MODE_FIXED else TabLayout.MODE_AUTO
            if (tabLayout.tabMode != mode) {
                tabLayout.tabMode = mode
                tabLayout.doOnNextLayout {
                    tabLayout.setScrollPosition(tabLayout.selectedTabPosition, 0f, true)
                }
            }
        }
        // Explicitly set initial tab in ViewModel to match UI

        viewPager.setCurrentItem(0, false)
        viewModel.selectTabByPosition(0)
    }

    private fun createTabMediator(
        tabLayout: TabLayout,
        viewPager: ViewPager2,
    ): TabLayoutMediator =
        TabLayoutMediator(tabLayout, viewPager) { tab: TabLayout.Tab, position: Int ->
            val binding = ViewTabLayoutIconOnStartBinding.inflate(LayoutInflater.from(tabLayout.context), tabLayout, false)
            when (position) {
                0 -> {
                    binding.tabIcon.setImageResource(R.drawable.ic_mode_edit_white)
                    binding.tabText.text = TR.changeNotetypeFields()
                }
                1 -> {
                    binding.tabIcon.setImageResource(R.drawable.ic_card_question)
                    binding.tabText.text = TR.changeNotetypeTemplates()
                }
            }

            val selectedColor =
                MaterialColors.getColor(
                    tabLayout,
                    com.google.android.material.R.attr.colorPrimaryVariant,
                )

            val unselectedColor =
                MaterialColors.getColor(
                    tabLayout,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    Color.GRAY,
                )

            val colorStateList =
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_selected),
                        intArrayOf(),
                    ),
                    intArrayOf(
                        selectedColor,
                        unselectedColor,
                    ),
                )

            binding.tabIcon.imageTintList = colorStateList
            binding.tabText.setTextColor(colorStateList)

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
                    Bundle().apply {
                        putLongArray(ARG_NOTE_IDS, ids.toLongArray())
                    }
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

            fun buildFieldSpinner(spinnerIndex: Int) =
                Spinner(requireContext())
                    .apply {
                        layoutParams =
                            LinearLayout
                                .LayoutParams(
                                    0,
                                    WRAP_CONTENT,
                                    1f,
                                )
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

            for ((i, name) in outputFieldNames.withIndex()) {
                binding.fieldsContainer.addView(createMappingRow(buildFieldSpinner(i), name))
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
                        is Layout.Standard -> {
                            binding.clozeInfoLayout.isVisible = false
                        }
                        is Layout.WithWarning -> {
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
                                warningRes = CommonString.card_numbers_unchanged,
                            )
                        // Improvement: we could detect this using the max ord of provided notes
                        CLOZE_TO_REGULAR ->
                            WithWarning(
                                warningRes = CommonString.extra_cloze_deletions_removed,
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

            if (!viewModel.canChangeTemplates) return

            val inputTemplateNames = viewModel.inputNoteType.templatesNames
            val outputTemplateNames = viewModel.outputNoteType.templatesNames

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

            for ((i, name) in outputTemplateNames.withIndex()) {
                binding.templatesContainer.addView(createMappingRow(buildTemplateSpinner(i), name))
            }
        }
    }
}

private fun createMappingRow(
    spinner: Spinner,
    label: String,
) = LinearLayout(spinner.context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    // Baseline alignment can push wrapped labels below the row's measured height.
    isBaselineAligned = false
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    addView(spinner)
    addView(
        MaterialTextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            val themeTextColors = textColors
            TextViewCompat.setTextAppearance(this, getResFromAttr(context, com.google.android.material.R.attr.textAppearanceBodyLarge))
            setTextColor(themeTextColors)
            text = label
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        },
    )
}

/**
 * Changes note type of multiple notes, displaying a message on success
 */
private fun FragmentActivity.changeNoteType(viewModel: ChangeNoteTypeViewModel) =
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
        ChangeNoteTypeException.Kind.NO_CHANGES -> context.getString(CommonString.error_no_changes_to_save)
    }
