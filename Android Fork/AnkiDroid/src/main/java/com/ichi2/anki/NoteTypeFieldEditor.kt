// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Ryan Annis <squeenix@live.ca>
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.databinding.ActivityNoteTypeFieldEditorBinding
import com.ichi2.anki.databinding.ItemNotetypeFieldBinding
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.REQUEST_HINT_LOCALE_SELECTION
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.RESULT_SELECTED_LOCALE
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.Companion.newInstance
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import com.ichi2.anki.dialogs.registerConfirmationHandler
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Fields
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.servicelayer.LanguageHintService.setLanguageHintForField
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.getLongOrNull
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.anki.utils.ext.setCompoundDrawablesRelativeWithIntrinsicBoundsKt
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.customView
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.util.Locale
import com.ichi2.anki.common.android.R as CommonR

@NeedsTest("perform one action, then another")
class NoteTypeFieldEditor : AnkiActivity(R.layout.activity_note_type_field_editor) {
    private val binding by viewBinding(ActivityNoteTypeFieldEditorBinding::bind)

    // Position of the current field selected
    private val currentPos: Int
        get() {
            val name = selectedFieldName ?: return 0
            val index = fieldsLabels.indexOf(name)
            val id = selectedFieldId
            return if (index == -1 || id == null || noteFields[index].id == id) index else -1
        }
    private var selectedFieldName: String? = null
    private var selectedFieldId: Long? = null
    private var pendingInput: Bundle? = null
    private var fieldNameInput: EditText? = null

    // Backing field for [notetype]. Not with _ because it's only allowed for public field.
    private var notetypeBackup: NotetypeJson? = null
    private var notetype: NotetypeJson
        get() = notetypeBackup!!
        set(value) {
            notetypeBackup = value
        }

    private lateinit var fieldsLabels: List<String>

    // WARN: this should be lateinit, but this can't yet be done on an inline class
    private var noteFields: Fields = Fields(JSONArray())

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStorageIsReady()) {
            return
        }
        savedInstanceState?.let {
            selectedFieldName = it.getString(STATE_SELECTED_FIELD)
            selectedFieldId = it.getLongOrNull(STATE_SELECTED_FIELD_ID)
            pendingInput = it.getBundle(STATE_PENDING_INPUT)
        }
        setContentView(R.layout.activity_note_type_field_editor)
        enableToolbar()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout() or ime())
            binding.toolbarContainer.updatePadding(left = constraints.left, right = constraints.right, top = constraints.top)
            binding.btnAdd.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = constraints.bottom + 32.dp.toPx(this@NoteTypeFieldEditor)
                rightMargin = constraints.right + 32.dp.toPx(this@NoteTypeFieldEditor)
            }
            binding.fields.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = constraints.left
                rightMargin = constraints.right
                bottomMargin = constraints.bottom
            }
            WindowInsetsCompat.CONSUMED
        }
        binding.notetypeName.text = intent.getStringExtra(EXTRA_NOTETYPE_NAME)
        startLoadingCollection()
        setFragmentResultListener(REQUEST_HINT_LOCALE_SELECTION, ::onDialogInput)
        for (requestKey in listOf(REQUEST_ADD_FIELD, REQUEST_DELETE_FIELD, REQUEST_REPOSITION_FIELD, REQUEST_SORT_FIELD)) {
            registerConfirmationHandler(requestKey) { result -> onDialogInput(requestKey, result) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SELECTED_FIELD, selectedFieldName)
        selectedFieldId?.let { outState.putLong(STATE_SELECTED_FIELD_ID, it) }
        outState.putBundle(STATE_PENDING_INPUT, pendingInput)
        super.onSaveInstanceState(outState)
    }

    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        initialize()
        if (notetypeBackup == null) return
        val pending = pendingInput ?: return
        lifecycleScope.launch {
            withStarted {
                pendingInput = null
                applyDialogInput(pending.requireString(PENDING_KEY), pending.getBundle(PENDING_VALUE) ?: Bundle())
            }
        }
    }

    private fun onDialogInput(
        key: String,
        result: Bundle,
    ) {
        if (notetypeBackup == null) {
            if (pendingInput != null) {
                Timber.i("Ignoring %s while an earlier input waits for the collection", key)
                return
            }
            pendingInput =
                Bundle().apply {
                    putString(PENDING_KEY, key)
                    putBundle(PENDING_VALUE, result)
                }
        } else {
            applyDialogInput(key, result)
        }
    }

    private fun applyDialogInput(
        key: String,
        result: Bundle,
    ) {
        if (key != REQUEST_ADD_FIELD && currentPos == -1) {
            Timber.w("The chosen field is no longer in the note type")
            dismissAllDialogFragments()
            return
        }
        when (key) {
            INPUT_CONTEXT_MENU_ACTION -> performAction(NoteTypeFieldEditorContextMenuAction.valueOf(result.requireString(INPUT_ACTION)))
            REQUEST_HINT_LOCALE_SELECTION -> {
                val selectedLocale =
                    BundleCompat.getSerializable(
                        result,
                        RESULT_SELECTED_LOCALE,
                        Locale::class.java,
                    )
                if (selectedLocale != null) {
                    addFieldLocaleHint(selectedLocale)
                }
                dismissAllDialogFragments()
            }
            REQUEST_ADD_FIELD -> {
                val fieldName = result.getString(RESULT_FIELD_NAME) ?: return
                if (fieldName in fieldsLabels) {
                    showThemedToast(this, resources.getString(CommonString.toast_duplicate_field), true)
                    return
                }
                try {
                    addField(fieldName, false)
                } catch (e: ConfirmModSchemaException) {
                    e.log()
                    // This should never be thrown
                }
            }
            REQUEST_DELETE_FIELD -> {
                if (fieldsLabels.size < 2) {
                    showThemedToast(this, resources.getString(CommonString.toast_last_field), true)
                    dismissAllDialogFragments()
                    return
                }
                getColUnsafe.modSchema(check = false)
                deleteField()

                // This ensures that the context menu closes after the field has been deleted
                supportFragmentManager.popBackStackImmediate(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE,
                )
            }
            REQUEST_REPOSITION_FIELD ->
                try {
                    getColUnsafe.modSchema(check = false)
                    repositionField(result.getInt(RESULT_FIELD_INDEX))
                } catch (e: JSONException) {
                    throw RuntimeException(e)
                }
            REQUEST_SORT_FIELD -> {
                getColUnsafe.modSchema(check = false)
                launchCatchingTask { changeSortField(notetype, currentPos) }
            }
        }
    }

    // ----------------------------------------------------------------------------
    // UI SETUP
    // ----------------------------------------------------------------------------

    /**
     * Initialize the data holding properties and the UI from the model. This method expects that it
     * isn't followed by other type of work that access the data properties as it has the capability
     * to finish the activity.
     */
    private fun initialize() {
        val noteTypeID = intent.getLongExtra(EXTRA_NOTETYPE_ID, 0)
        val collectionModel = getColUnsafe.notetypes.get(noteTypeID)
        if (collectionModel == null) {
            showThemedToast(this, CommonString.field_editor_model_not_available, true)
            finish()
            return
        }
        notetype = collectionModel
        noteFields = notetype.fields
        fieldsLabels = notetype.fieldsNames
        binding.fields.adapter = NoteFieldAdapter(this, fieldNamesWithKind())
        binding.fields.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position: Int, _ ->
                showDialogFragment(newInstance(fieldsLabels[position]))
                selectedFieldName = fieldsLabels[position]
                selectedFieldId = noteFields[position].id
            }
        binding.btnAdd.contentDescription = TR.sentenceCase.addField
        binding.btnAdd.setOnClickListener { addFieldDialog() }
    }
    // ----------------------------------------------------------------------------
    // CONTEXT MENU DIALOGUES
    // ----------------------------------------------------------------------------

    /**
     * Clean the input field or explain why it's rejected
     * @param fieldNameInput Editor to get the input
     * @return The value to use, or null in case of failure
     */
    private fun uniqueName(fieldNameInput: EditText): String? {
        var input =
            fieldNameInput.text
                .toString()
                .replace("[\\n\\r{}:\"]".toRegex(), "")
        // The number of #, ^, /, space, tab, starting the input
        var offset = 0
        while (offset < input.length) {
            if (!listOf('#', '^', '/', ' ', '\t').contains(input[offset])) {
                break
            }
            offset++
        }
        input = input.substring(offset).trim()
        if (input.isEmpty()) {
            showThemedToast(this, resources.getString(CommonString.toast_empty_name), true)
            return null
        }
        if (fieldsLabels.any { input == it }) {
            showThemedToast(this, resources.getString(CommonString.toast_duplicate_field), true)
            return null
        }
        return input
    }

    /*
     * Creates a dialog to create a field
     */
    private fun addFieldDialog() {
        fieldNameInput =
            FixedEditText(this).apply {
                focusWithKeyboard()
            }
        fieldNameInput?.let { fieldNameInput ->
            fieldNameInput.isSingleLine = true
            AlertDialog.Builder(this).show {
                customView(view = fieldNameInput, paddingStart = 64, paddingEnd = 64, paddingTop = 32)
                title(text = TR.sentenceCase.addField)
                positiveButton(CommonString.menu_add) {
                    // Name is valid, now field is added
                    val fieldName = uniqueName(fieldNameInput)
                    try {
                        addField(fieldName, true)
                    } catch (e: ConfirmModSchemaException) {
                        e.log()

                        // Create dialogue to for schema change
                        this@NoteTypeFieldEditor.showDialogFragment(
                            ConfirmationDialog.newFullSyncInstance(
                                context = this@NoteTypeFieldEditor,
                                requestKey = REQUEST_ADD_FIELD,
                                result = Bundle().apply { putString(RESULT_FIELD_NAME, fieldName) },
                            ),
                        )
                    }
                    getColUnsafe.notetypes.update(notetype)
                    initialize()
                }
                negativeButton(CommonString.dialog_cancel)
            }
        }
    }

    /**
     * Adds a field with the given name
     */
    @Throws(ConfirmModSchemaException::class)
    private fun addField(
        fieldName: String?,
        modSchemaCheck: Boolean,
    ) {
        fieldName ?: return
        // Name is valid, now field is added
        if (modSchemaCheck) {
            getColUnsafe.modSchema(check = true)
        } else {
            getColUnsafe.modSchema(check = false)
        }
        launchCatchingTask {
            Timber.d("doInBackgroundAddField")
            withProgress {
                withCol {
                    notetypes.addFieldModChanged(notetype, notetypes.newField(fieldName))
                }
            }
            initialize()
        }
    }

    /*
     * Creates a dialog to delete the currently selected field
     */
    private fun deleteFieldDialog() {
        if (fieldsLabels.size < 2) {
            showThemedToast(this, resources.getString(CommonString.toast_last_field), true)
        } else {
            try {
                getColUnsafe.modSchema(check = true)
                val fieldName = noteFields[currentPos].name
                showDialogFragment(
                    ConfirmationDialog.newInstance(
                        requestKey = REQUEST_DELETE_FIELD,
                        title = fieldName,
                        message = resources.getString(CommonString.field_delete_warning),
                        positiveButtonText = getString(CommonString.dialog_positive_delete),
                    ),
                )
            } catch (e: ConfirmModSchemaException) {
                e.log()
                showDialogFragment(ConfirmationDialog.newFullSyncInstance(this, REQUEST_DELETE_FIELD))
            }
        }
    }

    private fun deleteField() {
        launchCatchingTask {
            Timber.d("doInBackGroundDeleteField")
            withProgress(message = getString(CommonString.model_field_editor_changing)) {
                val result =
                    withCol {
                        try {
                            notetypes.remFieldLegacy(notetype, noteFields[currentPos])
                            true
                        } catch (e: ConfirmModSchemaException) {
                            // Should never be reached
                            e.log()
                            false
                        }
                    }
                if (!result) {
                    closeActivity()
                }
                initialize()
            }
        }
    }

    /*
     * Creates a dialog to rename the currently selected field
     * Processing time is constant
     */
    private fun renameFieldDialog() {
        fieldNameInput = FixedEditText(this).apply { focusWithKeyboard() }
        fieldNameInput?.let { fieldNameInput ->
            fieldNameInput.isSingleLine = true
            fieldNameInput.setText(fieldsLabels[currentPos])
            fieldNameInput.moveCursorToEnd()
            AlertDialog.Builder(this).show {
                customView(view = fieldNameInput, paddingStart = 64, paddingEnd = 64, paddingTop = 32)
                title(CommonString.model_field_editor_rename)
                positiveButton(CommonString.rename) {
                    if (uniqueName(fieldNameInput) == null) {
                        return@positiveButton
                    }
                    // Field is valid, now rename
                    try {
                        renameField()
                    } catch (e: ConfirmModSchemaException) {
                        e.log()

                        // Handler mod schema confirmation
                        val c = ConfirmationDialog()
                        c.setArgs(resources.getString(CommonString.full_sync_confirmation))
                        val confirm =
                            Runnable {
                                getColUnsafe.modSchema(check = false)
                                try {
                                    renameField()
                                } catch (e1: ConfirmModSchemaException) {
                                    e1.log()
                                    // This should never be thrown
                                }
                            }
                        c.setConfirm(confirm)
                        this@NoteTypeFieldEditor.showDialogFragment(c)
                    }
                }
                negativeButton(CommonString.dialog_cancel)
            }
        }
    }

    /**
     * Displays a dialog to allow the user to reposition a field within a list.
     */
    private fun repositionFieldDialog() {
        /**
         * Shows an input dialog for selecting a new position.
         *
         * @param numberOfTemplates The total number of available positions.
         * @param result A lambda function that receives the validated new position as an integer.
         */
        fun showDialog(
            numberOfTemplates: Int,
            result: (Int) -> Unit,
        ) {
            AlertDialog
                .Builder(this)
                .show {
                    title(CommonString.model_field_editor_reposition_menu)
                    positiveButton(text = TR.actionsReposition()) {
                        val input = (it as AlertDialog).getInputField()
                        result(input.text.toString().toInt())
                    }
                    negativeButton(CommonString.dialog_cancel)
                    setMessage(TR.fieldsNewPosition1(numberOfTemplates))
                    setView(CommonR.layout.dialog_generic_text_input)
                }.input(
                    prefill = (currentPos + 1).toString(),
                    inputType = InputType.TYPE_CLASS_NUMBER,
                    displayKeyboard = true,
                    waitForPositiveButton = false,
                ) { dialog, text: CharSequence ->
                    val number = text.toString().toIntOrNull()
                    dialog.positiveButton.isEnabled = number != null && number in 1..numberOfTemplates
                }
        }

        // handle repositioning
        showDialog(fieldsLabels.size) { newPosition ->
            if (newPosition == currentPos + 1) return@showDialog

            Timber.i("Repositioning field from %d to %d", currentPos, newPosition)
            try {
                getColUnsafe.modSchema(check = true)
                repositionField(newPosition - 1)
            } catch (e: ConfirmModSchemaException) {
                e.log()

                // Handle mod schema confirmation
                this@NoteTypeFieldEditor.showDialogFragment(
                    ConfirmationDialog.newFullSyncInstance(
                        context = this@NoteTypeFieldEditor,
                        requestKey = REQUEST_REPOSITION_FIELD,
                        result = Bundle().apply { putInt(RESULT_FIELD_INDEX, newPosition - 1) },
                    ),
                )
            }
        }
    }

    private fun repositionField(index: Int) {
        launchCatchingTask {
            withProgress(message = getString(CommonString.model_field_editor_changing)) {
                val result =
                    withCol {
                        Timber.d("doInBackgroundRepositionField")
                        try {
                            notetypes.moveFieldLegacy(notetype, noteFields[currentPos], index)
                            true
                        } catch (e: ConfirmModSchemaException) {
                            e.log()
                            // Should never be reached
                            false
                        }
                    }
                if (!result) {
                    closeActivity()
                }
                initialize()
            }
        }
    }

    /*
     * Renames the current field
     */
    @Throws(ConfirmModSchemaException::class)
    private fun renameField() {
        val fieldLabel =
            fieldNameInput!!
                .text
                .toString()
                .replace("[\\n\\r]".toRegex(), "")
        val field = noteFields[currentPos]
        getColUnsafe.notetypes.renameFieldLegacy(notetype, field, fieldLabel)
        initialize()
    }

    /*
     * Changes the sort field (that displays in card browser) to the current field
     */
    private fun sortByField() {
        try {
            getColUnsafe.modSchema(check = true)
            launchCatchingTask { changeSortField(notetype, currentPos) }
        } catch (e: ConfirmModSchemaException) {
            e.log()
            // Handler mMod schema confirmation
            this@NoteTypeFieldEditor.showDialogFragment(ConfirmationDialog.newFullSyncInstance(this, REQUEST_SORT_FIELD))
        }
    }

    private suspend fun changeSortField(
        notetype: NotetypeJson,
        idx: Int,
    ) {
        withProgress(resources.getString(CommonString.model_field_editor_changing)) {
            withCol {
                Timber.d("doInBackgroundChangeSortField")
                notetypes.setSortIndex(notetype, idx)
                notetypes.save(notetype)
            }
        }
        initialize()
    }

    private fun closeActivity() {
        finish()
    }

    fun handleAction(contextMenuAction: NoteTypeFieldEditorContextMenuAction) {
        onDialogInput(INPUT_CONTEXT_MENU_ACTION, Bundle().apply { putString(INPUT_ACTION, contextMenuAction.name) })
    }

    private fun performAction(contextMenuAction: NoteTypeFieldEditorContextMenuAction) {
        when (contextMenuAction) {
            NoteTypeFieldEditorContextMenuAction.Sort -> sortByField()
            NoteTypeFieldEditorContextMenuAction.Reposition -> repositionFieldDialog()
            NoteTypeFieldEditorContextMenuAction.Delete -> deleteFieldDialog()
            NoteTypeFieldEditorContextMenuAction.Rename -> renameFieldDialog()
            NoteTypeFieldEditorContextMenuAction.AddLanguageHint -> localeHintDialog()
        }
    }

    private fun localeHintDialog() {
        Timber.i("displaying locale hint dialog")
        // We don't currently show the current value, but we may want to in the future
        showDialogFragment(LocaleSelectionDialog())
    }

    /*
     * Sets the Locale Hint of the field to the provided value.
     * This allows some keyboard (GBoard) to change language
     */
    private fun addFieldLocaleHint(selectedLocale: Locale) {
        setLanguageHintForField(getColUnsafe.notetypes, notetype, currentPos, selectedLocale)
        val format = getString(CommonString.model_field_editor_language_hint_dialog_success_result, selectedLocale.displayName)
        showSnackbar(format, Snackbar.LENGTH_SHORT)
        initialize()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Throws(ConfirmModSchemaException::class)
    fun addField(fieldNameInput: EditText) {
        val fieldName = uniqueName(fieldNameInput)
        addField(fieldName, true)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Throws(ConfirmModSchemaException::class)
    fun renameField(fieldNameInput: EditText?) {
        this.fieldNameInput = fieldNameInput
        renameField()
    }

    /*
     * Returns a list of field names with their kind
     * So far the only kind is SORT, which defines the field upon which notes could be sorted
     */
    private fun fieldNamesWithKind(): List<Pair<String, NodetypeKind>> =
        fieldsLabels.mapIndexed { index, fieldName ->
            Pair(
                fieldName,
                if (index == notetype.sortf) NodetypeKind.SORT else NodetypeKind.UNDEFINED,
            )
        }

    companion object {
        const val EXTRA_NOTETYPE_NAME = "extra_notetype_name"
        const val EXTRA_NOTETYPE_ID = "extra_notetype_id"
        private const val STATE_SELECTED_FIELD = "selectedField"
        private const val STATE_SELECTED_FIELD_ID = "selectedFieldId"
        private const val STATE_PENDING_INPUT = "pendingInput"
        private const val PENDING_KEY = "key"
        private const val PENDING_VALUE = "value"
        private const val INPUT_CONTEXT_MENU_ACTION = "context_menu_action"
        private const val INPUT_ACTION = "action"
        private const val REQUEST_ADD_FIELD = "request_add_field"
        private const val REQUEST_DELETE_FIELD = "request_delete_field"
        private const val REQUEST_REPOSITION_FIELD = "request_reposition_field"
        private const val REQUEST_SORT_FIELD = "request_sort_field"
        private const val RESULT_FIELD_NAME = "field_name"
        private const val RESULT_FIELD_INDEX = "field_index"
    }
}

enum class NodetypeKind {
    SORT,
    UNDEFINED,
}

internal class NoteFieldAdapter(
    private val context: Context,
    labels: List<Pair<String, NodetypeKind>>,
) : ArrayAdapter<Pair<String, NodetypeKind>>(context, 0, labels) {
    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val binding =
            if (convertView != null) {
                ItemNotetypeFieldBinding.bind(convertView)
            } else {
                ItemNotetypeFieldBinding.inflate(LayoutInflater.from(context), parent, false)
            }

        getItem(position)?.let {
            val (name, kind) = it
            binding.fieldName.text = name
            binding.fieldName.setCompoundDrawablesRelativeWithIntrinsicBoundsKt(
                end =
                    when (kind) {
                        NodetypeKind.SORT -> R.drawable.ic_sort
                        NodetypeKind.UNDEFINED -> 0
                    },
            )
        }
        return binding.root
    }
}
