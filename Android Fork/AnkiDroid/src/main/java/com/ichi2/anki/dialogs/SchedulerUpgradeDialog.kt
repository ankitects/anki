// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Mohd Raghib <raghib.khan76@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.utils.openUrl

/**
 * A reusable, non-dismissible dialog that prompts the user to upgrade the scheduler.
 *
 * This dialog is intended to be shown when the collection is using an outdated scheduler (v1),
 * and prevents interaction with the app until the upgrade is acknowledged or canceled.
 *
 * Reports the decision to the current activity without retaining an activity-owned callback.
 */

class SchedulerUpgradeDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        isCancelable = false
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(CommonString.scheduler_upgrade_required_dialog_title)
            .setMessage(TR.schedulingUpdateRequired())
            .setPositiveButton(CommonString.dialog_ok, null)
            .setNegativeButton(CommonString.dialog_cancel, null)
            .apply {
                if (AdaptionUtil.hasWebBrowser(requireActivity())) {
                    setNeutralButton(TR.schedulingUpdateMoreInfoButton(), null)
                }
            }.create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = requireDialog() as AlertDialog
        dialog.setCanceledOnTouchOutside(false)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            it.isEnabled = false // prevent multiple taps
            reportDecision(upgrade = true)
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
            reportDecision(upgrade = false)
        }

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            requireActivity().openUrl(R.string.link_scheduler_upgrade_faq)
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }

            reportDecision(upgrade = false)
            true
        }
    }

    private fun reportDecision(upgrade: Boolean) {
        val manager = parentFragmentManager
        dismiss()
        manager.setFragmentResult(REQUEST_SCHEDULER_UPGRADE, Bundle().apply { putBoolean(RESULT_UPGRADE, upgrade) })
    }

    companion object {
        const val TAG = "schedulerUpgrade"
        const val REQUEST_SCHEDULER_UPGRADE = "request_scheduler_upgrade"
        const val RESULT_UPGRADE = "upgrade"
    }
}
