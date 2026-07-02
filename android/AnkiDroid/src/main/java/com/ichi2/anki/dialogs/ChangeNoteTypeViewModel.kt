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

import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.notetypes.ChangeNotetypeRequest
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.json.NamedJSONComparator
import com.ichi2.anki.dialogs.ChangeNoteTypeException.Kind.NO_CHANGES
import com.ichi2.anki.libanki.CardTemplates
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.utils.InitStatus
import com.ichi2.anki.utils.ViewModelDelayedInitializer
import com.ichi2.anki.utils.ext.require
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.properties.Delegates.notNull

private typealias TemplateIndex = Int
private typealias FieldIndex = Int

/**
 * Defines how fields in the new note type inherit values from the old note type.
 *
 * Key: field index in the **new** note type
 * Value: field index in the **old** note type to copy content from, or `null` if the field should be empty
 *
 * Example: Converting from a note type with fields `["Front", "Back"]` to `["Question", "Answer", "Extra"]`:
 * ```
 * mapOf(
 *     0 to 0,    // "Question" (new) inherits from "Front" (old)
 *     1 to 1,    // "Answer" (new) inherits from "Back" (old)
 *     2 to null  // "Extra" (new) will be empty
 * )
 * ```
 */
private typealias FieldChangeMap = Map<FieldIndex, FieldIndex?>

/**
 * Defines how templates in the new note type inherit scheduling data from the old note type.
 *
 * Key: template index in the **new** note type
 * Value: template index in the **old** note type to copy scheduling from, or `null` if no scheduling is inherited
 *
 * Example: Converting from a note type with templates `["Card 1", "Card 2"]` to `["Card A"]`:
 * ```
 * mapOf(
 *     0 to 0  // "Card A" (new) inherits scheduling from "Card 1" (old)
 * )
 * // Note: "Card 2" from the old note type will be discarded
 * ```
 */
private typealias TemplateChangeMap = Map<TemplateIndex, TemplateIndex?>

/**
 * [ViewModel] for [ChangeNoteTypeDialog]
 *
 * Supports bulk editing the [Note Type][NotetypeJson] of multiple [notes][com.ichi2.anki.libanki.Note].
 *
 * A user selects a new note type ([setOutputNoteTypeId]), then:
 *
 * For each [Field][com.ichi2.anki.libanki.Fields] in the new note type:
 *   * Select the source field from the current note type, or (nothing) if the field should be blank
 *
 * For each [Card Template][CardTemplates] in the new note type:
 *  * Select the source Card Template to transfer [scheduling information][com.ichi2.anki.libanki.Card] from
 *
 * There are complexities in moving to and from a Cloze Note Type, expressed in [ConversionType]:
 *   * Standard Note Types are `1 -> {0,1}`: A Card Template may or may not generate a card, but
 *     a note type may have many templates
 *   * Cloze Note Types are `1 -> N`: There is One Card Template which can generate multiple cards
 *
 * Changing the type of a note requires a one-way sync.
 */
// Future improvement: make availableNoteTypes responsive to collection updates
class ChangeNoteTypeViewModel(
    private val stateHandle: SavedStateHandle,
) : ViewModel(),
    ViewModelDelayedInitializer {
    /**
     * IDs of notes to be modified
     *
     * non-empty & distinct
     */
    val noteIds: List<NoteId> =
        stateHandle.require<LongArray>(ARG_NOTE_IDS).toList().also {
            require(it.isNotEmpty()) { "$ARG_NOTE_IDS was empty" }
            require(it.distinct().size == it.size) { "$ARG_NOTE_IDS was not distinct" }
        }

    // unchanged after init { }
    // ************************************************

    /** The note type of the notes to be modified */
    var inputNoteType by notNull<NotetypeJson>()
        private set

    /**
     * The note types which a user can provide to [setOutputNoteTypeId]
     */
    lateinit var availableNoteTypes: List<NotetypeJson>

    // UI variables
    // ************************************************

    /** @see Tab */
    var currentTab = Tab.Fields

    // User-modifiable state
    // ************************************************

    @VisibleForTesting
    internal lateinit var fieldChangeMapFlow: MutableStateFlow<FieldChangeMap>

    @VisibleForTesting
    internal lateinit var templateChangeMapFlow: MutableStateFlow<TemplateChangeMap>

    /**
     * A flow emitting the note type selected by the user
     *
     * @see setOutputNoteTypeId
     */
    lateinit var outputNoteTypeFlow: MutableStateFlow<NotetypeJson>

    // Flows
    // ************************************************

    /** Flow to track initialization status */
    override val flowOfInitStatus: MutableStateFlow<InitStatus> = MutableStateFlow(InitStatus.Pending)

    /** Flow which closes the dialog */
    val closeDialogFlow = MutableStateFlow<Unit?>(null)

    // lateinit flows
    // ************************************************

    /** flow that emits whether the current conversion is between cloze and regular note types. */
    val conversionTypeFlow: StateFlow<ConversionType> by lazy {
        outputNoteTypeFlow
            .transform { newNoteType ->
                emit(ConversionType.fromNoteTypeChange(current = inputNoteType, new = newNoteType))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ConversionType.fromNoteTypeChange(current = inputNoteType, new = outputNoteType),
            )
    }

    /**
     * Whether [updateTemplateMapping] can be called
     *
     * Templates may only be updated if the input and output note type are non-cloze
     */
    val canChangeTemplatesFlow by lazy {
        conversionTypeFlow
            .map {
                it == ConversionType.REGULAR_TO_REGULAR
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = !inputNoteType.isCloze,
            )
    }

    /**
     * Whether the current state differs from the initial state (same input note type, default maps).
     * Used to enable/disable the Save button.
     */
    val hasChangesFlow: StateFlow<Boolean> by lazy {
        combine(outputNoteTypeFlow, fieldChangeMapFlow, templateChangeMapFlow) { outputNoteType, fieldMap, templateMap ->
            when {
                outputNoteType.id != inputNoteType.id -> true
                fieldMap != rebuildFieldMap(inputNoteType) -> true
                templateMap != rebuildTemplateMap(inputNoteType) -> true
                else -> false
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
    }

    // Derived Flows
    // ************************************************

    /**
     * A list of the names of the templates which would be discarded
     *
     * e.g. `["Card 1", "Card 3"]`
     */
    val discardedTemplatesFlow by lazy {
        templateChangeMapFlow
            .map { map ->
                val mappedIndices = map.values.toSet()
                inputNoteType.templatesNames.filterIndexed { index, _ -> index !in mappedIndices }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )
    }

    /**
     * A list of the names of the fields which will be discarded
     *
     * e.g. `["Front", "Extra"]`
     */
    val discardedFieldsFlow by lazy {
        fieldChangeMapFlow
            .map { map ->
                val mappedIndices = map.values.toSet()
                inputNoteType.fieldsNames.filterIndexed { index, _ -> index !in mappedIndices }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )
    }

    // Derived state
    // ************************************************

    val fieldChangeMap: FieldChangeMap
        get() = fieldChangeMapFlow.value

    val templateChangeMap: TemplateChangeMap
        get() = templateChangeMapFlow.value

    /** The notes which [executeChangeNoteTypeAsync] will affect */
    val noteCount
        get() = noteIds.size

    override val scope: CoroutineScope
        get() = viewModelScope

    /**
     * The note type which [noteIds] will be converted to
     *
     * @see setOutputNoteTypeId
     */
    val outputNoteType
        get() = outputNoteTypeFlow.value

    init {
        delayedInit {
            inputNoteType = withCol { getNote(noteIds.first()) }.notetype
            availableNoteTypes = withCol { notetypes.all().sortedWith(NamedJSONComparator.INSTANCE) }

            // delayed init of outputNoteType and dependent properties
            val outputNoteType =
                stateHandle.get<NoteTypeId>(STATE_OUTPUT_NOTE_TYPE_ID).let { id ->
                    if (id == null || id == inputNoteType.id) {
                        inputNoteType
                    } else {
                        Timber.d("restoring output note type: %d", id)
                        withCol { notetypes.get(id) } ?: inputNoteType
                    }
                }
            outputNoteTypeFlow = MutableStateFlow(outputNoteType)
            outputNoteTypeFlow
                .onEach { noteType ->
                    stateHandle[STATE_OUTPUT_NOTE_TYPE_ID] = noteType.id
                }.launchIn(viewModelScope)

            // restore template & field mapping if available
            stateHandle.get<FieldChangeMap>(STATE_FIELD_MAP)?.let { fieldMap ->
                fieldChangeMapFlow = stateHandle.getMutableStateFlow(STATE_FIELD_MAP, fieldMap)
            }

            stateHandle.get<TemplateChangeMap>(STATE_TEMPLATE_MAP)?.let { templateMap ->
                templateChangeMapFlow = stateHandle.getMutableStateFlow(STATE_TEMPLATE_MAP, templateMap)
            }

            if (!this::fieldChangeMapFlow.isInitialized || !this::templateChangeMapFlow.isInitialized) {
                Timber.d("initializing maps")
                fieldChangeMapFlow = stateHandle.getMutableStateFlow(STATE_FIELD_MAP, rebuildFieldMap(selectedNoteType = outputNoteType))
                templateChangeMapFlow =
                    stateHandle.getMutableStateFlow(STATE_TEMPLATE_MAP, rebuildTemplateMap(selectedNoteType = outputNoteType))
            } else {
                Timber.d("maps restored from SavedStateHandle")
            }
        }
    }

    /**
     * Performs the [changeNoteTypeOfNotes] operation
     *
     * @return the number of notes affected
     *
     * @throws ChangeNoteTypeException no changes are made
     * @throws ConfirmModSchemaException if a one-way sync dialog needs to be accepted
     */
    @NeedsTest("one way sync")
    @NeedsTest("closeDialogFlow")
    fun executeChangeNoteTypeAsync() =
        viewModelScope.async {
            Timber.d("Changing note type from '%s' to '%s'", inputNoteType.name, outputNoteType.name)
            Timber.d("Field map: %s", fieldChangeMap)
            Timber.d("Card map: %s", templateChangeMap)

            val changes =
                changeNoteTypeOfNotes(
                    noteIds = noteIds,
                    sourceId = inputNoteType.id,
                    targetId = outputNoteType.id,
                    fieldMap = fieldChangeMap,
                    templateMap = templateChangeMap,
                )

            undoableOp { changes }

            closeDialogFlow.emit(Unit)

            return@async noteIds.size
        }

    /**
     * For a given list of Note Ids, change the note type from [sourceId] to [targetId].
     *
     * @param noteIds the IDs of notes to modify
     * @param sourceId the note type ID of the notes being converted
     * @param targetId the note type ID to convert the notes to
     * @param fieldMap see [FieldChangeMap]
     * @param templateMap see [TemplateChangeMap]
     * @throws ChangeNoteTypeException If no changes are made
     */
    // TODO: return the count of changed notes from OpChanges, which may differ from noteIds.size
    @CheckResult
    private suspend fun changeNoteTypeOfNotes(
        noteIds: List<NoteId>,
        sourceId: NoteTypeId,
        targetId: NoteTypeId,
        fieldMap: FieldChangeMap,
        templateMap: TemplateChangeMap,
    ): OpChanges =
        withCol {
            val info = notetypes.changeNotetypeInfo(oldNoteTypeId = sourceId, newNoteTypeId = targetId)

            // The `newFields` and `newTemplates` lists are relative to the new notetype's
            // field/template count.
            //
            // Each value represents the index in the previous notetype.
            // -1 indicates the original value will be discarded.
            val input: ChangeNotetypeRequest =
                info.input.copy {
                    this.noteIds.addAll(noteIds)
                    fieldMap.forEach { (key, value) -> newFields[key] = value ?: -1 }
                    // moving to and from cloze only allows field mappings
                    if (newTemplates.any()) {
                        templateMap.forEach { (key, value) -> newTemplates[key] = value ?: -1 }
                    }
                }

            if (sourceId == targetId &&
                info.input.newFieldsList == input.newFieldsList &&
                info.input.newTemplatesList == input.newTemplatesList
            ) {
                Timber.i("change note types: no changes to save")
                throw ChangeNoteTypeException(NO_CHANGES, "No changes to save")
            }

            return@withCol notetypes.changeNotetypeOfNotes(input)
        }

    /**
     * Maps content from an input field to an output field during a note-type change.
     *
     * During conversion, each field in the new note type must specify which field in the
     * old note type its value should be copied from (or whether it should remain empty).
     *
     * Example:
     * ```
     * oldNoteTypeFields = ["A0", "A1", "A2"]
     * newNoteTypeFields = ["B0", "B1", "B2"]
     *
     * // The following means field "B1" of the note after conversion will inherit
     * // its value from field "A2" of the note before conversion.
     * updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(2))
     * ```
     */
    fun updateFieldMapping(
        outputFieldIndex: Int,
        mappedFrom: SelectedIndex,
    ) = viewModelScope.launch {
        Timber.d("Updating field mapping: '%d' -> '%s'", outputFieldIndex, mappedFrom)
        fieldChangeMapFlow.value =
            fieldChangeMap
                .toMutableMap()
                .apply { this[outputFieldIndex] = mappedFrom.toNullableInt() }
    }

    /**
     * Maps an input template to an output template during a note-type change.
     *
     * During conversion, each template in the new note type must specify which template in the
     * old note type its content should be copied from (or whether it should remain empty).
     *
     * Example:
     * ```
     * oldNoteTypeTemplates = ["Card 0", "Card 1", "Card 2"]
     * newNoteTypeTemplates = ["Card A", "Card B", "Card C"]
     *
     * // The following means template "Card B" of the note after conversion will inherit
     * // its content from "Card 2" of the note before conversion.
     * updateTemplateMapping(outputTemplateIndex = 1, mappedFrom = SelectedIndex.from(2))
     * ```
     */
    fun updateTemplateMapping(
        outputTemplateIndex: Int,
        mappedFrom: SelectedIndex,
    ) = viewModelScope.launch {
        require(canChangeTemplatesFlow.value) { "changing templates was disabled" }

        Timber.d("Updating card mapping: %d -> %s", outputTemplateIndex, mappedFrom)
        val updatedValue = mappedFrom.toNullableInt()

        val updatedMap = templateChangeMap.toMutableMap()
        // a card can only be mapped once, change all 'other' values to null
        if (updatedValue != null) {
            val keysToMapToNothing = updatedMap.filterValues { it == updatedValue }.keys
            assert(keysToMapToNothing.size <= 1) { "a card was mapped multiple times" }
            keysToMapToNothing.forEach { updatedMap[it] = null }
        }

        updatedMap[outputTemplateIndex] = updatedValue

        templateChangeMapFlow.value = updatedMap
    }

    /**
     * Updates the selected note type, resetting the mappings of templates and fields
     *
     * @param id The id of the selected note type
     * @throws IllegalArgumentException if [id] is not found
     */
    fun setOutputNoteTypeId(id: NoteTypeId) =
        viewModelScope.async {
            val newNoteType = requireNotNull(availableNoteTypes.find { it.id == id }) { "note type $id not found" }
            Timber.i("updating selected note type to ${newNoteType.id}")
            outputNoteTypeFlow.value = newNoteType
            // Initialize maps immediately after note type selection
            fieldChangeMapFlow.value = rebuildFieldMap(newNoteType)
            templateChangeMapFlow.value = rebuildTemplateMap(newNoteType)
        }

    /**
     * Selects a tab by its position
     *
     * @param position the position of the tab to select
     */
    fun selectTabByPosition(position: Int) {
        val selectedTab = Tab.entries.first { it.position == position }
        Timber.i("Tab selected: %s", selectedTab.name)
        currentTab = selectedTab
    }

    private fun rebuildFieldMap(selectedNoteType: NotetypeJson): Map<Int, Int?> {
        val inputFields = inputNoteType.fields
        val outputFields = selectedNoteType.fields

        // initially match ords by name
        val currentOrdMapping =
            outputFields
                .mapNotNull { inputField ->
                    inputFields
                        .find { it.name == inputField.name }
                        ?.let { selectedField -> inputField.ord to selectedField.ord as Int? }
                }.toMap(HashMap())

        // then fill in any unmapped ords
        val unmappedOrds = (0 until outputFields.size).filter { ord -> ord !in currentOrdMapping }
        for (unmappedOrd in unmappedOrds) {
            currentOrdMapping[unmappedOrd] =
                when {
                    // we've exhausted all the input ords
                    currentOrdMapping.size >= inputFields.size -> null
                    // if [ord] -> [ord] is unused, map it
                    !currentOrdMapping.containsValue(unmappedOrd) -> unmappedOrd
                    // otherwise, pick the first unmapped input field
                    else -> (0 until inputFields.size).firstOrNull { !currentOrdMapping.containsValue(it) }
                }
        }
        return currentOrdMapping
    }

    /**
     * Rebuilds the template mapping by ordinal position.
     *
     * Maps each output template to the input template at the same index (0 → 0, 1 → 1, etc.).
     * Extra output templates are mapped to `null`.
     */
    private fun rebuildTemplateMap(selectedNoteType: NotetypeJson): Map<Int, Int?> {
        val inputTemplates = inputNoteType.templates
        val outputTemplates = selectedNoteType.templates
        return outputTemplates.indices
            .associateWith { idx ->
                if (idx < inputTemplates.size) idx else null
            }
    }

    enum class Tab(
        val position: Int,
    ) {
        Fields(0),
        Templates(1),
    }

    companion object {
        const val ARG_NOTE_IDS = "ARG_NOTE_IDS"
        const val STATE_FIELD_MAP = "fieldMap"
        const val STATE_TEMPLATE_MAP = "templateMap"
        const val STATE_OUTPUT_NOTE_TYPE_ID = "outputNoteType"
    }
}

/** How the regular/cloze status of a note type is affected */
// TODO: we may want to consider image occlusion separately
enum class ConversionType {
    REGULAR_TO_REGULAR,
    REGULAR_TO_CLOZE,
    CLOZE_TO_REGULAR,
    CLOZE_TO_CLOZE,
    ;

    companion object {
        fun fromNoteTypeChange(
            current: NotetypeJson,
            new: NotetypeJson,
        ): ConversionType {
            val isCurrent = current.isCloze
            val isNew = new.isCloze
            return when {
                isCurrent && isNew -> CLOZE_TO_CLOZE
                isCurrent && !isNew -> CLOZE_TO_REGULAR
                !isCurrent && isNew -> REGULAR_TO_CLOZE
                else -> REGULAR_TO_REGULAR
            }
        }
    }
}

/**
 * A user selection when mapping fields or templates when changing note type
 * either an [index][Index], or [(nothing)][NOTHING]
 */
sealed interface SelectedIndex {
    /** The field/template will be discarded in the new note type */
    data object NOTHING : SelectedIndex

    /** The field/template will be mapped to [index] in the new new note type */
    data class Index(
        val index: Int,
    ) : SelectedIndex

    /** Converts the structure to a nullable int, which is easier to serialize */
    fun toNullableInt(): Int? =
        when (this) {
            is NOTHING -> null
            is Index -> this.index
        }

    companion object {
        fun from(index: Int): SelectedIndex = Index(index)
    }
}

/** An expected exception when changing note types */
class ChangeNoteTypeException(
    val kind: Kind,
    message: String,
) : IllegalStateException(message) {
    enum class Kind {
        NO_CHANGES,
    }
}
