// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright 2015 Google Inc. All Rights Reserved.

package com.ichi2.compat.customtabs

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

/**
 * This is a helper class to manage the connection to the Custom Tabs Service.
 */
class CustomTabActivityHelper : ServiceConnectionCallback {
    private var customTabsSession: CustomTabsSession? = null
    private var client: CustomTabsClient? = null
    private var connection: CustomTabsServiceConnection? = null

    /**
     * Unbinds the Activity from the Custom Tabs Service.
     * @param activity the activity that is connected to the service.
     */
    fun unbindCustomTabsService(activity: Activity) {
        if (connection == null) return
        connection.let { activity.unbindService(it!!) }
        client = null
        customTabsSession = null
        connection = null
    }

    /**
     * Creates or retrieves an exiting CustomTabsSession.
     *
     * @return a CustomTabsSession.
     */
    val session: CustomTabsSession?
        get() {
            if (client == null) {
                customTabsSession = null
            } else if (customTabsSession == null) {
                customTabsSession = client!!.newSession(null)
            }
            return customTabsSession
        }

    /**
     * Binds the Activity to the Custom Tabs Service.
     * @param activity the activity to be bound to the service.
     */
    fun bindCustomTabsService(activity: Activity) {
        if (client != null) return
        try {
            val packageName = CustomTabsHelper.getPackageNameToUse(activity) ?: return
            connection = ServiceConnection(this)
            CustomTabsClient.bindCustomTabsService(activity, packageName, connection!!)
        } catch (e: SecurityException) {
            Timber.w(e, "CustomTabsService bind attempt failed, using fallback")
            CrashReportService.sendExceptionReport(
                e = e,
                origin = "bindCustomTabsService",
                onlyIfSilent = true,
            )
            disableCustomTabHandler()
        }
    }

    private fun disableCustomTabHandler() {
        Timber.i("Disabling custom tab handler and using fallback")
        sCustomTabsFailed = true
        client = null
        customTabsSession = null
        connection = null
    }

    /**
     * @see CustomTabsSession.mayLaunchUrl
     * @return true if call to mayLaunchUrl was accepted.
     */
    fun mayLaunchUrl(
        uri: Uri?,
        extras: Bundle?,
        otherLikelyBundles: List<Bundle?>?,
    ): Boolean {
        if (client == null) return false
        val session = session ?: return false
        return session.mayLaunchUrl(uri, extras, otherLikelyBundles)
    }

    override fun onServiceConnected(client: CustomTabsClient) {
        try {
            this.client = client
            try {
                this.client!!.warmup(0L)
            } catch (e: IllegalStateException) {
                // Issue 5337 - some browsers like TorBrowser don't adhere to Android 8 background limits
                // They will crash as they attempt to start services. warmup failure shouldn't be fatal though.
                Timber.w(e, "Ignoring CustomTabs implementation that doesn't conform to Android 8 background limits")
            }
            session
        } catch (e: SecurityException) {
            // #6142 - A securityException here means that we're not able to load the CustomTabClient at all, whereas
            // the IllegalStateException was a failure, but could be continued from
            Timber.w(e, "CustomTabsService bind attempt failed, using fallback")
            disableCustomTabHandler()
        }
    }

    override fun onServiceDisconnected() {
        client = null
        customTabsSession = null
    }

    /**
     * To be used as a fallback to open the Uri when Custom Tabs is not available.
     */
    interface CustomTabFallback {
        /**
         *
         * @param activity The Activity that wants to open the Uri.
         * @param uri The uri to be opened by the fallback.
         */
        fun openUri(
            activity: Activity,
            uri: Uri,
        )
    }

    @get:CheckResult
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val isFailed: Boolean
        get() = sCustomTabsFailed && client == null

    companion object {
        private var sCustomTabsFailed = false

        /**
         * Opens the URL on a Custom Tab if possible. Otherwise falls back to opening it on a WebView.
         *
         * @param activity The host activity.
         * @param customTabsIntent a CustomTabsIntent to be used if Custom Tabs is available.
         * @param uri the Uri to be opened.
         * @param fallback a CustomTabFallback to be used if Custom Tabs is not available.
         */
        fun openCustomTab(
            activity: Activity,
            customTabsIntent: CustomTabsIntent,
            uri: Uri,
            fallback: CustomTabFallback?,
        ) {
            val packageName = CustomTabsHelper.getPackageNameToUse(activity)

            // If we cant find a package name or there was a serious failure during init, we don't support
            // Chrome Custom Tabs. So, we fallback to the webview
            if (packageName == null || sCustomTabsFailed) {
                if (fallback != null) {
                    fallback.openUri(activity, uri)
                } else {
                    Timber.e("A version of Chrome supporting custom tabs was not available, and the fallback was null")
                }
            } else {
                customTabsIntent.intent.setPackage(packageName)
                try {
                    customTabsIntent.launchUrl(activity, uri)
                } catch (ex: ActivityNotFoundException) {
                    Timber.w("No app found to handle opening an external url from CustomTabsActivityHelper")
                    activity.showSnackbar(R.string.activity_start_failed)
                }
            }
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun resetFailed() {
            sCustomTabsFailed = false
        }
    }
}
