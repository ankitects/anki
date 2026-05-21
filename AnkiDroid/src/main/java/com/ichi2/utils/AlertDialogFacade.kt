/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

@file:Suppress("unused")

package com.ichi2.utils

import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.text.InputFilter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.HandlerUtils.executeOnMainThread
import com.ichi2.anki.databinding.DialogAlertDialogCheckboxBinding
import com.ichi2.anki.databinding.DialogAlertDialogTitleWithHelpBinding
import com.ichi2.anki.databinding.DialogGenericRecyclerViewBinding
import com.ichi2.anki.databinding.DialogListviewMessageBinding
import com.ichi2.themes.Themes
import timber.log.Timber

/** Wraps [DialogInterface.OnClickListener] as we don't need the `which` parameter */
typealias DialogInterfaceListener = (DialogInterface) -> Unit

/**
 * - [ValidationResult.VALID] - user may proceed
 * - [ValidationResult.REJECTED] - user may not proceed (no error)
 * - [ValidationResult.error] - `error` is displayed to the user
 */
@JvmInline
value class ValidationResult private constructor(
    val error: String?,
) {
    companion object {
        /** The user may proceed */
        val VALID = ValidationResult(null)

        /**
         * The user may not proceed; no error displayed
         *
         * Typically for 'obvious' issues, such as not changing a name
         */
        val REJECTED = ValidationResult("")

        fun error(message: String) = ValidationResult(message)
    }
}

fun DialogInterfaceListener.toClickListener(): OnClickListener = OnClickListener { dialog: DialogInterface, _ -> this(dialog) }

/*
 * Allows easier transformations from [MaterialDialog] to [AlertDialog].
 * Inline this file when material dialog is removed
 */
fun AlertDialog.Builder.title(
    @StringRes stringRes: Int? = null,
    text: String? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        setTitle(stringRes)
    } else {
        setTitle(text)
    }
}

fun AlertDialog.Builder.message(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        setMessage(stringRes)
    } else {
        setMessage(text)
    }
}

/**
 * Shows an icon to the left of the dialog title.
 */
fun AlertDialog.Builder.iconAttr(
    @DrawableRes res: Int,
): AlertDialog.Builder = this.setIcon(Themes.getResFromAttr(this.context, res))

fun AlertDialog.Builder.positiveButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        this.setPositiveButton(stringRes, click?.toClickListener())
    } else {
        this.setPositiveButton(text, click?.toClickListener())
    }
}

fun AlertDialog.Builder.neutralButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        this.setNeutralButton(stringRes, click?.toClickListener())
    } else {
        this.setNeutralButton(text, click?.toClickListener())
    }
}

fun AlertDialog.Builder.negativeButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        this.setNegativeButton(stringRes, click?.toClickListener())
    } else {
        this.setNegativeButton(text, click?.toClickListener())
    }
}

fun AlertDialog.Builder.cancelable(cancelable: Boolean): AlertDialog.Builder = this.setCancelable(cancelable)

/**
 * Executes the provided block, then creates an [AlertDialog] with the arguments supplied
 * and immediately displays the dialog
 */
inline fun <T : AlertDialog.Builder> T.show(
    enableEnterKeyHandler: Boolean = false, // Make it opt-in
    block: T.() -> Unit,
): AlertDialog {
    this.apply { block() }
    val dialog = this.show()
    return if (enableEnterKeyHandler) {
        dialog.setupEnterKeyHandler()
    } else {
        dialog
    }
}

/**
 * Extension function to configure an AlertDialog to handle the Enter key press event.
 * This will make the Enter key directly trigger the positive button action instead of just selecting it.
 */
fun AlertDialog.setupEnterKeyHandler(): AlertDialog {
    this.setOnKeyListener { dialog, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
            // Get the positive button and simulate a click
            val positiveButton = (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
            if (positiveButton != null && positiveButton.isEnabled) {
                positiveButton.performClick()
                return@setOnKeyListener true
            }
        }
        false
    }
    return this
}

/**
 * Creates an [AlertDialog] from the [AlertDialog.Builder] instance, then executes [block] with it.
 */
fun AlertDialog.Builder.createAndApply(block: AlertDialog.() -> Unit): AlertDialog =
    create().apply {
        block()
    }

/**
 * Executes [block] on the [AlertDialog.Builder] instance and returns the initialized [AlertDialog].
 */
fun <T : AlertDialog.Builder> T.create(block: T.() -> Unit): AlertDialog {
    block()
    return create()
}

/**
 * Adds a checkbox to the dialog, whilst continuing to display the value of [message]
 * @param stringRes The string resource to display for the checkbox label.
 * @param text The literal string to display for the checkbox label.
 * @param isCheckedDefault Whether or not the checkbox is initially checked.
 * @param onToggle A listener invoked when the checkbox is checked or unchecked.
 */
fun AlertDialog.Builder.checkBoxPrompt(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    isCheckedDefault: Boolean = false,
    onToggle: (checked: Boolean) -> Unit,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    val binding = DialogAlertDialogCheckboxBinding.inflate(LayoutInflater.from(context))
    val checkBox = binding.checkbox

    val checkBoxLabel = if (stringRes != null) context.getString(stringRes) else text
    checkBox.text = checkBoxLabel
    checkBox.isChecked = isCheckedDefault

    checkBox.setOnCheckedChangeListener { _, isChecked ->
        onToggle(isChecked)
    }

    return this.setView(binding.root)
}

fun AlertDialog.getCheckBoxPrompt(): CheckBox =
    requireNotNull(findViewById(R.id.checkbox)) {
        "CheckBox prompt is not available. Forgot to call AlertDialog.Builder.checkBoxPrompt()?"
    }

/**
 * Sets a custom view for the dialog.
 *
 * @param view the view to display in the dialog
 * @param paddingStart the start padding in pixels
 * @param paddingTop the top padding in pixels
 * @param paddingEnd the end padding in pixels
 * @param paddingBottom the bottom padding in pixels
 *
 * @see [AlertDialog.Builder.setView]
 * @see [View.setPaddingRelative]
 */
fun AlertDialog.Builder.customView(
    view: View,
    paddingTop: Int = 0,
    paddingBottom: Int = 0,
    paddingStart: Int = 0,
    paddingEnd: Int = 0,
): AlertDialog.Builder {
    val container = FrameLayout(context)

    val containerParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )

    container.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
    container.addView(view, containerParams)
    setView(container)

    return this
}

fun AlertDialog.Builder.customListAdapter(adapter: RecyclerView.Adapter<*>) {
    val binding = DialogGenericRecyclerViewBinding.inflate(LayoutInflater.from(context))
    val recyclerView = binding.dialogRecyclerView
    recyclerView.adapter = adapter
    recyclerView.layoutManager = LinearLayoutManager(context)
    this.setView(binding.root)
}

/**
 * Adds a RecyclerView with a custom adapter and decoration to the AlertDialog.
 * @param adapter The adapter for the RecyclerView.
 * @param context The context used to access resources and LayoutInflater.
 */
fun AlertDialog.Builder.customListAdapterWithDecoration(
    adapter: RecyclerView.Adapter<*>,
    context: Context,
) {
    val binding = DialogGenericRecyclerViewBinding.inflate(LayoutInflater.from(context))
    val recyclerView = binding.dialogRecyclerView
    recyclerView.adapter = adapter
    recyclerView.layoutManager = LinearLayoutManager(context)
    val dividerItemDecoration = DividerItemDecoration(recyclerView.context, LinearLayoutManager.VERTICAL)
    recyclerView.addItemDecoration(dividerItemDecoration)
    this.setView(binding.root)
}

/**
 * Note: using [waitForPositiveButton] = true doesn't automatically close the dialog and it
 * requires a manual call to [android.app.Dialog.dismiss] inside the callback listening for text
 * input to replicate the standard dialog behavior.
 *
 * @param hint The hint text to be displayed to the user
 * @param prefill The text to initially appear in the [EditText]
 * @param allowEmpty If true, [DialogInterface.BUTTON_POSITIVE] is disabled if the [EditText] is empty
 * @param displayKeyboard Whether to open the keyboard when the dialog appears
 * @param callback if [waitForPositiveButton], called when [positiveButton] is pressed, otherwise
 *  called whenever the text is changed
 * @param maxLength if set, the user may not enter more than the supplied number of digits
 * @param inputType see [EditText.setInputType]
 * @param waitForPositiveButton MaterialDialog compat: if `false` [callback] is called on input
 * @param validator see [ValidationResult]. Valid if `null`, an error is shown if non-null.
 * if `true` [callback] is called when [positiveButton] is pressed
 */
fun AlertDialog.input(
    hint: String? = null,
    inputType: Int? = null,
    prefill: CharSequence? = null,
    allowEmpty: Boolean = false,
    maxLength: Int? = null,
    displayKeyboard: Boolean = false,
    waitForPositiveButton: Boolean = true,
    validator: ((String) -> ValidationResult)? = null,
    callback: (AlertDialog, CharSequence) -> Unit,
): AlertDialog {
    // Builder.setView() may not be called before show()
    if (!this.isShowing) throw IllegalStateException("input() requires .show()")

    getInputTextLayout().hint = hint

    getInputField().apply {
        if (displayKeyboard) {
            AndroidUiUtils.setFocusAndOpenKeyboard(this, window!!)
        }

        inputType?.let { this.inputType = it }

        doOnTextChanged { text, _, _, _ ->
            val input = text?.toString() ?: ""

            // handle allowEmpty
            if (!allowEmpty && input.isEmpty()) {
                this@input.getInputTextLayout().error = null
                this@input.positiveButton.isEnabled = false
                return@doOnTextChanged
            }

            // handle validation errors
            val validationError = validator?.invoke(input)
            this@input.getInputTextLayout().error = validationError?.error
            this@input.positiveButton.isEnabled = validationError?.error == null
            if (validationError != null) return@doOnTextChanged

            // no errors, see if we should fire the callback on every keypress
            // TODO: this was used to perform additional validation, which should be moved to the
            //  'validator' parameter,
            if (!waitForPositiveButton) {
                callback(this@input, input)
            }
        }

        if (waitForPositiveButton) {
            positiveButton.setOnClickListener {
                callback(this@input, this.text.toString())
            }
        }

        maxLength?.let { filters += InputFilter.LengthFilter(it) }

        requestFocus()
        // this calls callback(this, prefill). positiveButton may be disabled if there's no prefill
        setText(prefill)
        moveCursorToEnd()
    }
    return this
}

/**
 * @return the layout for the input text of the dialog
 * @throws IllegalArgumentException if the dialog does not contain [R.id.dialog_text_input_layout]]
 */
fun AlertDialog.getInputTextLayout() =
    requireNotNull(findViewById<TextInputLayout>(R.id.dialog_text_input_layout)) {
        "view must be dialog_generic_text_input"
    }

/**
 * @return the [EditText] of the dialog
 * @throws IllegalArgumentException if the dialog does not contain [R.id.dialog_text_input_layout]]
 */
fun AlertDialog.getInputField() = getInputTextLayout().editText!!

/** @see AlertDialog.getButton */
val AlertDialog.positiveButton: Button
    get() = getButton(DialogInterface.BUTTON_POSITIVE)
val AlertDialog.negativeButton: Button
    get() = getButton(DialogInterface.BUTTON_NEGATIVE)
val AlertDialog.neutralButton: Button?
    get() = getButton(DialogInterface.BUTTON_NEUTRAL)

/**
 * Executes [block] when a touch outside the dialog occurs
 *
 * This MUST be called after [show] or inside [AlertDialog.setOnShowListener]
 *
 * This will not call [AlertDialog.cancel]
 */
fun AlertDialog.handleOutsideTouch(
    binding: ViewBinding,
    block: () -> Unit,
) {
    val dialogContentView =
        findViewById(com.google.android.material.R.id.contentPanel)
            ?: binding.root.parent as? View
            ?: binding.root

    window?.decorView?.setOnTouchListener { _, event ->
        if (event.action != MotionEvent.ACTION_DOWN) return@setOnTouchListener false
        if (dialogContentView.rawHitTest(event)) return@setOnTouchListener false
        block()
        true
    }
}

/**
 * Extension function for AlertDialog.Builder to set a list of items.
 * Items are not displayed if [AlertDialog.Builder.setMessage] has been called
 *
 * @param items The items to display in the list.
 * @param onClick A lambda function that is invoked when an item is clicked.
 */
fun AlertDialog.Builder.listItems(
    items: List<CharSequence>,
    onClick: (dialog: DialogInterface, index: Int) -> Unit,
): AlertDialog.Builder =
    this.setItems(items.toTypedArray()) { dialog, which ->
        onClick(dialog, which)
    }

/**
 * Extension workaround for Displaying ListView & Message Together
 * Alert Dialog Doesn't allow message and listview together so a customView is used.
 *
 * @param message The message which you want to display in the dialog
 * @param items The items to display in the list.
 * @param onClick A lambda function that is invoked when an item is clicked.
 */
fun AlertDialog.Builder.listItemsAndMessage(
    message: String?,
    items: List<CharSequence>,
    onClick: (dialog: DialogInterface, index: Int) -> Unit,
): AlertDialog.Builder {
    val binding = DialogListviewMessageBinding.inflate(LayoutInflater.from(context))
    binding.message.text = message
    binding.listView.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, items)

    val dialog = this.create()
    binding.listView.setOnItemClickListener { _, _, index, _ ->
        onClick(dialog, index)
    }
    return this.setView(binding.root)
}

/**
 * Adds a custom title view to the dialog with a 'help' icon. Typically used to open the Anki Manual
 *
 * **Example:**
 * ```kotlin
 * MaterialAlertDialogBuilder(context).create {
 *     titleWithHelpIcon(stringRes = R.string.reset_card_dialog_title) {
 *         requireActivity().openUrl(Uri.parse(getString(R.string.link_manual)))
 *     }
 * }
 * ```
 *
 * @param onHelpClick action executed when the help icon is clicked
 * @param startIcon optional icon to display at the start of the title
 */
fun AlertDialog.Builder.titleWithHelpIcon(
    @StringRes stringRes: Int? = null,
    text: String? = null,
    @DrawableRes startIcon: Int? = null,
    onHelpClick: View.OnClickListener,
): AlertDialog.Builder {
    // setup the view for the dialog
    val binding = DialogAlertDialogTitleWithHelpBinding.inflate(LayoutInflater.from(context))
    setCustomTitle(binding.root)

    if (startIcon != null) {
        binding.titleIcon.setImageResource(startIcon)
        binding.titleIcon.visibility = View.VISIBLE
    }

    // apply a custom title
    if (stringRes != null) {
        binding.title.setText(stringRes)
    } else if (text != null) {
        binding.title.text = text
    }

    // set the action when clicking the help icon
    binding.helpIcon.setOnClickListener { v ->
        Timber.i("dialog help icon click")
        onHelpClick.onClick(v)
    }
    return this
}

/** Calls [AlertDialog.dismiss], ignoring errors */
fun AlertDialog.dismissSafely() {
    // The exception will be uncaught if not run on the main thread.
    executeOnMainThread {
        try {
            // safer to catch the exception to be sure dismiss() was called
            dismiss()
        } catch (e: IllegalArgumentException) {
            if (window == null || !isShowing) {
                Timber.d(e, "Dialog not attached to window manager")
                return@executeOnMainThread
            }
            Timber.w(e, "Dialog not attached to window manager")
        }
    }
}
