//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs.tags

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.RadioGroup
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.OnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.databinding.DialogTagsBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.withCollapsedWhitespace
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.TagsUtil
import com.ichi2.utils.customView
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber

class TagsDialog : AnalyticsDialogFragment {
    /**
     * Enum that define all possible types of TagsDialog
     */
    @Parcelize
    enum class DialogType : Parcelable {
        /**
         * Edit tags of note(s)
         */
        EDIT_TAGS,

        /**
         * Filter notes by tags
         */
        FILTER_BY_TAG,

        CUSTOM_STUDY,
    }

    private lateinit var binding: DialogTagsBinding
    private var type: DialogType? = null
    private var tagsArrayAdapter: TagsArrayAdapter? = null
    private var toolbarSearchView: AccessibleSearchView? = null
    private var toolbarSearchItem: MenuItem? = null
    private val listener: TagsDialogListener?

    private lateinit var selectedOption: CardStateFilter

    @VisibleForTesting
    val viewModel: TagsDialogViewModel by viewModels {
        val idsFile = requireArguments().requireParcelable<IdsFile>(ARG_TAGS_FILE)
        val noteIds = idsFile.getIds()
        val checkedTags =
            requireNotNull(requireArguments().getStringArrayList(ARG_CHECKED_TAGS)) {
                "$ARG_CHECKED_TAGS is required"
            }
        val type = BundleCompat.getParcelable(requireArguments(), ARG_DIALOG_TYPE, DialogType::class.java)
        val isCustomStudying = type != null && type == DialogType.CUSTOM_STUDY
        viewModelFactory {
            initializer {
                TagsDialogViewModel(
                    noteIds = noteIds,
                    checkedTags = checkedTags,
                    isCustomStudying = isCustomStudying,
                )
            }
        }
    }

    /**
     * Constructs a new [TagsDialog] that will communicate the results using the provided listener.
     */
    constructor(listener: TagsDialogListener?) {
        this.listener = listener
    }

    /**
     * Constructs a new [TagsDialog] that will communicate the results using Fragment Result API.
     *
     * @see [Fragment Result API](https://developer.android.com/guide/fragments/communicate.fragment-result)
     */
    constructor() {
        listener = null
    }

    /**
     * Construct a tags dialog for a collection of notes
     *
     * @param type the type of dialog @see [DialogType]
     * @return Initialized instance of [TagsDialog]
     */
    fun withArguments(
        context: Context,
        type: DialogType,
        noteIds: List<NoteId> = emptyList(),
        checkedTags: ArrayList<String> = arrayListOf(),
    ): TagsDialog {
        // TODO: checkedTags is unbounded and could exceed the bundle size
        val file = IdsFile(context.cacheDir, noteIds)
        arguments = this.arguments ?: Bundle().apply {
            putParcelable(ARG_TAGS_FILE, file)
            putParcelable(ARG_DIALOG_TYPE, type)
            putStringArrayList(ARG_CHECKED_TAGS, checkedTags)
        }
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resizeWhenSoftInputShown(requireActivity().window)

        type = requireArguments().requireParcelable(ARG_DIALOG_TYPE)
        isCancelable = true
    }

    private val tagsDialogListener: TagsDialogListener
        get() =
            listener
                ?: TagsDialogListener.createFragmentResultSender(parentFragmentManager)

    @NeedsTest(
        "In EDIT_TAGS dialog, long-clicking a tag should open the add tag dialog with the clicked tag" +
            " filled as prefix properly. In other dialog types, long-clicking a tag behaves like a short click.",
    )
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogTagsBinding.inflate(layoutInflater)

        val positiveText =
            if (type == DialogType.EDIT_TAGS) {
                getString(R.string.dialog_confirm)
            } else {
                getString(R.string.select)
            }

        val tagsListLayout: RecyclerView.LayoutManager = LinearLayoutManager(requireContext())
        binding.tagsList.apply {
            requestFocus()
            layoutManager = tagsListLayout
        }
        binding.optionsGroup.apply {
            isVisible = type != DialogType.EDIT_TAGS && type != DialogType.CUSTOM_STUDY
            for (i in 0 until childCount) {
                getChildAt(i).id = i
            }
            check(0)
        }
        selectedOption = radioButtonIdToCardState(binding.optionsGroup.checkedRadioButtonId)
        binding.optionsGroup.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            selectedOption = radioButtonIdToCardState(checkedId)
        }

        adjustToolbar(binding.root)

        val dialog =
            AlertDialog
                .Builder(requireActivity())
                .positiveButton(text = positiveText) { onPositiveButton() }
                .negativeButton(R.string.dialog_cancel)
                .customView(view = binding.root)
                .create()

        lifecycleScope.launch {
            val showProgressJob =
                launch {
                    delay(600)
                    withContext(Dispatchers.Main) {
                        binding.loadingContainer.visibility = View.VISIBLE
                        viewModel.initProgress
                            .flowWithLifecycle(lifecycle)
                            .onEach { progress ->
                                binding.progressText.text =
                                    when (progress) {
                                        TagsDialogViewModel.InitProgress.Processing ->
                                            getString(R.string.dialog_processing)
                                        is TagsDialogViewModel.InitProgress.FetchingNoteTags ->
                                            "${progress.noteNumber}/${progress.noteCount}"
                                        TagsDialogViewModel.InitProgress.Finished -> null
                                    }
                            }.launchIn(lifecycleScope)
                    }
                }
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            positiveButton?.isEnabled = false

            val tags = viewModel.tags.await()

            tagsArrayAdapter = TagsArrayAdapter(tags) { binding.root.showMaxTagSelectedNotice(tags) }
            binding.tagsList.adapter = tagsArrayAdapter
            if (tags.isEmpty) {
                binding.noTagsTextView.visibility = View.VISIBLE
            }
            tagsArrayAdapter?.tagContextAndLongClickListener =
                if (type == DialogType.EDIT_TAGS) {
                    OnContextAndLongClickListener { v ->
                        createAddTagDialog(v.tag as String)
                        true
                    }
                } else {
                    OnContextAndLongClickListener { false }
                }
            showProgressJob.cancel()
            binding.loadingContainer.isVisible = false
            positiveButton?.isEnabled = true
        }

        dialog.window?.let {
            resizeWhenSoftInputShown(it)
        }

        return dialog
    }

    private fun View.showMaxTagSelectedNotice(tags: TagsList) {
        if (type == DialogType.CUSTOM_STUDY && tags.copyOfCheckedTagList().size > 100) {
            // the backend text is long and is more like an explanation and doesn't fit into
            // a snackbar so cut just for the first sentence:
            //  "A maximum of 100 tags can be selected."
            val backendText = withCollapsedWhitespace(TR.errors100TagsMax())
            val firstPointIndex = backendText.indexOf(".")
            val userFacingText =
                if (firstPointIndex < 0) {
                    // backend text was changed so just return the full text
                    backendText
                } else {
                    backendText.substring(0..firstPointIndex)
                }
            this.showSnackbar(userFacingText)
        }
    }

    private fun onPositiveButton() {
        lifecycleScope.launch {
            val tags = viewModel.tags.await()
            tagsDialogListener.onSelectedTags(
                tags.copyOfCheckedTagList(),
                tags.copyOfIndeterminateTagList(),
                selectedOption,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }

    private fun radioButtonIdToCardState(id: Int) =
        when (id) {
            0 -> CardStateFilter.ALL_CARDS
            1 -> CardStateFilter.NEW
            2 -> CardStateFilter.DUE
            else -> {
                Timber.w("unexpected value: %d", id)
                CardStateFilter.ALL_CARDS
            }
        }

    private fun adjustToolbar(tagsDialogView: View) {
        val toolbar: Toolbar = binding.toolbar.root
        val titleRes = if (type == DialogType.EDIT_TAGS) R.string.card_details_tags else R.string.studyoptions_limit_select_tags
        toolbar.setTitle(titleRes)

        val toolbarAddItem = toolbar.menu.findItem(R.id.tags_dialog_action_add)
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_add)
        drawable?.setTint(requireContext().getColor(R.color.white))
        toolbarAddItem.icon = drawable

        toolbarAddItem.setOnMenuItemClickListener {
            val query = toolbarSearchView!!.query.toString()
            if (toolbarSearchItem!!.isActionViewExpanded && query.isNotEmpty()) {
                addTag(query)
                toolbarSearchView!!.setQuery("", true)
            } else {
                createAddTagDialog(null)
            }
            true
        }
        toolbarSearchItem = toolbar.menu.findItem(R.id.tags_dialog_action_filter)
        val toolbarSearchItem: MenuItem? = toolbarSearchItem
        toolbarSearchView = toolbarSearchItem?.actionView as AccessibleSearchView
        val queryET = toolbarSearchView!!.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        queryET.filters = arrayOf(addTagFilter)
        toolbarSearchView!!.queryHint = getString(R.string.filter_tags)
        toolbarSearchView!!.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    toolbarSearchView!!.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    tagsArrayAdapter?.filter?.filter(newText)
                    return true
                }
            },
        )
        val checkAllItem = toolbar.menu.findItem(R.id.tags_dialog_action_select_all)
        checkAllItem.setOnMenuItemClickListener {
            launchCatchingTask {
                val tags = viewModel.tags.await()
                val didChange = tags.toggleAllCheckedStatuses()
                if (didChange) {
                    tagsArrayAdapter?.notifyDataSetChanged()
                    view?.showMaxTagSelectedNotice(tags)
                }
            }
            true
        }
        if (type == DialogType.EDIT_TAGS) {
            toolbarSearchView!!.queryHint = getString(R.string.add_new_filter_tags)
        } else {
            toolbarAddItem.isVisible = false
        }
    }

    /**
     * Create an add tag dialog.
     *
     * @param prefixTag: The tag to be prefilled into the EditText section. A trailing '::' will be appended.
     */
    @NeedsTest("The prefixTag should be prefilled properly")
    @NeedsTest("Entering an existing tag should show an error and disable the OK button")
    @NeedsTest("Entering a new valid tag should clear the error and enable the OK button")
    private fun createAddTagDialog(prefixTag: String?) {
        val addTagDialog =
            AlertDialog
                .Builder(requireActivity())
                .show {
                    title(text = getString(R.string.add_tag))
                    positiveButton(R.string.menu_add)
                    negativeButton(R.string.dialog_cancel)
                    setView(R.layout.dialog_generic_text_input)
                }.input(
                    hint = TR.actionsName().dropLastWhile { it == ':' },
                    inputType = InputType.TYPE_CLASS_TEXT,
                    displayKeyboard = true,
                ) { d: AlertDialog?, input: CharSequence ->
                    addTag(input.toString())
                    d?.dismiss()
                }
        val inputET = addTagDialog.getInputField()
        inputET.filters = arrayOf(addTagFilter)
        if (!prefixTag.isNullOrEmpty()) {
            // utilize the addTagFilter to append '::' properly by appending a space to prefixTag
            inputET.setText("$prefixTag ")
        }
        inputET.moveCursorToEnd()
        val positiveButton =
            addTagDialog.getButton(AlertDialog.BUTTON_POSITIVE)

        positiveButton.isEnabled = false

        val textInputLayout =
            inputET.parent?.parent
                as? com.google.android.material.textfield.TextInputLayout

        inputET.doAfterTextChanged { text ->

            val rawTag = text?.toString()?.trim()

            if (rawTag.isNullOrEmpty()) {
                textInputLayout?.error = null
                positiveButton.isEnabled = false
                return@doAfterTextChanged
            }

            lifecycleScope.launch {
                val tags = viewModel.tags.await()

                val normalized =
                    TagsUtil.getUniformedTag(rawTag)

                val exists =
                    tags.contains(normalized)

                if (exists) {
                    textInputLayout?.error =
                        getString(R.string.tag_already_exists)

                    positiveButton.isEnabled = false
                } else {
                    textInputLayout?.error = null

                    positiveButton.isEnabled = true
                }
            }
        }
        addTagDialog.show()
    }

    @VisibleForTesting
    fun addTag(rawTag: String?) {
        if (rawTag.isNullOrEmpty()) return
        lifecycleScope.launch {
            val tags = viewModel.tags.await()
            val tag = TagsUtil.getUniformedTag(rawTag)
            val feedbackText: String
            if (tags.add(tag)) {
                binding.noTagsTextView.isVisible = false
                tags.add(tag)
                val positiveText = (dialog as? AlertDialog)?.positiveButton?.text ?: getString(R.string.dialog_ok)
                feedbackText = getString(R.string.tag_editor_add_feedback, tag, positiveText)
            } else {
                feedbackText = getString(R.string.tag_editor_add_feedback_existing, tag)
            }
            tags.check(tag)
            tagsArrayAdapter?.sortData()
            tagsArrayAdapter?.notifyDataSetChanged()
            // Expand to reveal the newly added tag.
            tagsArrayAdapter?.filter?.apply {
                setExpandTarget(tag)
                refresh()
            }

            // Show a snackbar to let the user know the tag was added successfully
            binding.tagsDialogSnackbar.showSnackbar(feedbackText)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun getSearchView(): AccessibleSearchView? = toolbarSearchView

    companion object {
        const val ARG_TAGS_FILE = "tagsFile"
        private const val ARG_DIALOG_TYPE = "dialogType"
        private const val ARG_CHECKED_TAGS = "checkedTags"

        /**
         * The filter that constrains the inputted tag.
         * Space is not allowed in a tag. For UX of hierarchical tag, inputting a space will instead
         * insert "::" at the cursor. If there are already some colons in front of the cursor,
         * complete to 2 colons. For example:
         *   "tag"   -- input a space --> "tag::"
         *   "tag:"  -- input a space --> "tag::"
         *   "tag::" -- input a space --> "tag::"
         */
        private val addTagFilter =
            InputFilter { source: CharSequence, start: Int, end: Int, dest: Spanned?, destStart: Int, _: Int ->
                if (!source.subSequence(start, end).contains(' ')) {
                    return@InputFilter null
                }
                var previousColonsCnt = 0
                if (dest != null) {
                    val previousPart = dest.take(destStart)
                    if (previousPart.endsWith("::")) {
                        previousColonsCnt = 2
                    } else if (previousPart.endsWith(":")) {
                        previousColonsCnt = 1
                    }
                }
                val sb = StringBuilder()
                for (char in source.subSequence(start, end)) {
                    if (char == ' ') {
                        if (previousColonsCnt == 0) {
                            sb.append("::")
                        } else if (previousColonsCnt == 1) {
                            sb.append(":")
                        }
                    } else {
                        sb.append(char)
                    }
                    previousColonsCnt =
                        if (char == ':') {
                            previousColonsCnt + 1
                        } else {
                            0
                        }
                }
                sb
            }
    }
}
