// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.dialogs.tools

import android.content.Context
import android.content.DialogInterface.BUTTON_POSITIVE
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

open class AsyncDialogBuilder(
    private val alertDialogBuilder: AlertDialog.Builder,
) {
    lateinit var continuation: Continuation<DialogResult>

    var onShowListener: ((AlertDialog) -> Unit)? = null

    private lateinit var checkedItems: BooleanArray

    fun setNegativeButton(textResource: Int) {
        alertDialogBuilder.setNegativeButton(textResource) { _, _ ->
            continuation.resume(DialogResult.Cancel)
        }
    }

    fun setPositiveButton(textResource: Int) {
        alertDialogBuilder.setPositiveButton(textResource) { _, _ ->
            continuation.resume(
                when {
                    ::checkedItems.isInitialized -> DialogResult.Ok.MultipleChoice(checkedItems)
                    else -> DialogResult.Ok.Simple
                },
            )
        }
    }

    sealed interface CheckedItems {
        data object None : CheckedItems

        data object All : CheckedItems

        class Some(
            val checkedItems: BooleanArray,
        ) : CheckedItems
    }

    fun setMultiChoiceItems(
        items: List<CharSequence>,
        checkedItems: CheckedItems,
        disablePositiveButtonIfNoItemsChosen: Boolean = true,
    ) {
        this.checkedItems =
            when (checkedItems) {
                is CheckedItems.All -> BooleanArray(items.size) { true }
                is CheckedItems.None -> BooleanArray(items.size) { false }
                is CheckedItems.Some -> checkedItems.checkedItems.clone()
            }

        fun enableDisablePositiveButton(dialog: AlertDialog) {
            dialog.getButton(BUTTON_POSITIVE).isEnabled = this.checkedItems.contains(true)
        }

        alertDialogBuilder.setMultiChoiceItems(items.toTypedArray(), this.checkedItems) { dialog, position, isChecked ->
            this.checkedItems[position] = isChecked
            if (disablePositiveButtonIfNoItemsChosen) enableDisablePositiveButton(dialog as AlertDialog)
        }

        if (disablePositiveButtonIfNoItemsChosen) onShowListener = ::enableDisablePositiveButton
    }
}

/**
 * A clutch that delegates calls to [AlertDialog.Builder].
 * All defined methods have the exact same signature.
 *
 * TODO When context receivers are finalized, remove this class and instead say:
 *   suspend fun Context.awaitDialog(block: context(AsyncDialogBuilder, AlertDialog.Builder) () -> Unit): DialogResult {
 *       val alertDialogBuilder = AlertDialog.Builder(this@Context)
 *       val asyncDialogBuilder = AsyncDialogBuilder(alertDialogBuilder)
 *       block(asyncDialogBuilder, alertDialogBuilder)
 *       ...
 *   }
 */
class CompoundDialogBuilder(
    private val alertDialogBuilder: AlertDialog.Builder,
) : AsyncDialogBuilder(alertDialogBuilder) {
    /** @see AlertDialog.Builder.setTitle */
    fun setTitle(
        @StringRes titleId: Int,
    ): AlertDialog.Builder = alertDialogBuilder.setTitle(titleId)

    /** @see AlertDialog.Builder.setTitle */
    fun setTitle(title: CharSequence): AlertDialog.Builder = alertDialogBuilder.setTitle(title)

    /** @see AlertDialog.Builder.setMessage */
    fun setMessage(
        @StringRes messageId: Int,
    ): AlertDialog.Builder = alertDialogBuilder.setMessage(messageId)

    /** @see AlertDialog.Builder.setMessage */
    fun setMessage(message: CharSequence): AlertDialog.Builder = alertDialogBuilder.setMessage(message)
}

sealed interface DialogResult {
    data object Cancel : DialogResult

    interface Ok : DialogResult {
        object Simple : Ok

        class MultipleChoice(
            val checkedItems: BooleanArray,
        ) : Ok
    }
}

/**
 * Show a dialog that is configured using the provided block.
 * Returns the result of user interaction with the dialog.
 * Experimental!
 *
 *     val dialogResult = awaitDialog {
 *         setTitle("Title")
 *         setMessage("Message")
 *         setPositiveButton(CommonString.dialog_ok)
 *         setNegativeButton(CommonString.dialog_cancel)
 *     }
 *
 *     if (dialogResult is DialogResult.Ok) {
 *         // User tapped on the Ok button in the dialog
 *     }
 *
 * In order for this to properly work,
 * instead of using [AlertDialog.Builder] methods that take listeners,
 * use an [AsyncDialogBuilder] method of the same name without one.
 */
suspend fun Context.awaitDialog(block: CompoundDialogBuilder.() -> Unit): DialogResult {
    val alertDialogBuilder = AlertDialog.Builder(this)
    val compoundDialogBuilder = CompoundDialogBuilder(alertDialogBuilder)

    compoundDialogBuilder.block()

    alertDialogBuilder
        .setOnCancelListener { compoundDialogBuilder.continuation.resume(DialogResult.Cancel) }
        .create()
        .apply { setOnShowListener { compoundDialogBuilder.onShowListener?.invoke(this) } }
        .show()

    return suspendCancellableCoroutine { compoundDialogBuilder.continuation = it }
}
