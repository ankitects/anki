/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.InitialActivity.StartupFailure.InitializationError
import com.ichi2.anki.R
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
            title(R.string.ankidroid_init_failed_webview_title)
            message(text = failure.toHumanReadableString(context))
            positiveButton(R.string.close) {
                activity.closeCollectionAndFinish()
            }
            failure.infoLink?.let { url ->
                neutralButton(R.string.help) {
                    activity.openUrl(url)
                }
            }
            cancelable(false)
        }
    }
}
