// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateMargins
import androidx.core.view.updateMarginsRelative
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.awaitCatchingTask
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.ActivityManageNoteTypesBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.notetype.ManageNoteTypesState.UserMessage
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.anki.sync.userAcceptsSchemaChange
import com.ichi2.anki.utils.Destination
import com.ichi2.themes.setTransparentStatusBar
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
import timber.log.Timber
import com.ichi2.anki.common.android.R as CommonR

class ManageNotetypes : AnkiActivity(R.layout.activity_manage_note_types) {
    @VisibleForTesting
    val binding by viewBinding(ActivityManageNoteTypesBinding::bind)
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
        if (!ensureStorageIsReady()) {
            return
        }
        enableEdgeToEdge()
        applyInsets()
        setTransparentStatusBar()
        enableToolbar()
        binding.noteTypesList.adapter = notetypesAdapter
        observeProgress(viewModel) { it }
        viewModel.pendingAdd?.awaitCatchingTask()
        binding.floatingActionButton.apply {
            setOnClickListener {
                val addNewNotesType = AddNewNotesType(this@ManageNotetypes)
                launchCatchingTask { addNewNotesType.showAddNewNotetypeDialog() }
            }
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
                        getString(CommonString.model_delete_multiple_warning, selection)
                    } else {
                        return@launchCatchingTask
                    }
                AlertDialog.Builder(this@ManageNotetypes).show {
                    title(CommonString.dialog_positive_delete)
                    message(text = deleteMessage)
                    positiveButton(CommonString.dialog_positive_delete) {
                        viewModel.deleteSelectedNoteTypes()
                    }
                    negativeButton(CommonString.dialog_cancel)
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
                                UserMessage.DeletingLastModel -> getString(CommonString.toast_last_model)
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

    private fun applyInsets() {
        // Material 3 supplies the FAB size before layout.
        val fabSize = binding.floatingActionButton.customSize
        val fabBottomMargin = binding.floatingActionButton.marginBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout() or ime())
            Timber.d("Applying insets: $constraints")
            binding.appBarLayout.updatePadding(left = constraints.left, top = constraints.top, right = constraints.right)
            binding.noteTypesList.updatePadding(
                left = constraints.left,
                right = constraints.right,
                // FAB size + its layout margin + a 16dp gap.
                bottom = constraints.bottom + fabSize + fabBottomMargin + 16.dp.toPx(this),
            )
            val fabLayoutParams = binding.floatingActionButton.layoutParams as ViewGroup.MarginLayoutParams
            // also applies the 32dp bottom/end margin to not sit right on top of navigation bar
            fabLayoutParams.updateMarginsRelative(
                bottom = constraints.bottom + fabBottomMargin,
                end = constraints.right + 32.dp.toPx(this),
            )
            // needed otherwise the updated relative margins are not seen
            binding.floatingActionButton.layoutParams = fabLayoutParams
            val selectionToolbarParams = binding.selectionToolbar.layoutParams as ViewGroup.MarginLayoutParams
            // also applies the 32dp bottom margin to not sit right on top of navigation bar
            selectionToolbarParams.updateMargins(bottom = constraints.bottom + 32.dp.toPx(this))
            WindowInsetsCompat.CONSUMED
        }
    }

    @VisibleForTesting
    fun bindState(state: ManageNoteTypesState) {
        if (state.error != null) {
            if (state.error.isReportable) {
                CrashReportService.sendExceptionReport(
                    state.error.source,
                    ManageNotetypes::class.java.simpleName,
                )
            }
            AlertDialog.Builder(this).show {
                message(text = state.error.source.message)
                positiveButton(CommonString.close) { viewModel.refreshNoteTypes() }
            }
            viewModel.clearError()
            return
        }
        // send only the items that should be displayed
        notetypesAdapter.submitList(state.noteTypes.filter { it.shouldBeDisplayed })
        notetypesAdapter.isInMultiSelectMode = state.isInMultiSelectMode
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
                CommonPlurals.note_types_selected,
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

        // keep the same "lifted" background on the app bar while searching
        searchItem.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    binding.appBarLayout.isLiftOnScroll = false
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    binding.appBarLayout.isLiftOnScroll = true
                    return true
                }
            },
        )

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
                        title(CommonString.rename_model)
                        positiveButton(CommonString.rename) {
                            val userInput =
                                (it as AlertDialog)
                                    .getInputField()
                                    .text
                                    .toString()
                                    .trim()
                            if (userInput.isEmpty()) return@positiveButton
                            viewModel.rename(state.id, userInput)
                        }
                        negativeButton(CommonString.dialog_cancel)
                        setView(CommonR.layout.dialog_generic_text_input)
                    }.input(
                        hint = TR.deckConfigNamePrompt(),
                        prefill = state.name,
                        waitForPositiveButton = false,
                        displayKeyboard = true,
                        callback = { dialog, text ->
                            val inputStr = text.toString().trim()

                            val isDuplicate =
                                allNotetypes.any { it.id != state.id && it.name.equals(inputStr, ignoreCase = true) }

                            val isUnchanged = inputStr == state.name

                            if (inputStr.isBlank()) {
                                dialog.getInputTextLayout().error = null
                                dialog.positiveButton.isEnabled = false
                                return@input
                            } else if (isDuplicate && !isUnchanged) {
                                dialog.getInputTextLayout().error = getString(CommonString.error_name_exists)
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
                    CommonString.model_delete_warning
                } else {
                    return@launchCatchingTask
                }
            AlertDialog.Builder(this@ManageNotetypes).show {
                title(CommonString.model_browser_delete)
                message(messageResourceId)
                positiveButton(CommonString.dialog_positive_delete) {
                    viewModel.delete(state.id)
                }
                negativeButton(CommonString.dialog_cancel)
            }
        }
    }
}

class ManageNoteTypesDestination : Destination {
    override fun toIntent(context: Context) = Intent(context, ManageNotetypes::class.java)
}
