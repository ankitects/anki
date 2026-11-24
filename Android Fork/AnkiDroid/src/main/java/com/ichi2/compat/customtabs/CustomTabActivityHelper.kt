// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright 2015 Google Inc. All Rights Reserved.

package com.ichi2.compat.customtabs

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.CheckResult
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import com.ichi2.anki.snackbar.showSnackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * This is a helper class to manage the connection to the Custom Tabs Service.
 * @param scope the activity's main-thread lifecycle scope
 */
@UiThread
class CustomTabActivityHelper(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ServiceConnectionCallback {
    private var customTabsSession: CustomTabsSession? = null
    private var client: CustomTabsClient? = null
    private var connection: CustomTabsServiceConnection? = null
    private var customTabsProviderInfo: String? = null
    private var initializationJob: Job? = null

    /**
     * Unbinds the Activity from the Custom Tabs Service.
     * @param activity the activity that is connected to the service.
     */
    fun unbindCustomTabsService(activity: Activity) {
        onServiceDisconnected()
        connection?.let { activity.unbindService(it) }
        connection = null
        customTabsProviderInfo = null
    }

    /**
     * Returns the prepared session, or `null` if none is available.
     *
     * Custom tabs can open without a session, so do not wait for this if it is unavailable.
     */
    val session: CustomTabsSession?
        get() = customTabsSession

    /**
     * Binds the Activity to the Custom Tabs Service.
     * @param activity the activity to be bound to the service.
     */
    fun bindCustomTabsService(activity: Activity) {
        if (connection != null) return
        customTabsProviderInfo = null
        try {
            val packageName = CustomTabsHelper.getPackageNameToUse(activity) ?: return
            customTabsProviderInfo = getProviderInfo(activity, packageName)
            connection = ServiceConnection(this)
            CustomTabsClient.bindCustomTabsService(activity, packageName, connection!!)
        } catch (e: SecurityException) {
            Timber.w(e, "CustomTabsService bind attempt failed, using fallback. %s", customTabsProviderInfo)
            CrashReportService.sendExceptionReport(
                e = e,
                origin = "bindCustomTabsService",
                additionalInfo = customTabsProviderInfo,
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
        // connection should be set to null in `onStop`
        customTabsProviderInfo = null
    }

    private fun getProviderInfo(
        context: Context,
        packageName: String,
    ): String {
        val packageInfo = runCatching { context.getPackageInfoCompat(packageName, PackageInfoFlagsCompat.EMPTY) }.getOrNull()
        val version =
            packageInfo?.let {
                val versionCode = runCatching { PackageInfoCompat.getLongVersionCode(it) }.getOrNull()
                "${it.versionName ?: "unknown"} (${versionCode ?: "unknown"})"
            }
        return "Custom Tabs provider: $packageName, version: ${version ?: "unknown"}"
    }

    /**
     * Preloads a URL for future loading.
     *
     * @see CustomTabsSession.mayLaunchUrl
     */
    fun mayLaunchUrl(
        url: String,
        extras: Bundle? = null,
        otherLikelyBundles: List<Bundle?>? = null,
    ) {
        scope.launch {
            // must be on the main thread
            val cachedSession = session
            val success =
                withContext(ioDispatcher) {
                    cachedSession?.mayLaunchUrl(url.toUri(), extras, otherLikelyBundles) == true
                }
            if (!success) {
                Timber.w("Couldn't preload url: %s", url)
            }
        }
    }

    override fun onServiceConnected(client: CustomTabsClient) {
        onServiceDisconnected()
        this.client = client
        initializationJob =
            scope.launch {
                try {
                    customTabsSession =
                        withContext(ioDispatcher) {
                            warmup(client)
                            // Cancelling cannot interrupt Binder IPC. Don't start another call after stopping.
                            ensureActive()
                            client.newSession(null)
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    // A late failure from a cancelled connection must not disable its replacement.
                    currentCoroutineContext().ensureActive()
                    handleInitializationFailure(e)
                }
            }
    }

    @WorkerThread
    private fun warmup(client: CustomTabsClient) {
        try {
            client.warmup(0L)
        } catch (e: IllegalStateException) {
            // Issue 5337 - some browsers don't adhere to Android 8 background limits.
            // Warmup failure shouldn't be fatal.
            Timber.w(e, "Ignoring CustomTabs implementation that doesn't conform to Android 8 background limits")
        }
    }

    private fun handleInitializationFailure(e: RuntimeException) {
        Timber.w(e, "CustomTabsService bind attempt failed, using fallback. %s", customTabsProviderInfo)
        CrashReportService.sendExceptionReport(
            e = e,
            origin = "CustomTabActivityHelper::onServiceConnected",
            additionalInfo = customTabsProviderInfo,
            onlyIfSilent = true,
        )
        // TODO: https://github.com/ankidroid/Anki-Android/issues/21708
        // Edge throws on a cold bind. Retry in the future instead of disabling the feature
        disableCustomTabHandler()
    }

    override fun onServiceDisconnected() {
        initializationJob?.cancel()
        initializationJob = null
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
                    activity.showSnackbar(CommonString.activity_start_failed)
                }
            }
        }

        @VisibleForTesting(otherwise = VisibleForTesting.NONE)
        fun resetFailed() {
            sCustomTabsFailed = false
        }
    }
}
