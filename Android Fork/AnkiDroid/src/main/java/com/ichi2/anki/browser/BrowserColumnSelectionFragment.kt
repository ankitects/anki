// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnSelectionRecyclerItem.ColumnItem
import com.ichi2.anki.browser.BrowserColumnSelectionRecyclerItem.UsageItem
import com.ichi2.anki.browser.ColumnUsage.ACTIVE
import com.ichi2.anki.browser.ColumnUsage.AVAILABLE
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.databinding.DialogBrowserColumnsSelectionBinding
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.launchCatchingViewTask
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.requireParcelable
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Allows a user to select and reorder the visible columns for the [CardBrowser]
 *
 * A user may drag columns between 2 [sections][ColumnUsage]: 'Active' and 'Available'
 * A user may use the + or - buttons to quickly move an item between sections
 *
 * A preview of the first browser row is displayed to help explain the meaning of fields
 *
 * A 'discard changes' dialog appears only if the 'active' list has been modified/reordered
 *
 * A user may not save if there are 0 columns
 *
 * This class must be hosted inside a [CardBrowser]
 */
@NeedsTest("saving")
@NeedsTest("dismissing: save changes dialog")
@NeedsTest("dismissing via 'save_columns'")
@NeedsTest("instance state restoration")
class BrowserColumnSelectionFragment : DialogFragment(R.layout.dialog_browser_columns_selection) {
    private val viewModel: CardBrowserViewModel by activityViewModels()

    private val binding by viewBinding(DialogBrowserColumnsSelectionBinding::bind)

    lateinit var columnAdapter: BrowserColumnSelectionAdapter

    /** The columns which were selected when this dialog was opened */
    private lateinit var initiallySelectedColumns: List<CardBrowserColumn>

    private val onBackPressedDispatcher
        get() = (dialog as ComponentDialog).onBackPressedDispatcher

    private val cardsOrNotes: CardsOrNotes by lazy {
        requireArguments().requireParcelable(ARG_MODE)
    }

    private val discardChangesCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                Timber.d("discardChangesCallback")
                DiscardChangesDialog.showDialog(requireContext()) {
                    Timber.i("OK button pressed to confirm discard changes")
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.ThemeOverlay_AnkiDroid_AlertDialog_FullScreen)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(STATE_ACTIVE, columnAdapter.displayed.toCollection(ArrayList()))
        outState.putParcelableArrayList(STATE_AVAILABLE, columnAdapter.available.toCollection(ArrayList()))
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets()

        if (savedInstanceState == null) {
            launchCatchingViewTask {
                val (active, available) = viewModel.loadColumnSelection(cardsOrNotes)
                setupRecyclerView(active, available)
            }
        } else {
            fun getSavedList(key: String) = BundleCompat.getParcelableArrayList(savedInstanceState, key, ColumnWithSample::class.java)!!
            setupRecyclerView(getSavedList(STATE_ACTIVE), getSavedList(STATE_AVAILABLE))
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            Timber.d("menu item click: %s", menuItem.title)
            when (menuItem.itemId) {
                R.id.action_save_columns -> {
                    if (!hasUnsavedChanges) {
                        Timber.d("no changes to save")
                        dismiss()
                    }

                    Timber.d("save columns and close")
                    if (viewModel.updateActiveColumns(columnAdapter.selected, cardsOrNotes)) {
                        dismiss()
                        true
                    } else {
                        Timber.w("could not save with 0 columns")
                        // you must have at least one column
                        showSnackbar(TR.browsingYouMustHaveAtLeastOne())
                        false
                    }
                }
                else -> false
            }
        }
        binding.toolbar.setNavigationOnClickListener {
            Timber.d("navigation up clicked")
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { view, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { view, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            view.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
        binding.root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)

        // setup back button handling
        onBackPressedDispatcher.addCallback(this, discardChangesCallback)
    }

    private fun setupRecyclerView(
        active: List<ColumnWithSample>,
        available: List<ColumnWithSample>,
    ) {
        // Create a RecyclerView with 2 types of elements: [ColumnWithSample] and [ColumnUsage]
        // Columns are draggable elements, the usage elements act as headings
        this.initiallySelectedColumns = active.map { it.columnType }

        val recyclerViewItems =
            sequence {
                yield(UsageItem(ACTIVE))
                yieldAll(active.map(::ColumnItem))

                yield(UsageItem(AVAILABLE))
                yieldAll(available.map(::ColumnItem))
            }.toMutableList()

        val itemTouchHelper = ItemTouchHelper(BrowserColumnSelectionTouchHelperCallback(recyclerViewItems))

        this.columnAdapter =
            BrowserColumnSelectionAdapter(recyclerViewItems).apply {
                setOnDragHandleTouchedListener { viewHolder ->
                    itemTouchHelper.startDrag(viewHolder)
                }
            }

        // handle 'discard changes'

        columnAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                // onItemRangeMoved and onItemRangeChanged are called
                override fun onItemRangeChanged(
                    positionStart: Int,
                    itemCount: Int,
                    payload: Any?,
                ) {
                    super.onItemRangeChanged(positionStart, itemCount, payload)
                    val initialValue = discardChangesCallback.isEnabled
                    val newValue = hasUnsavedChanges
                    discardChangesCallback.isEnabled = newValue
                    // only log if there is a change in value
                    if (initialValue != newValue) {
                        Timber.i("show discard warning on close: %b => %b", initialValue, newValue)
                    }
                }
            },
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = columnAdapter
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    /** Whether the user has added, removed or reordered the displayed columns */
    // Although a user can reorder the non-displayed columns, this order is not persisted
    private val hasUnsavedChanges: Boolean
        get() = initiallySelectedColumns != columnAdapter.selected

    companion object {
        const val ARG_MODE = "mode"
        private const val STATE_ACTIVE = "active"
        private const val STATE_AVAILABLE = "available"

        fun createInstance(cardsOrNotes: CardsOrNotes): BrowserColumnSelectionFragment =
            BrowserColumnSelectionFragment().apply {
                Timber.d("Building 'Manage columns' dialog for %s mode", cardsOrNotes)
                arguments =
                    Bundle().apply {
                        putParcelable(ARG_MODE, cardsOrNotes)
                    }
            }
    }
}

/**
 * Whether a [CardBrowserColumn] is being used in the [CardBrowser]
 */
enum class ColumnUsage(
    @StringRes val titleRes: Int,
) {
    /** A column displayed in Browse */
    ACTIVE(CommonString.user_active_columns),

    /** A column which is not displayed in Browse */
    AVAILABLE(CommonString.user_potential_columns),
}
