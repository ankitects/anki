// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <github@mikehardy.net>

package com.ichi2.anki.analytics

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.databinding.DialogFeedbackBinding
import org.acra.dialog.CrashReportDialog
import org.acra.dialog.CrashReportDialogHelper

/**
 * This file will appear to have static type errors because BaseCrashReportDialog extends android.support.XXX
 * instead of androidx.XXX.
 *
 * See [AnkiDroid Wiki: Crash-Reports](https://github.com/ankidroid/Anki-Android/wiki/Crash-Reports)
 */
// Registered: we are sufficiently registered in this special case
// ActivityLayoutPrefix: technically we are an activity, but use the 'dialog_' prefix
@SuppressLint("Registered", "ActivityLayoutPrefix")
class AnkiDroidCrashReportDialog :
    CrashReportDialog(),
    DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener {
    private lateinit var binding: DialogFeedbackBinding
    private var helper: CrashReportDialogHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setIcon(R.drawable.logo_star_144dp)
        dialogBuilder.setTitle(R.string.feedback_title)
        dialogBuilder.setPositiveButton(getString(R.string.feedback_report), this@AnkiDroidCrashReportDialog)
        dialogBuilder.setNegativeButton(R.string.dialog_cancel, this@AnkiDroidCrashReportDialog)
        helper = CrashReportDialogHelper(this, intent)
        dialogBuilder.setView(buildCustomView(savedInstanceState))
        val dialog = dialogBuilder.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener(this)
        dialog.show()
    }

    /**
     * Build the custom view used by the dialog
     */
    override fun buildCustomView(savedInstanceState: Bundle?): View {
        binding = DialogFeedbackBinding.inflate(layoutInflater)

        binding.alwaysReport.isChecked = this.sharedPrefs().getBoolean("autoreportCheckboxValue", true)
        // Set user comment if reloading after the activity has been stopped
        savedInstanceState?.getString(STATE_COMMENT)?.let { savedComment ->
            binding.userComment.setText(savedComment)
        }
        return binding.root
    }

    override fun onClick(
        dialog: DialogInterface,
        which: Int,
    ) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // Next time don't tick the auto-report checkbox by default
            val autoReport = binding.alwaysReport.isChecked
            val preferences = this.sharedPrefs()
            preferences.edit { putBoolean("autoreportCheckboxValue", autoReport) }
            // Set the autoreport value to true if ticked
            if (autoReport) {
                preferences.edit {
                    putString(
                        CrashReporter.FEEDBACK_REPORT_KEY,
                        CrashReporter.FEEDBACK_REPORT_ALWAYS,
                    )
                }
                CrashReportService.setReportingMode(CrashReporter.FEEDBACK_REPORT_ALWAYS)
            }
            // Send the crash report
            helper!!.sendCrash(binding.userComment.text.toString(), "")
        } else {
            // If the user got to the dialog, they were not limited.
            // The limiter persists it's limit info *before* the user cancels.
            // Therefore, on cancel, purge limits to make sure the user may actually send in future.
            // Better to maybe send to many reports than definitely too few.
            CrashReportService.deleteLimiterData(this)
            helper!!.cancelReports()
        }
        finish()
    }

    override fun onDismiss(dialog: DialogInterface) {
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.userComment.text?.let { comment ->
            outState.putString(STATE_COMMENT, comment.toString())
        }
    }

    companion object {
        private const val STATE_COMMENT = "comment"
    }
}
