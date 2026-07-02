/*
 *  Copyright (c) 2024 RohanRaj123 <rajrohan88293@gmail.com>
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

package com.ichi2.anki.dialogs

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
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
        Timber.i("Dialog is shown to guide users correctly to troubleshoot the Tts error: Missing voice error")
        activity.runOnUiThread {
            AlertDialog.Builder(activity).show {
                setTitle(activity.getString(R.string.tts_error_dialog_title))
                setMessage(activity.getString(R.string.tts_error_dialog_reason_text, TtsVoices.ttsEngine, ttsTag?.lang))
                setNegativeButton(context.getString(R.string.tts_error_dialog_change_button_text)) { _, _ -> openSettings(activity) }
                setPositiveButton(
                    activity.getString(R.string.tts_error_dialog_supported_voices_button_text),
                ) { _, _ -> showVoicesDialog(fragmentManager) }
                setNeutralButton(context.getString(R.string.help)) { _, _ ->
                    activity.openUrl(R.string.link_faq_tts)
                }
            }
        }
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
