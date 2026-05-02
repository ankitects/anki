/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.instantnoteeditor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CustomActionModeCallback
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.databinding.ActivityInstantNoteEditorBinding
import com.ichi2.anki.databinding.DialogInstantEditorBinding
import com.ichi2.anki.databinding.ViewInstantEditorFieldBinding
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.startDeckSelection
import com.ichi2.anki.withProgress
import com.ichi2.themes.setTransparentBackground
import com.ichi2.utils.AndroidUiUtils.hideKeyboard
import com.ichi2.utils.AssetHelper.TEXT_PLAIN
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.rawHitTest
import com.ichi2.utils.show
import com.ichi2.utils.title
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Single instance Activity for instantly editing and adding cloze card/s without actually opening the app,
 * uses a custom dialog layout and a transparent activity theme to achieve the functionality.
 **/
class InstantNoteEditorActivity : AnkiActivity(R.layout.activity_instant_note_editor) {
    private val viewModel: InstantEditorViewModel by viewModels()

    /**
     * The [androidx.viewbinding.ViewBinding] of the dialog if the activity is in a valid state
     */
    // TODO: extract the dialog so dialogBinding is not nullable
    @SuppressLint("ActivityLayoutPrefix") // not an activity
    private var dialogBinding: DialogInstantEditorBinding? = null

    private val binding by viewBinding(ActivityInstantNoteEditorBinding::bind)

    private val editMode: EditMode
        get() = viewModel.editorMode.value

    private val updatedTextKey = "updatedText"

    private lateinit var clozeEditTextField: TextInputEditText
    private lateinit var instantAlertDialog: AlertDialog

    /** Gets the actual cloze field text value **/
    private val clozeFieldText: String?
        get() = viewModel.actualClozeFieldText.value

    private val dialogBackCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showDiscardChangesDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }
        setTransparentBackground()
        enableEdgeToEdge()

        setViewBinding(binding)

        onBackPressedDispatcher.addCallback(this, dialogBackCallback)

        if (Intent.ACTION_SEND != intent.action && intent.type == null && TEXT_PLAIN != intent.type) {
            Timber.i("Intent type is not supported")
            return
        }

        viewModel.setClozeFieldText(
            savedInstanceState?.getString(updatedTextKey) ?: getSharedIntentText(intent),
        )

        setupErrorListeners()
        prepareEditorDialog()

        registerDeckSelectedHandler(action = ::onDeckSelected)
    }

    override fun onDestroy() {
        if (this::instantAlertDialog.isInitialized) {
            instantAlertDialog.dismiss()
        }
        super.onDestroy()
    }

    private fun prepareEditorDialog() =
        lifecycleScope.launch {
            Timber.d("Checking for cloze note type")

            viewModel.dialogType.collect { dialogType ->
                dialogType?.let { dialog ->
                    when (dialog) {
                        DialogType.NO_CLOZE_NOTE_TYPES_DIALOG -> {
                            Timber.d("Showing no cloze note type dialog")
                            noClozeNoteTypesFoundDialog()
                        }

                        DialogType.SHOW_EDITOR_DIALOG -> {
                            Timber.d("Showing editor dialog")
                            showEditorDialog()
                        }
                    }
                }
            }
        }

    /** Setup the deck spinner and custom editor dialog layout **/
    private fun showEditorDialog() {
        showDialog()
        updateSelectedDeckName()
    }

    private fun updateSelectedDeckName() {
        viewModel.deckId?.let { did ->
            if (this::instantAlertDialog.isInitialized) {
                launchCatchingTask {
                    withProgress {
                        val selectedDeckName = withCol { decks.name(did) }
                        dialogBinding?.noteDeckName?.text = selectedDeckName
                    }
                }
            }
        }
    }

    /** Gets the shared text received through an Intent. **/
    private fun getSharedIntentText(receivedIntent: Intent): String? = receivedIntent.getStringExtra(Intent.EXTRA_TEXT)

    private fun openNoteEditor() {
        val sharedText = clozeEditTextField.text.toString()
        val noteEditorIntent = NoteEditorLauncher.AddInstantNote(sharedText).toIntent(this)
        startActivity(noteEditorIntent)
        finish()
    }

    fun showDialog() {
        Timber.d("Showing Instant Note Editor dialog")
        val dialogBinding =
            DialogInstantEditorBinding.inflate(layoutInflater).also { binding ->
                dialogBinding = binding
            }

        dialogBinding.openNoteEditor.setOnClickListener {
            openNoteEditor()
        }
        handleClozeMode(dialogBinding.incrementClozeButton)

        val editFields = createEditFields(this, dialogBinding, viewModel.currentlySelectedNotetype.value)

        Timber.d("Adding edit text fields to the dialog")
        for (editField in editFields) {
            this.dialogBinding?.editorFieldsLayout?.addView(editField)
        }

        setLayoutVisibility(dialogBinding)

        instantAlertDialog =
            AlertDialog.Builder(this).show {
                setView(dialogBinding.root)
                setCancelable(false)
                setFinishOnTouchOutside(false)
                dialogBinding.spinnerLayout.setOnClickListener {
                    startDeckSelection(all = false, filtered = false)
                }
                dialogBinding.actionSaveNote.setOnClickListener {
                    Timber.d("Save note button pressed")
                    checkAndSave()
                }

                // required due to setCancelable(false)
                setOnKeyListener { _, keyCode, event ->
                    if (!(keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP)) {
                        return@setOnKeyListener false
                    }

                    this@InstantNoteEditorActivity.onBackPressedDispatcher.onBackPressed()
                    false
                }
            }

        // consume the touch event outside the dialog
        dialogBinding.root.rootView.userClickOutsideDialog(
            exclude = dialogBinding.root,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (intentTextChanged()) outState.putString(updatedTextKey, clozeFieldText)
    }

    @KotlinCleanup("notetypeJson -> non-null")
    private fun createEditFields(
        context: Context,
        dialogBinding: DialogInstantEditorBinding,
        notetypeJson: NotetypeJson?,
    ): List<View> {
        val editLines: MutableList<View> = mutableListOf()

        val clozeFields = viewModel.getClozeFields()
        var clozeFieldsSet = false

        for (field in notetypeJson!!.fields) {
            val fieldBinding = ViewInstantEditorFieldBinding.inflate(LayoutInflater.from(context))

            val name = field.name
            fieldBinding.editTextLayout.hint = name

            Timber.d("Populating the cloze edit text fields")
            // Anki allows multiple cloze fields, we pick the first field
            if (clozeFields.contains(name) && !clozeFieldsSet) {
                setupClozeFields(dialogBinding, fieldBinding.editFieldText)
                dialogBinding.chipLayoutTitle.text = name
                setupChipGroup(viewModel, dialogBinding.clozeTextChipGroup)
                clozeFieldsSet = true
            }

            editLines.add(fieldBinding.root)
        }
        return editLines
    }

    /** Sets the copied text to the cloze field and enable the single tap gesture for that field**/
    private fun setupClozeFields(
        dialogBinding: DialogInstantEditorBinding,
        textBox: TextInputEditText,
    ) {
        clozeEditTextField = textBox
        lifecycleScope.launch {
            viewModel.actualClozeFieldText.collectLatest { text ->
                textBox.setText(text)
                dialogBackCallback.isEnabled = intentTextChanged()
            }
        }

        dialogBinding.editorFieldsLayout.visibility = View.GONE

        enableErrorMessage()

        setActionModeCallback(textBox)

        dialogBinding.switchEditModeButton.setOnClickListener {
            viewModel.setClozeFieldText(textBox.text.toString())
            when (editMode) {
                EditMode.ADVANCED -> {
                    clozeEditTextField.hideKeyboard()
                    textBox.setText(clozeFieldText)
                    viewModel.setEditorMode(EditMode.SINGLE_TAP)
                    dialogBinding.switchEditModeButton.setIconResource(R.drawable.ic_mode_edit_white)

                    dialogBinding.singleTapLayout.visibility = View.VISIBLE
                    setupChipGroup(viewModel, dialogBinding.clozeTextChipGroup)
                    dialogBinding.editorFieldsLayout.visibility = View.GONE

                    viewModel.setClozeFieldText(textBox.text.toString())
                }

                EditMode.SINGLE_TAP -> {
                    dialogBinding.switchEditModeButton.setIconResource(R.drawable.ic_touch)
                    viewModel.setEditorMode(EditMode.ADVANCED)

                    dialogBinding.singleTapLayout.visibility = View.GONE
                    dialogBinding.editorFieldsLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setLayoutVisibility(dialogBinding: DialogInstantEditorBinding) {
        when (editMode) {
            EditMode.SINGLE_TAP -> {
                dialogBinding.singleTapLayout.visibility = View.VISIBLE
                dialogBinding.editorFieldsLayout.visibility = View.GONE
            }
            EditMode.ADVANCED -> {
                dialogBinding.singleTapLayout.visibility = View.GONE
                dialogBinding.editorFieldsLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun handleClozeMode(clozeButton: MaterialButton) {
        clozeButton.setOnClickListener {
            viewModel.toggleClozeMode()
        }
        lifecycleScope.launch {
            viewModel.currentClozeMode.collectLatest { mode ->
                when (mode) {
                    ClozeMode.INCREMENT -> clozeButton.setIconResource(R.drawable.ic_cloze_new_card)
                    ClozeMode.NO_INCREMENT -> clozeButton.setIconResource(R.drawable.ic_cloze_same_card)
                }
            }
        }
    }

    /** Set the error message to null when the text is changed in the TextInputEditText **/
    private fun enableErrorMessage() {
        clozeEditTextField.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                    // No action needed
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    viewModel.setWarningMessage(null)
                }

                override fun afterTextChanged(s: Editable?) {
                    // No action needed
                }
            },
        )
    }

    /**
     * Checks if the fields are not empty and contain cloze deletions,
     * retrieves the field content, and saves the note
     */
    private fun checkAndSave() {
        extractFieldValues()

        saveNoteWithProgress(skipClozeCheck = false)
    }

    private fun handleSaveNoteResult(result: SaveNoteResult) {
        when (result) {
            is SaveNoteResult.Failure -> {
                Timber.d("Failed to save note")
                savingErrorDialog(result.message ?: getString(R.string.something_wrong))
            }

            SaveNoteResult.Success -> {
                // Don't show snackbar to avoid blocking parent app
                showThemedToast(this@InstantNoteEditorActivity, TR.addingAdded(), true)
                finish()
            }

            is SaveNoteResult.Warning -> {
                Timber.d("Showing warning to the user")
                viewModel.setWarningMessage(result.message ?: getString(R.string.something_wrong))
            }
        }
    }

    /** Gets the field content from the editor, and updates the Note **/
    private fun extractFieldValues() {
        val editTextValues = mutableListOf<String>()

        dialogBinding?.editorFieldsLayout?.let { layout ->
            for (i in 0 until layout.childCount) {
                val childView = layout.getChildAt(i)

                if (childView.id == R.id.single_tap_layout) {
                    continue
                }

                if (childView is TextInputLayout) {
                    val text = extractTextFromInputField(childView)
                    Timber.d("String values in field are $text")
                    editTextValues.add(text)

                    updateFields(i, childView.findViewById(R.id.edit_field_text))
                }
            }
        }
    }

    private fun extractTextFromInputField(textInputLayout: TextInputLayout): String {
        val textInputEditText =
            textInputLayout.findViewById<TextInputEditText>(R.id.edit_field_text)
        return textInputEditText?.text?.toString() ?: ""
    }

    private fun updateFields(
        index: Int,
        field: TextInputEditText?,
    ) {
        val fieldContent = field!!.text?.toString() ?: ""
        val correctedFieldContent =
            NoteService.convertToHtmlNewline(
                fieldContent,
                false,
            )

        val note = viewModel.editorNote
        if (note.values()[index] != correctedFieldContent) {
            note.values()[index] = correctedFieldContent
        }
    }

    /** Show a dialog when there is no cloze note type is found, allowing user either to cancel or to open
     * AnkiDroid Note Editor **/
    private fun noClozeNoteTypesFoundDialog() {
        AlertDialog.Builder(this).show {
            title(R.string.cloze_note_required)
            message(R.string.cloze_not_found_message)
            positiveButton(R.string.open) {
                openNoteEditor()
            }
            negativeButton(R.string.dialog_cancel) {
                finish()
            }
        }
    }

    private fun setupErrorListeners() {
        Timber.d("Setting up error listeners")
        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog
                    .Builder(this)
                    .setTitle(R.string.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }.launchIn(lifecycleScope)

        viewModel.instantEditorError
            .onEach { errorMessage ->
                when (errorMessage) {
                    null -> {
                        dialogBinding?.warningText?.visibility = View.INVISIBLE
                    }

                    TR.addingYouHaveAClozeDeletionNote() -> {
                        noClozeDialog(errorMessage)
                    }

                    else -> {
                        dialogBinding?.warningText?.visibility = View.VISIBLE
                        dialogBinding?.warningText?.text = errorMessage
                    }
                }
            }.launchIn(lifecycleScope)
    }

    /** In case saving the note fails we, want to allow user to cancel and try again, or exist the activity **/
    private fun savingErrorDialog(message: String) {
        AlertDialog.Builder(this).show {
            message(text = message)
            positiveButton(R.string.try_again) {
                checkAndSave()
            }
            negativeButton(R.string.dialog_cancel) {
                instantAlertDialog.dismiss()
            }
        }
    }

    /** Warns the user for no cloze in the cloze field, and provide the choice to proceed or
     * to abort save and go back to the editor  **/
    private fun noClozeDialog(errorMessage: String) {
        AlertDialog.Builder(this).show {
            message(text = errorMessage)
            positiveButton(text = TR.actionsSave()) {
                saveNoteWithProgress(skipClozeCheck = true)
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    private fun saveNoteWithProgress(skipClozeCheck: Boolean) {
        lifecycleScope.launch {
            val result =
                withProgress(resources.getString(R.string.saving_facts)) {
                    viewModel.checkAndSaveNote(skipClozeCheck = skipClozeCheck)
                }
            handleSaveNoteResult(result)
        }
    }

    private fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) {
            return
        }
        require(deck is SelectableDeck.Deck)
        viewModel.deckId = deck.deckId
        updateSelectedDeckName()
    }

    private fun setActionModeCallback(textBox: TextInputEditText) {
        val clozeMenuId = View.generateViewId()
        textBox.customSelectionActionModeCallback = getActionModeCallback(textBox, clozeMenuId)
        textBox.customInsertionActionModeCallback = getActionModeCallback(textBox, clozeMenuId)
    }

    private fun getActionModeCallback(
        textBox: TextInputEditText,
        clozeMenuId: Int,
    ): ActionMode.Callback =
        CustomActionModeCallback(
            // we always have cloze type notes here
            isClozeType = true,
            getString(R.string.multimedia_editor_popup_cloze),
            clozeMenuId,
            onActionItemSelected = { mode, item ->
                val itemId = item.itemId
                if (itemId == clozeMenuId) {
                    val selectedText =
                        textBox.text?.substring(
                            textBox.selectionStart,
                            textBox.selectionEnd,
                        ) ?: ""
                    convertSelectedTextToCloze(
                        textBox,
                        selectedText,
                        viewModel.currentClozeNumber.value,
                    )

                    mode.finish()
                    true
                } else {
                    false
                }
            },
        )

    private fun View.userClickOutsideDialog(exclude: View) {
        setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_DOWN) return@setOnTouchListener false
            if (exclude.rawHitTest(event)) {
                return@setOnTouchListener false
            }
            this@InstantNoteEditorActivity.onBackPressedDispatcher.onBackPressed()
            false
        }
    }

    private fun intentTextChanged(): Boolean {
        Timber.d("Checking if the original text was changed")
        return intent.getStringExtra(Intent.EXTRA_TEXT) != clozeFieldText
    }

    private fun showDiscardChangesDialog() {
        DiscardChangesDialog.showDialog(this, message = TR.addingDiscardCurrentInput()) {
            Timber.i("InstantNoteEditorActivity:: OK button pressed to confirm discard changes")
            finish()
        }
    }

    private fun convertSelectedTextToCloze(
        textBox: EditText,
        word: String,
        incrementNumber: Int,
    ) {
        val text = textBox.text.toString()
        val selectionStart = textBox.selectionStart

        val start = text.indexOf(word, selectionStart - word.length)
        val end = start + word.length

        if (start != -1 && end != -1) {
            val newText =
                text.take(start) + "{{c$incrementNumber::$word}}" + text.substring(end)

            textBox.setText(newText)
            textBox.setSelection(start + "{{c$incrementNumber::".length)
        }
    }

    /**
     * Enum representing the modes available for cloze functionality.
     */
    enum class ClozeMode {
        INCREMENT,
        NO_INCREMENT,
    }

    /**
     * This enum class represents the different edit modes available for the user interface element.
     */
    enum class EditMode {
        /**
         * In this mode, a single tap on the text will turn it to cloze
         */
        SINGLE_TAP,

        /**
         * In this mode, user can edit the text as they want
         */
        ADVANCED,
    }

    /**
     * Enum class that represent the dialog that can be shown when the InstantEditor is initialized
     * **/
    enum class DialogType {
        /** Indicates that no cloze note types were found. **/
        NO_CLOZE_NOTE_TYPES_DIALOG,

        /** Indicates that the editor dialog should be shown. **/
        SHOW_EDITOR_DIALOG,
    }
}
