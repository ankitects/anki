// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 RohanRaj123 <rajrohan88293@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.TtsVoices
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.utils.openUrl
import com.ichi2.utils.show
import timber.log.Timber

object TtsPlaybackErrorDialog {
    fun ttsPlaybackErrorDialog(
        activity: Activity,
        fragmentManager: FragmentManager,
        ttsTag: TTSTag?,
    ) {
        activity.runOnUiThread {
            if (showMissingEngineDialogIfNeeded(activity)) return@runOnUiThread
            Timber.i("Displaying TTS Error: 'Language not supported'")
            AlertDialog.Builder(activity).show {
                setTitle(activity.getString(CommonString.tts_error_dialog_title))
                setMessage(activity.getString(CommonString.tts_error_dialog_reason_text, TtsVoices.ttsEngine, ttsTag?.lang))
                setNegativeButton(context.getString(CommonString.tts_error_dialog_change_button_text)) { _, _ -> openSettings(activity) }
                setPositiveButton(
                    activity.getString(CommonString.tts_error_dialog_supported_voices_button_text),
                ) { _, _ -> showVoicesDialog(fragmentManager) }
                setNeutralButton(context.getString(CommonString.help)) { _, _ ->
                    activity.openUrl(R.string.link_faq_tts)
                }
            }
        }
    }

    /**
     * Shows installation guidance if no TTS engine is installed.
     *
     * @return `true` if the dialog is shown or queued on the UI thread; `false` if an engine is installed.
     */
    fun showMissingEngineDialogIfNeeded(activity: Activity): Boolean {
        if (TtsVoices.hasInstalledEngine(activity)) return false

        Timber.w("no TTS engines installed")
        activity.runOnUiThread {
            AlertDialog.Builder(activity).show {
                setTitle(CommonString.tts_no_engine_title)
                setMessage(CommonString.tts_no_engine_message)
                setPositiveButton(CommonString.dialog_ok, null)
                setNeutralButton(CommonString.help) { _, _ -> activity.openUrl(R.string.link_faq_tts_no_engine) }
            }
        }
        return true
    }

    private fun openSettings(activity: Activity) {
        try {
            Timber.i("Opening TextToSpeech engine settings to change the engine")
            activity.startActivity(
                Intent("com.android.settings.TTS_SETTINGS").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            )
        } catch (e: Exception) {
            CrashReportService.sendExceptionReport(e, e.localizedMessage)
        }
    }

    private fun showVoicesDialog(fragmentManager: FragmentManager) {
        TtsVoicesDialogFragment().show(fragmentManager, "TTS_VOICES_DIALOG_FRAGMENT")
    }
}
