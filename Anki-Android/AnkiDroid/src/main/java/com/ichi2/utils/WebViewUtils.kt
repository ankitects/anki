/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.AndroidRuntimeException
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import androidx.webkit.WebViewCompat
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.utils.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@JvmInline
value class WebViewVersion(
    val value: Int,
)

@JvmInline
value class WebViewVersionCode(
    val value: Long,
)

/** The Android package version code which corresponds to the Webview version. */
internal val OLDEST_WORKING_WEBVIEW_VERSION_CODE = WebViewVersionCode(418306960L)

/** The minimum supported Webview version(Human readable). */
internal val OLDEST_WORKING_WEBVIEW_VERSION = WebViewVersion(85)

/**
 * Shows a dialog if the current WebView version is older than the last supported version.
 */

context(context: Context)
fun showDialogIfWebViewOutdated(
    minimumWebViewVersion: WebViewVersion = OLDEST_WORKING_WEBVIEW_VERSION,
    onOutdated: () -> Unit = {},
): Boolean {
    val userVisibleCode = getChromeLikeWebViewVersionIfOutdated(context, minimumWebViewVersion) ?: return false

    // Provide guidance to the user if the WebView is outdated
    val webviewPackageInfo = WebViewCompat.getCurrentWebViewPackage(context)
    val legacyWebViewPackageInfo = getLegacyWebViewPackageInfo(context.packageManager)
    // TODO modify the alert dialog text to handle the usage of developer builds for system WebView
    if (legacyWebViewPackageInfo != null) {
        Timber.w("WebView is outdated. %s: %s", legacyWebViewPackageInfo.packageName, legacyWebViewPackageInfo.versionName)
        showOutdatedWebViewDialog(context, userVisibleCode, R.string.link_legacy_webview_update, onOutdated)
    } else {
        Timber.w("WebView is outdated. %s: %s", webviewPackageInfo?.packageName, webviewPackageInfo?.versionName)
        showOutdatedWebViewDialog(context, userVisibleCode, R.string.link_webview_update, onOutdated)
    }
    return true
}

@MainThread
fun getWebviewUserAgent(context: Context): String? {
    try {
        return WebView(context).settings.userAgentString
    } catch (e: AndroidRuntimeException) {
        // MissingWebViewPackageException is not public
        if (e.cause.toString().contains("MissingWebViewPackageException")) {
            Timber.w(e, "MissingWebViewPackageException")
            return null // WebView not installed - don't log a crash report
        }
        CrashReportService.sendExceptionReport(e, "WebViewUtils", "some issue occurred while extracting webview user agent")
    } catch (e: Throwable) {
        CrashReportService.sendExceptionReport(e, "WebViewUtils", "some issue occurred while extracting webview user agent")
    }
    return null
}

/*
 * Returns a Chrome-like WebView version if it is outdated, otherwise null if
 * cannot be determined at all or if okay
 */
private fun getChromeLikeWebViewVersionIfOutdated(
    context: Context,
    minimumWebViewVersion: WebViewVersion,
): Int? {
    // If we cannot get the package information at all, return null
    val webviewPackageInfo = WebViewCompat.getCurrentWebViewPackage(context) ?: return null
    val webviewVersion =
        webviewPackageInfo.versionName ?: run {
            Timber.w("Failed to obtain WebView version")
            return null
        }
    val versionCode = PackageInfoCompat.getLongVersionCode(webviewPackageInfo)
    return checkWebViewVersionComponents(
        webviewPackageInfo.packageName,
        webviewVersion,
        versionCode,
        getWebviewUserAgent(context),
        minimumWebViewVersion,
    )
}

@VisibleForTesting
fun checkWebViewVersionComponents(
    packageName: String,
    webviewVersion: String,
    versionCode: Long,
    userAgent: String?,
    minimumWebViewVersion: WebViewVersion = OLDEST_WORKING_WEBVIEW_VERSION,
): Int? {
    // Sometimes the webview version code appears too old, and the package name does as well,
    // but it's a webview that advertises modern capabilities via User-Agent in "Chrome" section
    // Our warning is purely advisory, so, let's let those through if User-Agent looks okay
    userAgent?.let {
        val chromeRegex = """Chrome/(\d+)""".toRegex()
        val matchResult = chromeRegex.find(userAgent)?.groupValues?.get(1)
        matchResult?.toInt()?.let {
            if (it >= minimumWebViewVersion.value) {
                // If the User-Agent says we are modern, trust it and skip further checks.
                return null
            } else {
                // If the User-Agent is explicitly below the floor, return it immediately.
                return it
            }
        }
    }
    // Checking the version code works for most webview packages
    if (versionCode >= OLDEST_WORKING_WEBVIEW_VERSION_CODE.value) {
        Timber.d(
            "WebView is up to date. %s: %s(%s)",
            packageName,
            webviewVersion,
            versionCode.toString(),
        )
        return null
    }
    return webviewVersion.split('.').firstOrNull()?.toIntOrNull()
}

data class WebViewInfo(
    val userAgent: String?,
    val packageName: String?,
    val versionCode: Long?,
)

/**
 * Retrieves [WebViewInfo] containing the current User Agent and system WebView package details.
 *
 * It is called on the main thread because [WebViewCompat.getCurrentWebViewPackage]
 * and [getWebviewUserAgent] require main thread access to the WebView system.
 *
 * It does not throw; if the WebView provider is missing or an error occurs
 * during retrieval, a [WebViewInfo] object with null values is returned.
 *
 * @return A [WebViewInfo] object with WebView package details.
 */
suspend fun getWebViewInfo(context: Context): WebViewInfo =
    withContext(Dispatchers.Main) {
        val packageInfo = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
        WebViewInfo(
            userAgent = getWebviewUserAgent(context),
            packageName = packageInfo?.packageName,
            versionCode = runCatching { packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } }.getOrNull(),
        )
    }

private fun showOutdatedWebViewDialog(
    context: Context,
    installedVersion: Int,
    @StringRes learnMoreUrl: Int,
    onDismiss: () -> Unit = {},
) {
    AlertDialog.Builder(context).show {
        setMessage(context.getString(R.string.webview_update_message, installedVersion, OLDEST_WORKING_WEBVIEW_VERSION.value))
        setPositiveButton(R.string.scoped_storage_learn_more) { _, _ ->
            context.openUrl(learnMoreUrl)
        }
        setOnDismissListener { onDismiss() }
    }
}

private fun getLegacyWebViewPackageInfo(packageManager: PackageManager): PackageInfo? =
    try {
        packageManager.getPackageInfo("com.android.webview", 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

/**
 * Enables debugging of web contents (HTML / CSS / JavaScript)
 * loaded into any WebViews of this application. This flag can be enabled
 * in order to facilitate debugging of web layouts and JavaScript
 * code running inside WebViews. Please refer to WebView documentation
 * for the debugging guide.
 *
 * In WebView 113.0.5656.0 and later, this is enabled automatically if the
 * app is declared as
 * [`android:debuggable="true"`](https://developer.android.com/guide/topics/manifest/application-element#debug)
 * in its manifest; otherwise, the
 * default is {@code false}.
 *
 * Enabling web contents debugging allows the state of any WebView in the
 * app to be inspected and modified by the user via adb. This is a security
 * liability and should not be enabled in production builds of apps unless
 * this is an explicitly intended use of the app. More info on
 * [secure debug settings](https://developer.android.com/topic/security/risks/android-debuggable)
 *
 * @param enabled whether to enable web contents debugging
 */
fun setWebContentsDebuggingEnabled(enabled: Boolean) =
    try {
        WebView.setWebContentsDebuggingEnabled(enabled)
    } catch (e: Exception) {
        // android.util.AndroidRuntimeException: android.webkit.WebViewFactory$MissingWebViewPackageException: Failed to load WebView provider: No WebView installed
        Timber.w(e, "setWebContentsDebuggingEnabled")
    }
