/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.utils

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber
import java.lang.Exception

object IntentUtil {
    @JvmStatic // (fixable) required due to structure of unit tests
    fun canOpenIntent(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            val packageManager = context.packageManager
            intent.resolveActivity(packageManager) != null
        } catch (e: Exception) {
            Timber.w(e)
            false
        }

    fun tryOpenIntent(
        activity: AnkiActivity,
        intent: Intent,
    ) {
        try {
            if (canOpenIntent(activity, intent)) {
                activity.startActivity(intent)
            } else {
                val errorMsg = activity.getString(R.string.feedback_no_suitable_app_found)
                activity.showSnackbar(errorMsg, Snackbar.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            Timber.w(e)
            val errorMsg = activity.getString(R.string.feedback_no_suitable_app_found)
            activity.showSnackbar(errorMsg, Snackbar.LENGTH_SHORT)
        }
    }

    fun Intent.resolveMimeType(): String? =
        if (type == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(data.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            type
        }
}
