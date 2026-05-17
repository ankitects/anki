/*
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>
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
package com.ichi2.anki.notetype

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.databinding.ActivityManageNoteTypesBinding
import com.ichi2.anki.dialogs.dismissLoadingDialog
import com.ichi2.anki.dialogs.showLoadingDialog
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.notetype.ManageNoteTypesState.UserMessage
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.sync.userAcceptsSchemaChange
import com.ichi2.anki.utils.Destination
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.getInputField
import com.ichi2.utils.getInputTextLayout
import com.ichi2.utils.input
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch

class ManageNotetypes : AnkiActivity(R.layout.activity_manage_note_types) {
    private val binding by viewBinding(ActivityManageNoteTypesBinding::bind)
    val viewModel by viewModels<ManageNoteTypesViewModel>()

    private val notetypesAdapter: NoteTypesAdapter by lazy {
        NoteTypesAdapter(
            this@ManageNotetypes,
            onItemClick = viewModel::onItemClick,
            onItemLongClick = viewModel::onItemLongClick,
            onItemChecked = viewModel::onItemChecked,
            onEditCards = viewModel::onCardEditorRequested,
            onRename = ::renameNotetype,
            onDelete = ::deleteNotetype,
        )
    }
    private val outsideChangesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                viewModel.refreshNoteTypes()
            }
        }

    private val backCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                viewModel.clearSelection()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        enableToolbar().title = getString(R.string.model_browser_label)
        binding.noteTypesList.adapter = notetypesAdapter
        binding.floatingActionButton.apply {
            setOnClickListener {
                val addNewNotesType = AddNewNotesType(this@ManageNotetypes)
                launchCatchingTask { addNewNotesType.showAddNewNotetypeDialog() }
            }
            val params = (layoutParams as? CoordinatorLayout.LayoutParams)
            (params?.behavior as? HideViewOnScrollBehavior<View>)?.setViewEdge(HideViewOnScrollBehavior.EDGE_BOTTOM)
        }
        binding.btnClearSelection.setOnClickListener { viewModel.clearSelection() }

        binding.selectionToolbar.setOnClickListener {
            // Consume touch events to prevent tap-through
        }
        binding.btnDeleteSelection.setOnClickListener {
            launchCatchingTask {
                val deleteMessage =
                    if (userAcceptsSchemaChange()) {
                        val selection =
                            viewModel.selectedNoteTypes.joinToString { it.name }
                        getString(R.string.model_delete_multiple_warning, selection)
                    } else {
                        return@launchCatchingTask
                    }
                AlertDialog.Builder(this@ManageNotetypes).show {
                    title(R.string.dialog_positive_delete)
                    message(text = deleteMessage)
                    positiveButton(R.string.dialog_positive_delete) {
                        viewModel.deleteSelectedNoteTypes()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    backCallback.isEnabled = viewModel.state.value.isInMultiSelectMode
                    // as they are transient user messages take precedence and are immediately consumed
                    if (state.message != null) {
                        val snackbarMessage =
                            when (state.message) {
                                UserMessage.DeletingLastModel -> getString(R.string.toast_last_model)
                            }
                        showSnackbar(snackbarMessage)
                        viewModel.clearMessage()
                        return@collect
                    }
                    // after messages destinations are immediate targets for execution
                    val currentDestination = state.destination
                    if (currentDestination != null) {
                        viewModel.clearDestination()
                        outsideChangesLauncher.launch(currentDestination.toIntent(this@ManageNotetypes))
                        return@collect
                    }
                    bindState(state)
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun bindState(state: ManageNoteTypesState) {
        if (state.error != null) {
            if (state.error.isReportable) {
                CrashReportService.sendExceptionReport(
                    state.error.source,
                    ManageNotetypes::class.java.simpleName,
                )
            }
            AlertDialog.Builder(this).show {
                message(text = state.error.source.message)
                positiveButton(R.string.close) { viewModel.refreshNoteTypes() }
            }
            viewModel.clearError()
            return
        }
        if (state.isLoading) {
            showLoadingDialog()
        } else {
            dismissLoadingDialog()
        }
        // send only the items that should be displayed
        notetypesAdapter.submitList(state.noteTypes.filter { it.shouldBeDisplayed })
        notetypesAdapter.isInMultiSelectMode = state.isInMultiSelectMode
        supportActionBar?.subtitle =
            resources.getQuantityString(
                R.plurals.model_browser_types_available,
                state.noteTypes.size,
                state.noteTypes.size,
            )
        if (state.searchQuery.isNotEmpty()) {
            val searchMenuItem =
                findViewById<Toolbar>(R.id.toolbar).menu?.findItem(R.id.search_item)
            val searchView = searchMenuItem?.actionView as? AccessibleSearchView
            // Avoid resetting cursor position if query hasn't changed
            if (searchView?.query.toString() != state.searchQuery) {
                searchView?.setQuery(state.searchQuery, false)
            }
        }
        binding.selectionToolbar.isVisible = state.isInMultiSelectMode
        val selectedCount = state.noteTypes.count { it.isSelected }
        binding.selectedLabel.text =
            resources.getQuantityString(
                R.plurals.note_types_selected,
                selectedCount,
                selectedCount,
            )
        binding.floatingActionButton.isVisible = !state.isInMultiSelectMode
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search, menu)

        val searchItem = menu.findItem(R.id.search_item)
        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        val searchView = searchItem?.actionView as? AccessibleSearchView
        searchView?.maxWidth = Integer.MAX_VALUE
        searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))

        searchView?.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean = true

                override fun onQueryTextChange(newText: String?): Boolean {
                    if (newText != null && viewModel.state.value.searchQuery != newText) {
                        viewModel.filter(newText)
                    }
                    return true
                }
            },
        )
        return true
    }

    @VisibleForTesting
    internal fun renameNotetype(state: NoteTypeItemState) {
        launchCatchingTask {
            val allNotetypes = viewModel.state.value.noteTypes
            val dialog =
                AlertDialog
                    .Builder(this@ManageNotetypes)
                    .show {
                        title(R.string.rename_model)
                        positiveButton(R.string.rename) {
                            val userInput =
                                (it as AlertDialog)
                                    .getInputField()
                                    .text
                                    .toString()
                                    .trim()
                            if (userInput.isEmpty()) return@positiveButton
                            viewModel.rename(state.id, userInput)
                        }
                        negativeButton(R.string.dialog_cancel)
                        setView(R.layout.dialog_generic_text_input)
                    }.input(
                        hint = TR.deckConfigNamePrompt(),
                        prefill = state.name,
                        waitForPositiveButton = false,
                        displayKeyboard = true,
                        callback = { dialog, text ->
                            val inputStr = text.toString().trim()

                            val isDuplicate = allNotetypes.any { it.name.equals(inputStr, ignoreCase = true) }

                            val isUnchanged = inputStr == state.name

                            if (inputStr.isBlank()) {
                                dialog.getInputTextLayout().error = null
                                dialog.positiveButton.isEnabled = false
                                return@input
                            } else if (isDuplicate && !isUnchanged) {
                                dialog.getInputTextLayout().error = getString(R.string.error_name_exists)
                                dialog.positiveButton.isEnabled = false
                                return@input
                            }

                            dialog.getInputTextLayout().error = null
                            dialog.positiveButton.isEnabled = !isUnchanged
                        },
                    )
            // start with the button disabled as dialog shows the initial name
            dialog.positiveButton.isEnabled = false
        }
    }

    @VisibleForTesting
    internal fun deleteNotetype(state: NoteTypeItemState) {
        launchCatchingTask {
            @StringRes val messageResourceId: Int? =
                if (userAcceptsSchemaChange()) {
                    R.string.model_delete_warning
                } else {
                    return@launchCatchingTask
                }
            AlertDialog.Builder(this@ManageNotetypes).show {
                title(R.string.model_browser_delete)
                message(messageResourceId)
                positiveButton(R.string.dialog_positive_delete) {
                    viewModel.delete(state.id)
                }
                negativeButton(R.string.dialog_cancel)
            }
        }
    }
}

class ManageNoteTypesDestination : Destination {
    override fun toIntent(context: Context) = Intent(context, ManageNotetypes::class.java)
}
