// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * JS API capabilities that reach beyond the card the user is currently reviewing. Every entry is
 * off by default; call sites consult [AppPermissions.requirePermission] and the method throws when
 * the backing pref is not set.
 */
enum class DangerousJsApiPermission {
    /** Arbitrary field reads across the collection. Gates `searchCardWithCallback`. */
    QUERY_COLLECTION,

    /** Modify tags of any note in the collection. Gates `addTagToNote` */
    MODIFY_TAGS,
}

/**
 * App-level, user-opt-in capabilities. Distinct from [com.ichi2.utils.Permissions], which wraps
 * Android runtime permissions (audio, storage, notifications).
 *
 * @param showSnackbar displays a message to the user, warning that a permission has been denied.
 * e.g. "Security: Unsafe JavaScript call blocked ... this can be unblocked in the advanced settings"
 */
class AppPermissions(
    private val context: Context,
    private val showSnackbar: (String) -> Unit = {},
) {
    /**
     * Creates a new URL navigation intent, or `null` when permission is denied.
     *
     * HTTP(S) links do not require external app launches to be enabled and are returned with
     * `CATEGORY_BROWSABLE`.
     *
     * Other URL schemes require [external app launches][checkCanLaunchExternalApps] to be enabled.
     */
    fun createUrlIntent(url: Uri): Intent? =
        when (url.scheme) {
            "http", "https" -> Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE)
            else -> if (checkCanLaunchExternalApps()) Intent(Intent.ACTION_VIEW, url) else null
        }

    /**
     * Returns true if cards may launch external apps;
     * shows a rate-limited snackbar on denial.
     */
    fun checkCanLaunchExternalApps(): Boolean {
        // TODO: Consider a permission system based on the provided intent
        // TODO: Consider requiring the user has interacted with the WebView.
        val allowed =
            context
                .sharedPrefs()
                .getBoolean(context.getString(R.string.pref_allow_card_external_launch_key), false)
        if (!allowed) notifyLaunchDenied()
        return allowed
    }

    private fun notifyLaunchDenied() {
        // Avoid decision fatigue by limiting the number of times the warning is shown.
        // Reset on app restart.
        if (launchDenialsSinceProcessStart.incrementAndGet() > MAX_SNACKBARS_PER_PROCESS) return
        showSnackbar(context.getString(CommonString.card_external_launch_denied_snackbar))
    }

    /**
     * Asserts that the user has granted [permission].
     *
     * @throws DangerousJsPermissionDeniedException if [permission] has not been granted.
     */
    fun requirePermission(permission: DangerousJsApiPermission) {
        val key = context.getString(R.string.pref_allow_dangerous_js_api)
        if (!context.sharedPrefs().getBoolean(key, false)) {
            Timber.w("requirePermission denied: %s", permission.name)
            notifyJsApiDenied()
            // The exception is exposed to the JS as an error, as with any security failure from a
            // `JavascriptInterface` method.
            throw DangerousJsPermissionDeniedException(permission.name)
        }
    }

    private fun notifyJsApiDenied() {
        // Avoid decision fatigue by limiting the number of times the warning is shown.
        // Reset on app restart.
        if (jaApiDenialsSinceProcessStart.incrementAndGet() > MAX_SNACKBARS_PER_PROCESS) return
        showSnackbar(context.getString(CommonString.dangerous_js_api_denied_snackbar))
    }

    companion object {
        private const val MAX_SNACKBARS_PER_PROCESS = 2
        private val jaApiDenialsSinceProcessStart = AtomicInteger(0)
        private val launchDenialsSinceProcessStart = AtomicInteger(0)

        @VisibleForTesting
        fun resetDenialCounterForTesting() {
            jaApiDenialsSinceProcessStart.set(0)
            launchDenialsSinceProcessStart.set(0)
        }
    }
}

class DangerousJsPermissionDeniedException(
    val permission: String,
) : SecurityException("Dangerous permission blocked: $permission.")
