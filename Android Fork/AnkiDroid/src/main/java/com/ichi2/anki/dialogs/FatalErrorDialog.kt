// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.InitialActivity.StartupFailure.InitializationError
import com.ichi2.utils.cancelable
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import timber.log.Timber

object FatalErrorDialog {
    fun build(
        activity: AnkiActivity,
        failure: InitializationError,
    ): AlertDialog {
        val context: Context = activity
        Timber.i("Displaying 'Fatal error'")
        return AlertDialog.Builder(context).create {
            title(CommonString.ankidroid_init_failed_webview_title)
            message(text = failure.toHumanReadableString(context))
            positiveButton(CommonString.close) {
                activity.closeCollectionAndFinish()
            }
            failure.infoLink?.let { url ->
                neutralButton(CommonString.help) {
                    activity.openUrl(url)
                }
            }
            cancelable(false)
        }
    }
}
