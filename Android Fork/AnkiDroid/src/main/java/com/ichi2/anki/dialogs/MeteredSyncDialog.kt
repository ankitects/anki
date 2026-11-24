// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.create
import com.ichi2.utils.getCheckBoxPrompt
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton

/** Asks permission to sync on a metered network, preserving the request and checkbox across recreation. */
class MeteredSyncDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog =
        AlertDialog.Builder(requireContext()).create {
            message(CommonString.metered_sync_data_warning)
            positiveButton(CommonString.dialog_continue) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_METERED_SYNC,
                    Bundle().apply { putBoolean(RESULT_SYNC_MEDIA, requireArguments().getBoolean(ARG_SYNC_MEDIA)) },
                )
            }
            negativeButton(CommonString.dialog_cancel)
            checkBoxPrompt(
                CommonString.button_do_not_show_again,
                isCheckedDefault = savedInstanceState?.getBoolean(STATE_ALWAYS_ALLOW) ?: false,
                onToggle = MeteredSyncPolicy::setAlwaysAllow,
            )
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ALWAYS_ALLOW, (requireDialog() as AlertDialog).getCheckBoxPrompt().isChecked)
    }

    companion object {
        const val TAG = "meteredSyncConfirmation"
        const val REQUEST_METERED_SYNC = "request_metered_sync"
        const val RESULT_SYNC_MEDIA = "sync_media"
        private const val ARG_SYNC_MEDIA = "sync_media"
        private const val STATE_ALWAYS_ALLOW = "always_allow"

        fun newInstance(syncMedia: Boolean) =
            MeteredSyncDialog().apply {
                arguments = Bundle().apply { putBoolean(ARG_SYNC_MEDIA, syncMedia) }
            }
    }
}

fun FragmentActivity.registerMeteredSyncHandler(action: (syncMedia: Boolean) -> Unit) {
    setFragmentResultListener(MeteredSyncDialog.REQUEST_METERED_SYNC) { _, result ->
        action(result.getBoolean(MeteredSyncDialog.RESULT_SYNC_MEDIA))
    }
}
