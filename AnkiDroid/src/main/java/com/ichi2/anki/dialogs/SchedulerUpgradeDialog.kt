// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Mohd Raghib <raghib.khan76@gmail.com>

package com.ichi2.anki.dialogs

import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.utils.openUrl

/**
 * A reusable, non-dismissible dialog that prompts the user to upgrade the scheduler.
 *
 * This dialog is intended to be shown when the collection is using an outdated scheduler (v1),
 * and prevents interaction with the app until the upgrade is acknowledged or canceled.
 *
 * @param activity The host activity used to show the dialog.
 * @param onUpgrade Callback invoked when the user confirms the upgrade (via the positive button).
 * @param onCancel Callback invoked when the dialog is canceled (via negative button or back press).
 */

class SchedulerUpgradeDialog(
    private val activity: AppCompatActivity,
    private val onUpgrade: () -> Unit,
    private val onCancel: (() -> Unit)? = null,
) {
    fun showDialog() {
        val dialog =
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.scheduler_upgrade_required_dialog_title)
                .setMessage(TR.schedulingUpdateRequired())
                .setPositiveButton(R.string.dialog_ok, null)
                .setNegativeButton(R.string.dialog_cancel, null)
                .apply {
                    if (AdaptionUtil.hasWebBrowser(activity)) {
                        setNeutralButton(TR.schedulingUpdateMoreInfoButton(), null)
                    }
                }.create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        fun dismissAndCancel() {
            dialog.dismiss()
            onCancel?.invoke()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                it.isEnabled = false // prevent multiple taps
                onUpgrade.invoke()
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                dismissAndCancel()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                activity.openUrl(R.string.link_scheduler_upgrade_faq)
            }

            dialog.setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                    return@setOnKeyListener false
                }

                dismissAndCancel()
                true
            }
        }

        dialog.show()
    }
}
