/*
 * Copyright (c) 2015 Ryan Annis <squeenix@live.ca>
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
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
package com.ichi2.anki

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.databinding.ActivityNoteTypeFieldEditorBinding
import com.ichi2.anki.databinding.ItemNotetypeFieldBinding
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.KEY_SELECTED_LOCALE
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.REQUEST_HINT_LOCALE_SELECTION
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.Companion.newInstance
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Fields
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.servicelayer.LanguageHintService.setLanguageHintForField
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
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
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.util.Locale

@NeedsTest("perform one action, then another")
class NoteTypeFieldEditor : AnkiActivity(R.layout.activity_note_type_field_editor) {
    private val binding by viewBinding(ActivityNoteTypeFieldEditorBinding::bind)

    // Position of the current field selected
    private var currentPos = 0
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
        setContentView(R.layout.activity_note_type_field_editor)
        enableToolbar()
        binding.notetypeName.text = intent.getStringExtra(EXTRA_NOTETYPE_NAME)
        startLoadingCollection()
        setFragmentResultListener(REQUEST_HINT_LOCALE_SELECTION) { _, bundle ->
            val selectedLocale =
                BundleCompat.getSerializable(
                    bundle,
                    KEY_SELECTED_LOCALE,
                    Locale::class.java,
                )
            if (selectedLocale != null) {
                addFieldLocaleHint(selectedLocale)
            }
            dismissAllDialogFragments()
        }
    }

    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        initialize()
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
            showThemedToast(this, R.string.field_editor_model_not_available, true)
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
                currentPos = position
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
            showThemedToast(this, resources.getString(R.string.toast_empty_name), true)
            return null
        }
        if (fieldsLabels.any { input == it }) {
            showThemedToast(this, resources.getString(R.string.toast_duplicate_field), true)
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
                positiveButton(R.string.menu_add) {
                    // Name is valid, now field is added
                    val fieldName = uniqueName(fieldNameInput)
                    try {
                        addField(fieldName, true)
                    } catch (e: ConfirmModSchemaException) {
                        e.log()

                        // Create dialogue to for schema change
                        val c = ConfirmationDialog()
                        c.setArgs(resources.getString(R.string.full_sync_confirmation))
                        val confirm =
                            Runnable {
                                try {
                                    addField(fieldName, false)
                                } catch (e1: ConfirmModSchemaException) {
                                    e1.log()
                                    // This should never be thrown
                                }
                            }
                        c.setConfirm(confirm)
                        this@NoteTypeFieldEditor.showDialogFragment(c)
                    }
                    getColUnsafe.notetypes.update(notetype)
                    initialize()
                }
                negativeButton(R.string.dialog_cancel)
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
        val confirm =
            Runnable {
                getColUnsafe.modSchema(check = false)
                deleteField()

                // This ensures that the context menu closes after the field has been deleted
                supportFragmentManager.popBackStackImmediate(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE,
                )
            }

        if (fieldsLabels.size < 2) {
            showThemedToast(this, resources.getString(R.string.toast_last_field), true)
        } else {
            try {
                getColUnsafe.modSchema(check = true)
                val fieldName = noteFields[currentPos].name
                ConfirmationDialog().let {
                    it.setArgs(
                        title = fieldName,
                        message = resources.getString(R.string.field_delete_warning),
                    )
                    it.setConfirm(confirm)
                    showDialogFragment(it)
                }
            } catch (e: ConfirmModSchemaException) {
                e.log()
                ConfirmationDialog().let {
                    it.setConfirm(confirm)
                    it.setArgs(resources.getString(R.string.full_sync_confirmation))
                    showDialogFragment(it)
                }
            }
        }
    }

    private fun deleteField() {
        launchCatchingTask {
            Timber.d("doInBackGroundDeleteField")
            withProgress(message = getString(R.string.model_field_editor_changing)) {
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
                title(R.string.model_field_editor_rename)
                positiveButton(R.string.rename) {
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
                        c.setArgs(resources.getString(R.string.full_sync_confirmation))
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
                negativeButton(R.string.dialog_cancel)
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
                    positiveButton(R.string.dialog_ok) {
                        val input = (it as AlertDialog).getInputField()
                        result(input.text.toString().toInt())
                    }
                    negativeButton(R.string.dialog_cancel)
                    setMessage(TR.fieldsNewPosition1(numberOfTemplates))
                    setView(R.layout.dialog_generic_text_input)
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
                val c = ConfirmationDialog()
                c.setArgs(resources.getString(R.string.full_sync_confirmation))
                val confirm =
                    Runnable {
                        try {
                            getColUnsafe.modSchema(check = false)
                            repositionField(newPosition - 1)
                        } catch (e1: JSONException) {
                            throw RuntimeException(e1)
                        }
                    }
                c.setConfirm(confirm)
                this@NoteTypeFieldEditor.showDialogFragment(c)
            }
        }
    }

    private fun repositionField(index: Int) {
        launchCatchingTask {
            withProgress(message = getString(R.string.model_field_editor_changing)) {
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
            val c = ConfirmationDialog()
            c.setArgs(resources.getString(R.string.full_sync_confirmation))
            val confirm =
                Runnable {
                    getColUnsafe.modSchema(check = false)
                    launchCatchingTask { changeSortField(notetype, currentPos) }
                }
            c.setConfirm(confirm)
            this@NoteTypeFieldEditor.showDialogFragment(c)
        }
    }

    private suspend fun changeSortField(
        notetype: NotetypeJson,
        idx: Int,
    ) {
        withProgress(resources.getString(R.string.model_field_editor_changing)) {
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
        val format = getString(R.string.model_field_editor_language_hint_dialog_success_result, selectedLocale.displayName)
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
