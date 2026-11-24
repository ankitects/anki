// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.collection.OpChangesWithId
import anki.notetypes.Notetype
import anki.notetypes.StockNotetype
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.exception.CombinedException
import com.ichi2.anki.exception.ReportableException
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.addNotetype
import com.ichi2.anki.libanki.addNotetypeLegacy
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.getNotetypeNameIdUseCount
import com.ichi2.anki.libanki.getNotetypeNames
import com.ichi2.anki.libanki.getStockNotetype
import com.ichi2.anki.libanki.removeNotetype
import com.ichi2.anki.libanki.updateNotetype
import com.ichi2.anki.notetype.ManageNoteTypesState.CardEditor
import com.ichi2.anki.notetype.ManageNoteTypesState.FieldsEditor
import com.ichi2.anki.notetype.ManageNoteTypesState.UserMessage
import com.ichi2.anki.notetype.NoteTypeItemState.Companion.asModel
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

class ManageNoteTypesViewModel :
    ViewModel(),
    HasProgress<Nothing> {
    override val progressManager = ProgressManager<Nothing>()

    val state: StateFlow<ManageNoteTypesState>
        field = MutableStateFlow(ManageNoteTypesState())

    /** An add or clone that is still running, so a recreated screen can still show its errors */
    var pendingAdd: Deferred<Unit>? = null
        private set

    init {
        refreshNoteTypes()
    }

    fun refreshNoteTypes() {
        Timber.i("Refreshing list of notetypes")
        viewModelScope.launch {
            progressManager.withProgress { loadNoteTypes() }
        }
    }

    internal suspend fun loadAddNoteTypeOptions(): Pair<List<AddNotetypeUiModel>, List<String>> =
        progressManager.withProgress {
            withCol {
                val standardNotetypesModels =
                    StockNotetype.Kind.entries
                        .filter { it != StockNotetype.Kind.UNRECOGNIZED }
                        .map {
                            AddNotetypeUiModel(
                                id = it.number.toLong(),
                                name = getStockNotetype(it).name,
                                isStandard = true,
                            )
                        }
                val foundNotetypes = getNotetypeNames()
                Pair(
                    mutableListOf<AddNotetypeUiModel>().apply {
                        addAll(standardNotetypesModels)
                        addAll(foundNotetypes.map { it.toUiModel() })
                    },
                    foundNotetypes.map { it.name },
                )
            }
        }

    fun addStockNoteType(
        newName: String,
        kind: StockNotetype.Kind,
    ): Deferred<Unit> {
        Timber.i("Adding notetype from stock kind $kind")
        return addNoteType {
            val stockNoteType = getStockNotetype(kind).apply { name = newName }
            addNotetypeLegacy(BackendUtils.toJsonBytes(stockNoteType))
        }
    }

    fun cloneNoteType(
        newName: String,
        ntid: NoteTypeId,
    ): Deferred<Unit> {
        Timber.i("Cloning notetype with id $ntid")
        return addNoteType {
            val newNoteType =
                getNotetype(ntid).copy {
                    id = 0
                    name = newName
                }
            addNotetype(newNoteType)
        }
    }

    private fun addNoteType(add: Collection.() -> OpChangesWithId): Deferred<Unit> {
        val added =
            viewModelScope.async {
                progressManager.withProgress {
                    withCol { add() }
                    loadNoteTypes()
                }
            }
        pendingAdd = added
        added.invokeOnCompletion { if (pendingAdd === added) pendingAdd = null }
        return added
    }

    private suspend fun loadNoteTypes() {
        withCol { safeGetNotetypeNameIdUseCount() }
            .onFailure {
                state.update { oldState ->
                    oldState.copy(error = ReportableException(it))
                }
            }.onSuccess {
                state.update { oldState ->
                    oldState.copy(noteTypes = it)
                }
            }
    }

    fun filter(query: String) {
        Timber.i("Filtering list of notetypes with query=$query")
        state.update { oldState ->
            val matchedNoteTypes =
                oldState.noteTypes.map {
                    it.copy(shouldBeDisplayed = it.name.contains(query, ignoreCase = true))
                }
            oldState.copy(noteTypes = matchedNoteTypes, searchQuery = query)
        }
    }

    fun rename(
        ntid: NoteTypeId,
        name: String,
    ) {
        Timber.i("Renaming notetype with id $ntid")
        viewModelScope.launch {
            progressManager.withProgress {
                undoableOp<OpChanges> {
                    safeRenameNoteType(ntid, name)
                        .onSuccess { changes ->
                            state.update { oldState ->
                                val updatedNoteTypes =
                                    oldState.noteTypes.withUpdatedItem(ntid) { old -> old.copy(name = name) }
                                oldState.copy(noteTypes = updatedNoteTypes)
                            }
                            return@undoableOp changes
                        }.onFailure {
                            // TODO: Change to CardTypeException: https://github.com/ankidroid/Anki-Android-Backend/issues/537
                            // Card template 1 in note type 'character' has a problem.
                            // Expected to find a field replacement on the front of the card template.
                            state.update { oldState ->
                                oldState.copy(
                                    error = ReportableException(it, it !is BackendException),
                                )
                            }
                            OpChanges.getDefaultInstance()
                        }
                    OpChanges.getDefaultInstance()
                }
            }
        }
    }

    /** Deletes the note type with [ntid] and also updates the multi select mode status if needed */
    fun delete(ntid: NoteTypeId) {
        Timber.i("Deleting notetype with id $ntid")
        if (state.value.noteTypes.size <= 1) {
            state.update { oldState -> oldState.copy(message = UserMessage.DeletingLastModel) }
            return
        }
        viewModelScope.launch {
            progressManager.withProgress {
                undoableOp<OpChanges> {
                    safeRemoveNoteType(ntid)
                        .onSuccess { changes ->
                            state.update { oldState ->
                                val updatedNoteTypes = oldState.noteTypes.filterNot { it.id == ntid }
                                oldState.copy(
                                    noteTypes = updatedNoteTypes,
                                    isInMultiSelectMode = updatedNoteTypes.multiSelectModeStatus,
                                )
                            }
                            return@undoableOp changes
                        }.onFailure {
                            state.update { oldState ->
                                oldState.copy(error = ReportableException(it))
                            }
                            return@undoableOp OpChanges.getDefaultInstance()
                        }
                    OpChanges.getDefaultInstance()
                }
            }
        }
    }

    fun onItemClick(entry: NoteTypeItemState) {
        if (state.value.isInMultiSelectMode) {
            Timber.i("onItemClick: already in multiple selection mode, toggling selection for notetype with id: ${entry.id} ")
            state.update { oldState ->
                val updatedNoteTypes =
                    oldState.noteTypes.withUpdatedItem(entry.id) { noteType ->
                        noteType.copy(isSelected = !noteType.isSelected)
                    }
                oldState.copy(
                    noteTypes = updatedNoteTypes,
                    isInMultiSelectMode = updatedNoteTypes.multiSelectModeStatus,
                )
            }
        } else {
            Timber.i("onItemClick: not in multiple selection mode, sending show fields editor request")
            state.update { oldState ->
                oldState.copy(destination = FieldsEditor(entry.id, entry.name))
            }
        }
    }

    fun onItemLongClick(entry: NoteTypeItemState) {
        if (state.value.isInMultiSelectMode) {
            Timber.i("onItemLongClick: already in multiple selection mode, toggling selection for notetype with id: ${entry.id} ")
            state.update { oldState ->
                val updatedNoteTypes =
                    oldState.noteTypes.withUpdatedItem(entry.id) { noteType ->
                        noteType.copy(isSelected = !noteType.isSelected)
                    }
                oldState.copy(
                    noteTypes = updatedNoteTypes,
                    isInMultiSelectMode = updatedNoteTypes.multiSelectModeStatus,
                )
            }
        } else {
            Timber.i("onItemLongClick: no previous selection, starting multi select mode with notetype(${entry.id}) selected")
            state.update { oldState ->
                val updatedNoteTypes =
                    oldState.noteTypes.withUpdatedItem(entry.id) { noteType ->
                        noteType.copy(isSelected = true)
                    }
                oldState.copy(noteTypes = updatedNoteTypes, isInMultiSelectMode = true)
            }
        }
    }

    /** Updates the check status for a selected note type also updates the multi select mode status if needed */
    fun onItemChecked(
        entry: NoteTypeItemState,
        isChecked: Boolean,
    ) {
        Timber.i("onItemCheck: update selection for notetype(${entry.id}) with new status: $isChecked")
        state.update { oldState ->
            val updatedNoteTypes =
                state.value.noteTypes.withUpdatedItem(entry.id) { old -> old.copy(isSelected = isChecked) }
            oldState.copy(
                noteTypes = updatedNoteTypes,
                isInMultiSelectMode = updatedNoteTypes.multiSelectModeStatus,
            )
        }
    }

    /** Clears any selected note types and also exits the multi select mode */
    fun clearSelection() {
        Timber.i("Clearing selected notetypes")
        state.update { oldState ->
            val updatedNoteTypes =
                oldState.noteTypes.map { noteTypeItemState ->
                    noteTypeItemState.copy(isSelected = false)
                }
            oldState.copy(
                noteTypes = updatedNoteTypes,
                isInMultiSelectMode = updatedNoteTypes.multiSelectModeStatus,
            )
        }
    }

    /**
     * Deletes all the [NoteTypeItemState] which are currently selected. Any errors when deleting
     * the [Notetype]s will be combined into a single [CombinedException] to present to the user.
     */
    fun deleteSelectedNoteTypes() {
        val noteTypesToDelete = selectedNoteTypes.toMutableList()
        Timber.i("Deleting currently selected notetypes: ${noteTypesToDelete.map { it.id }}")
        // show loading and clear selection
        state.update { oldState ->
            val updateNotetypes =
                oldState.noteTypes.map { noteType ->
                    noteType.copy(isSelected = false)
                }
            oldState.copy(
                noteTypes = updateNotetypes,
                isInMultiSelectMode = false,
            )
        }
        viewModelScope.launch {
            progressManager.withProgress {
                val errors = mutableMapOf<NoteTypeItemState, Throwable>()
                noteTypesToDelete.forEach { noteType ->
                    undoableOp<OpChanges> {
                        safeRemoveNoteType(noteType.id)
                            .onFailure { exception ->
                                errors[noteType] = exception
                                OpChanges.getDefaultInstance()
                            }.onSuccess { return@undoableOp it }
                        OpChanges.getDefaultInstance()
                    }
                }
                // look through any errors we might have and remove from our list of note types the ones
                // that were in noteTypesToDelete but not in errors map(which presumably weren't deleted)
                val removedIds = noteTypesToDelete.map { it.id } - errors.keys.map { it.id }.toSet()
                val updatedNoteTypes = state.value.noteTypes.filterNot { it.id in removedIds }
                val combinedException =
                    CombinedException.from(
                        errors.map { (state, throwable) ->
                            "${state.name} - $throwable" to throwable
                        },
                    )
                state.update { oldState ->
                    oldState.copy(
                        noteTypes = updatedNoteTypes,
                        error = combinedException?.let { throwable -> ReportableException(throwable) },
                    )
                }
            }
        }
    }

    fun onCardEditorRequested(entry: NoteTypeItemState) {
        Timber.i("Sending open card editor request")
        state.update { oldState ->
            oldState.copy(destination = CardEditor(entry.id))
        }
    }

    /** Clears any previous user messages from the state */
    fun clearMessage() {
        Timber.i("Clearing user message from state")
        state.update { oldState -> oldState.copy(message = null) }
    }

    /** Clears any previous errors from the state */
    fun clearError() {
        Timber.i("Clearing errors from state")
        state.update { oldState -> oldState.copy(error = null) }
    }

    /** Clears any previous destinations requested by the user from the state */
    fun clearDestination() {
        Timber.i("Clearing requested destinations from state")
        state.update { oldState -> oldState.copy(destination = null) }
    }

    /**
     * Returns a new list where all items are unchanged with the exception of the [NoteTypeItemState]
     * identified by [nid] which is replaced by the result of invoking the [update] lambda.
     */
    private fun List<NoteTypeItemState>.withUpdatedItem(
        nid: NoteTypeId,
        update: (NoteTypeItemState) -> NoteTypeItemState,
    ): List<NoteTypeItemState> = map { noteType -> if (noteType.id == nid) update(noteType) else noteType }

    /** True if we are selecting multiple items(implies at least one item is currently selected), false otherwise. */
    @VisibleForTesting(otherwise = PRIVATE)
    val List<NoteTypeItemState>.multiSelectModeStatus: Boolean
        get() = any { it.isSelected }
}

/** The list of [NoteTypeItemState] that are currently selected */
val ManageNoteTypesViewModel.selectedNoteTypes: List<NoteTypeItemState>
    get() = state.value.noteTypes.filter { it.isSelected }

private fun Collection.safeRenameNoteType(
    ntid: NoteTypeId,
    newName: String,
): Result<OpChanges> =
    try {
        val currentNoteType: Notetype = getNotetype(ntid)
        val renamedNotetype = currentNoteType.copy { this.name = newName }
        Result.success(updateNotetype(renamedNotetype))
    } catch (exception: Exception) {
        Result.failure(exception)
    }

private fun Collection.safeRemoveNoteType(ntid: NoteTypeId): Result<OpChanges> =
    try {
        Result.success(removeNotetype(ntid))
    } catch (exception: Exception) {
        Result.failure(exception)
    }

private fun Collection.safeGetNotetypeNameIdUseCount(): Result<List<NoteTypeItemState>> =
    try {
        Result.success(getNotetypeNameIdUseCount().map(::asModel))
    } catch (exception: Exception) {
        Result.failure(exception)
    }
