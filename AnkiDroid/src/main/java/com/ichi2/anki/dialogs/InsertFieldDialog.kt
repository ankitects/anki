// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.databinding.DialogGenericRecyclerViewBinding
import com.ichi2.anki.databinding.DialogInsertFieldBinding
import com.ichi2.anki.databinding.ItemInsertSpecialFieldBinding
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.KEY_FIELD_ITEMS
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.KEY_INSERT_FIELD_METADATA
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.KEY_REQUEST_KEY
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Tab
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SpecialField
import com.ichi2.anki.model.SpecialFields
import dev.androidbroadcast.vbpd.viewBinding
import org.jetbrains.annotations.VisibleForTesting

/**
 * Dialog fragment used to show the fields that the user can insert in the card editor. This
 * fragment can notify other fragments from the same activity about an inserted field.
 *
 * @see [CardTemplateEditor.CardTemplateFragment]
 */
class InsertFieldDialog : DialogFragment(R.layout.dialog_insert_field) {
    private val viewModel by viewModels<InsertFieldDialogViewModel>()
    private val requestKey
        get() =
            requireNotNull(requireArguments().getString(KEY_REQUEST_KEY)) {
                KEY_REQUEST_KEY
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.ThemeOverlay_AnkiDroid_AlertDialog_FullScreen)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val binding = DialogInsertFieldBinding.bind(view)

        binding.toolbar.title = getString(R.string.card_template_editor_select_field)
        binding.toolbar.setNavigationOnClickListener { dismiss() }

        binding.viewPager.adapter = InsertFieldDialogAdapter(this)
        TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager,
        ) { tab: TabLayout.Tab, position: Int ->
            val entry =
                Tab.entries
                    .first { it.position == position }

            tab.text = entry.title
        }.attach()
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(viewModel.currentTab.position))

        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    Tab.entries
                        .first { it.position == position }
                        .let { selectedTab ->
                            viewModel.currentTab = selectedTab
                        }
                    super.onPageSelected(position)
                }
            },
        )

        // setup flows
        launchCatchingTask {
            viewModel.selectedFieldFlow.collect { field ->
                if (field == null) return@collect
                parentFragmentManager.setFragmentResult(
                    requestKey,
                    Bundle().apply { putString(KEY_INSERTED_FIELD, field.renderToTemplateTag()) },
                )
                dismiss()
            }
        }
    }

    companion object {
        /**
         * A key in the extras of the Fragment Result
         *
         * Represents the template tag for the selected field: `{{Front}}`
         */
        const val KEY_INSERTED_FIELD = "key_inserted_field"

        /**
         * Creates a new instance of [InsertFieldDialog]
         *
         * @param fieldItems The list of field names to be displayed in the dialog.
         * @param requestKey The key used to identify the result when returning the selected field
         *                   to the calling fragment.
         */
        fun newInstance(
            fieldItems: List<String>,
            metadata: InsertFieldMetadata,
            requestKey: String,
        ): InsertFieldDialog =
            InsertFieldDialog().apply {
                arguments =
                    Bundle().apply {
                        putStringArrayList(KEY_FIELD_ITEMS, ArrayList(fieldItems))
                        putParcelable(KEY_INSERT_FIELD_METADATA, metadata)
                        putString(KEY_REQUEST_KEY, requestKey)
                    }
            }
    }

    class InsertFieldDialogAdapter(
        fragment: Fragment,
    ) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment =
            when (position) {
                0 -> SelectBasicFieldFragment()
                1 -> SelectSpecialFieldFragment()
                else -> throw IllegalStateException("invalid position: $position")
            }

        override fun getItemCount() = 2
    }

    class SelectBasicFieldFragment : Fragment(R.layout.dialog_generic_recycler_view) {
        val viewModel by viewModels<InsertFieldDialogViewModel>(
            ownerProducer = { requireParentFragment() as InsertFieldDialog },
        )
        val binding by viewBinding(DialogGenericRecyclerViewBinding::bind)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)

            binding.root.layoutManager = LinearLayoutManager(context)
            binding.root.adapter =
                object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun onCreateViewHolder(
                        parent: ViewGroup,
                        viewType: Int,
                    ): RecyclerView.ViewHolder {
                        val root = layoutInflater.inflate(R.layout.item_material_dialog, parent, false)
                        return object : RecyclerView.ViewHolder(root) {}
                    }

                    override fun onBindViewHolder(
                        holder: RecyclerView.ViewHolder,
                        position: Int,
                    ) {
                        val textView = holder.itemView as TextView
                        val field = viewModel.fieldNames[position]
                        textView.text = field.name
                        textView.setOnClickListener { viewModel.selectNamedField(field) }
                    }

                    override fun getItemCount(): Int = viewModel.fieldNames.size
                }
        }
    }

    class SelectSpecialFieldFragment : Fragment(R.layout.dialog_generic_recycler_view) {
        val viewModel by viewModels<InsertFieldDialogViewModel>(
            ownerProducer = { requireParentFragment() as InsertFieldDialog },
        )
        val binding by viewBinding(DialogGenericRecyclerViewBinding::bind)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            super.onViewCreated(view, savedInstanceState)

            binding.root.adapter =
                object : RecyclerView.Adapter<InsertFieldViewHolder>() {
                    override fun onCreateViewHolder(
                        parent: ViewGroup,
                        viewType: Int,
                    ) = InsertFieldViewHolder(
                        ItemInsertSpecialFieldBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false,
                        ),
                    )

                    override fun onBindViewHolder(
                        holder: InsertFieldViewHolder,
                        position: Int,
                    ) {
                        val field = viewModel.specialFields[position]

                        holder.binding.title.text = "{{${field.name}}}"
                        holder.binding.description.text = field.buildDescription(requireContext(), viewModel.metadata)
                        holder.binding.root.setOnClickListener { viewModel.selectSpecialField(field) }
                    }

                    override fun getItemCount(): Int = viewModel.specialFields.size
                }
            binding.root.layoutManager = LinearLayoutManager(context)
        }
    }

    private class InsertFieldViewHolder(
        val binding: ItemInsertSpecialFieldBinding,
    ) : RecyclerView.ViewHolder(binding.root)
}

@VisibleForTesting
@CheckResult
fun SpecialField.buildDescription(
    context: Context,
    metadata: InsertFieldMetadata,
): Spanned {
    fun buildSuffix(value: String?): String {
        if (value == null) return ""
        return context.getString(R.string.special_field_example_suffix, value)
    }
    return when (this) {
        SpecialFields.FrontSide -> context.getString(R.string.special_field_front_side_help)
        SpecialFields.Deck ->
            context.getString(R.string.special_field_deck_help, buildSuffix(metadata.deck))

        SpecialFields.Subdeck ->
            context.getString(R.string.special_field_subdeck_help, buildSuffix(metadata.subdeck))
        SpecialFields.Flag -> {
            val code = metadata.flag ?: "N"
            context.getString(
                R.string.special_field_flag_help,
                if (code == "N") "flag$code" else "<b>flag$code</b>",
                "<b>$code</b>",
                Flag.entries.minOf { it.code }.toString(),
                Flag.entries.maxOf { it.code }.toString(),
            )
        }
        SpecialFields.Tags -> {
            val tags = if (metadata.tags.isNullOrBlank()) null else metadata.tags
            context.getString(R.string.special_field_tags_help, buildSuffix(tags))
        }
        SpecialFields.CardId ->
            context.getString(R.string.special_field_card_id_help, buildSuffix(metadata.cardId?.toString()))

        SpecialFields.CardTemplate ->
            context.getString(
                R.string.special_field_card_help,
                buildSuffix(metadata.cardTemplateName),
            )

        SpecialFields.NoteType ->
            context.getString(
                R.string.special_field_type_help,
                buildSuffix(metadata.noteTypeName),
            )
        // this shouldn't happen
        else -> ""
    }.parseAsHtml(HtmlCompat.FROM_HTML_MODE_LEGACY)
}

context(dialog: InsertFieldDialog)
private val Tab.title: String
    @StringRes
    get() =
        dialog.requireContext().getString(
            when (this) {
                Tab.FIELDS -> R.string.standard_fields_tab_header
                Tab.SPECIAL -> R.string.special_fields_tab_header
            },
        )
