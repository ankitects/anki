// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CommonString
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

/** GHSA-54q9-5c5p-9rxg: drop Uri-permission grant flags from a card-origin intent. */
fun Intent.stripDangerousPermissions(): Intent =
    apply {
        flags = flags and
            (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            ).inv()
    }

/** Schemes that must never be dispatched via `startActivity` from user-provided content */
private val BLOCKED_CARD_SCHEMES =
    setOf(
        // executes in the handling browser's origin — historic Android cross-origin XSS vector
        "javascript",
        // leaks app-private storage to viewers on older devices / lax receivers
        "file",
        // hands provider data (sms, contacts, media) to any app that claims to view it
        "content",
        // smuggles attacker-controlled HTML/PDF into a rendering app (phishing, script execution)
        "data",
    )

fun isBlockedCardScheme(scheme: String?): Boolean = scheme?.lowercase() in BLOCKED_CARD_SCHEMES

/** True if this intent's data URI (or its selector's data URI) uses a blocked card scheme. */
fun Intent.usesDangerousScheme(): Boolean = isBlockedCardScheme(data?.scheme) || isBlockedCardScheme(selector?.data?.scheme)

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
        activity: Activity,
        intent: Intent,
    ) {
        try {
            if (canOpenIntent(activity, intent)) {
                activity.startActivity(intent)
            } else {
                val errorMsg = activity.getString(CommonString.feedback_no_suitable_app_found)
                activity.showSnackbar(errorMsg, Snackbar.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            Timber.w(e)
            val errorMsg = activity.getString(CommonString.feedback_no_suitable_app_found)
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
