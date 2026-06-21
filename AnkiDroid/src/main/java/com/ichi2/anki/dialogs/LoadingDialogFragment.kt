// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.commitNow
import com.google.android.material.loadingindicator.LoadingIndicator
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.utils.cancelable
import com.ichi2.utils.create
import com.ichi2.utils.customView

/**
 * Simple [DialogFragment] to be used to show a "loading" ui state.
 */
class LoadingDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.fragment_loading, null)
        val canBeCancelled = arguments?.getBoolean(KEY_CANCELLABLE) ?: true
        dialogView.findViewById<TextView>(R.id.text).text =
            arguments?.getString(KEY_MESSAGE) ?: getString(R.string.dialog_processing)
        return AlertDialog
            .Builder(requireContext())
            .create {
                customView(dialogView)
                cancelable(canBeCancelled)
            }.apply { setCanceledOnTouchOutside(canBeCancelled) }
    }

    companion object {
        const val TAG = "LoadingDialogFragment"
        private const val KEY_MESSAGE = "key_message"
        private const val KEY_CANCELLABLE = "key_cancellable"

        /**
         * Creates an instance of [LoadingDialogFragment].
         * @param message optional message for the loading operation, will default to
         * [R.string.dialog_processing] if not provided
         * @param cancellable if the dialog should be cancellable or not (also affects cancel when
         * touching outside the dialog window)
         */
        fun newInstance(
            message: String? = null,
            cancellable: Boolean = true,
        ) = LoadingDialogFragment().apply {
            arguments =
                Bundle().apply {
                    putString(KEY_MESSAGE, message)
                    putBoolean(KEY_CANCELLABLE, cancellable)
                }
        }
    }
}

/**
 * Shows a [LoadingDialogFragment].
 * This method will look for a previous instance of the dialog and reuse it(by changing the message)
 * if it's already showing and the new call doesn't modify input parameters(ex. [cancellable]). In
 * any other cases, the old instance will be removed and a new one will be used.
 *
 * Note: Multiple calls of this method will result in a single dialog being shown, this also
 * implies that a call to [dismissLoadingDialog] will dismiss the dialogs of all calls. Callers need
 * to handle this scenario by combining the loading states and manually handling showing/dismissing.
 *
 * @param message the message to show along with the [LoadingIndicator] or null to default to
 * use "Processing..."
 * @param cancellable true if the dialog should be cancellable, false otherwise. This will also
 * apply to the [AlertDialog.setCanceledOnTouchOutside] property
 */
@MainThread
fun AnkiActivity.showLoadingDialog(
    message: String? = null,
    cancellable: Boolean = true,
) {
    val fragment =
        supportFragmentManager.findFragmentByTag(LoadingDialogFragment.TAG) as? LoadingDialogFragment
    val isAlreadyShowing = fragment?.dialog?.isShowing == true
    if (isAlreadyShowing) {
        // if a dialog is already showing and it has the same input params then just update the
        // dialog with the new message, otherwise remove the instance
        val fragmentView = fragment.view // sanity check
        if (fragmentView != null && cancellable == fragment.isCancelable) {
            fragmentView.findViewById<TextView>(R.id.text)?.text =
                message ?: getString(R.string.dialog_processing)
            return
        }
    }
    // remove the previous fragment if an instance is found but it is not showing or different input
    // parameters were requested for the new dialog fragment
    removeImmediately(fragment)
    val loadingDialog = LoadingDialogFragment.newInstance(message, cancellable)
    // showNow() avoids a race condition - removal is synchronous
    loadingDialog.showNow(supportFragmentManager, LoadingDialogFragment.TAG)
}

/** Synchronously removes the provided [LoadingDialogFragment] if valid(not null) */
private fun AnkiActivity.removeImmediately(fragment: LoadingDialogFragment?) {
    if (fragment != null) {
        supportFragmentManager.commitNow { remove(fragment) }
    }
}

/**
 * Dismisses and removes the current displayed [LoadingDialogFragment] if one is present.
 */
fun AnkiActivity.dismissLoadingDialog() {
    val fragment =
        supportFragmentManager.findFragmentByTag(LoadingDialogFragment.TAG) as? LoadingDialogFragment
    removeImmediately(fragment)
}
