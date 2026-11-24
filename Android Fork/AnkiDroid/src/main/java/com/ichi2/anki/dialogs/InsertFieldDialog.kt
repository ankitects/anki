// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Context
import android.os.Build
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
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
import com.ichi2.anki.CommonString
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.common.android.databinding.DialogGenericRecyclerViewBinding
import com.ichi2.anki.databinding.DialogInsertFieldBinding
import com.ichi2.anki.databinding.ItemInsertSpecialFieldBinding
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.ARG_FIELD_NAMES
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.ARG_INSERT_FIELD_METADATA
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Companion.ARG_REQUEST_KEY
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.Tab
import com.ichi2.anki.launchCatchingViewTask
import com.ichi2.anki.model.SpecialField
import com.ichi2.anki.model.SpecialFields
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.themes.Themes
import dev.androidbroadcast.vbpd.viewBinding
import org.jetbrains.annotations.VisibleForTesting
import com.ichi2.anki.common.android.R as CommonR

/**
 * Dialog fragment used to show the fields that the user can insert in the card editor. This
 * fragment can notify other fragments from the same activity about an inserted field.
 *
 * @see [CardTemplateEditor.CardTemplateFragment]
 */
class InsertFieldDialog : DialogFragment(R.layout.dialog_insert_field) {
    private val viewModel by viewModels<InsertFieldDialogViewModel>()
    private val requestKey: String by lazy {
        requireArguments().requireString(ARG_REQUEST_KEY)
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
        setupEdgeToEdge(binding)

        binding.toolbar.title = getString(CommonString.card_template_editor_select_field)
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
        launchCatchingViewTask {
            viewModel.selectedFieldFlow.collect { field ->
                if (field == null) return@collect
                parentFragmentManager.setFragmentResult(
                    requestKey,
                    Bundle().apply { putString(RESULT_INSERTED_FIELD, field.renderToTemplateTag()) },
                )
                dismiss()
            }
        }
    }

    private fun setupEdgeToEdge(binding: DialogInsertFieldBinding) {
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

    companion object {
        /**
         * A key in the extras of the Fragment Result
         *
         * Represents the template tag for the selected field: `{{Front}}`
         */
        const val RESULT_INSERTED_FIELD = "key_inserted_field"

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
                        putStringArrayList(ARG_FIELD_NAMES, ArrayList(fieldItems))
                        putParcelable(ARG_INSERT_FIELD_METADATA, metadata)
                        putString(ARG_REQUEST_KEY, requestKey)
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

    class SelectBasicFieldFragment : Fragment(CommonR.layout.dialog_generic_recycler_view) {
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

    class SelectSpecialFieldFragment : Fragment(CommonR.layout.dialog_generic_recycler_view) {
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
        return context.getString(CommonString.special_field_example_suffix, value)
    }
    return when (this) {
        SpecialFields.FrontSide -> context.getString(CommonString.special_field_front_side_help)
        SpecialFields.Deck ->
            context.getString(CommonString.special_field_deck_help, buildSuffix(metadata.deck))

        SpecialFields.Subdeck ->
            context.getString(CommonString.special_field_subdeck_help, buildSuffix(metadata.subdeck))
        SpecialFields.Flag -> {
            val code = metadata.flag ?: "N"
            context.getString(
                CommonString.special_field_flag_help,
                if (code == "N") "flag$code" else "<b>flag$code</b>",
                "<b>$code</b>",
                Flag.entries.minOf { it.code }.toString(),
                Flag.entries.maxOf { it.code }.toString(),
            )
        }
        SpecialFields.Tags -> {
            val tags = if (metadata.tags.isNullOrBlank()) null else metadata.tags
            context.getString(CommonString.special_field_tags_help, buildSuffix(tags))
        }
        SpecialFields.CardId ->
            context.getString(CommonString.special_field_card_id_help, buildSuffix(metadata.cardId?.toString()))

        SpecialFields.CardTemplate ->
            context.getString(
                CommonString.special_field_card_help,
                buildSuffix(metadata.cardTemplateName),
            )

        SpecialFields.NoteType ->
            context.getString(
                CommonString.special_field_type_help,
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
                Tab.FIELDS -> CommonString.standard_fields_tab_header
                Tab.SPECIAL -> CommonString.special_fields_tab_header
            },
        )
