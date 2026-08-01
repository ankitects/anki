// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: Copyright 2015 Google Inc. All Rights Reserved.

package com.ichi2.compat.customtabs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.browser.customtabs.CustomTabsService
import androidx.core.net.toUri
import com.ichi2.anki.common.crashreporting.runCatchingWithReport
import com.ichi2.anki.compat.CompatHelper.Companion.queryIntentActivitiesCompat
import com.ichi2.anki.compat.CompatHelper.Companion.resolveActivityCompat
import com.ichi2.anki.compat.CompatHelper.Companion.resolveServiceCompat
import com.ichi2.anki.compat.GET_RESOLVED_FILTER_L
import com.ichi2.anki.compat.ResolveInfoFlagsCompat
import timber.log.Timber

/**
 * Helper class for Custom Tabs.
 */
object CustomTabsHelper {
    private const val STABLE_PACKAGE = "com.android.chrome"
    private const val BETA_PACKAGE = "com.chrome.beta"
    private const val DEV_PACKAGE = "com.chrome.dev"
    private const val LOCAL_PACKAGE = "com.google.android.apps.chrome"
    private const val EXTRA_CUSTOM_TABS_KEEP_ALIVE = "android.support.customtabs.extra.KEEP_ALIVE"
    private var sPackageNameToUse: String? = null

    fun addKeepAliveExtra(
        context: Context,
        intent: Intent,
    ) {
        val keepAliveIntent =
            Intent().setClassName(
                context.packageName,
                KeepAliveService::class.java.canonicalName!!,
            )
        intent.putExtra(EXTRA_CUSTOM_TABS_KEEP_ALIVE, keepAliveIntent)
    }

    /**
     * Goes through all apps that handle VIEW intents and have a warmup service. Picks
     * the one chosen by the user if there is one, otherwise makes a best effort to return a
     * valid package name.
     *
     * This is **not** threadsafe.
     *
     * @param context [Context] to use for accessing [PackageManager].
     * @return The package name recommended to use for connecting to custom tabs related components.
     */
    fun getPackageNameToUse(context: Context): String? {
        if (sPackageNameToUse != null) return sPackageNameToUse
        val pm = context.packageManager
        // Get default VIEW intent handler.
        val activityIntent = Intent(Intent.ACTION_VIEW, "https://www.example.com".toUri())
        val defaultViewHandlerInfo =
            runCatchingWithReport("getPackageNameToUse", onlyIfSilent = true) {
                pm.resolveActivityCompat(activityIntent, ResolveInfoFlagsCompat.EMPTY)
            }.getOrNull()
        var defaultViewHandlerPackageName: String? = null
        if (defaultViewHandlerInfo != null) {
            defaultViewHandlerPackageName = defaultViewHandlerInfo.activityInfo.packageName
        }

        // Get all apps that can handle VIEW intents.
        val resolvedActivityList = pm.queryIntentActivitiesCompat(activityIntent, ResolveInfoFlagsCompat.EMPTY)
        val packagesSupportingCustomTabs: MutableList<String?> = ArrayList(resolvedActivityList.size)
        for (info in resolvedActivityList) {
            val serviceIntent = Intent()
            serviceIntent.action = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
            serviceIntent.setPackage(info.activityInfo.packageName)
            if (pm.resolveServiceCompat(serviceIntent, ResolveInfoFlagsCompat.EMPTY) != null) {
                packagesSupportingCustomTabs.add(info.activityInfo.packageName)
            }
        }

        // Now packagesSupportingCustomTabs contains all apps that can handle both VIEW intents
        // and service calls.
        if (packagesSupportingCustomTabs.isEmpty()) {
            sPackageNameToUse = null
        } else if (packagesSupportingCustomTabs.size == 1) {
            sPackageNameToUse = packagesSupportingCustomTabs[0]
        } else if (!defaultViewHandlerPackageName.isNullOrEmpty() &&
            !hasSpecializedHandlerIntents(context, activityIntent) &&
            packagesSupportingCustomTabs.contains(defaultViewHandlerPackageName)
        ) {
            sPackageNameToUse = defaultViewHandlerPackageName
        } else if (packagesSupportingCustomTabs.contains(STABLE_PACKAGE)) {
            sPackageNameToUse = STABLE_PACKAGE
        } else if (packagesSupportingCustomTabs.contains(BETA_PACKAGE)) {
            sPackageNameToUse = BETA_PACKAGE
        } else if (packagesSupportingCustomTabs.contains(DEV_PACKAGE)) {
            sPackageNameToUse = DEV_PACKAGE
        } else if (packagesSupportingCustomTabs.contains(LOCAL_PACKAGE)) {
            sPackageNameToUse = LOCAL_PACKAGE
        }
        return sPackageNameToUse
    }

    /**
     * Used to check whether there is a specialized handler for a given intent.
     * @param intent The intent to check with.
     * @return Whether there is a specialized handler for the given intent.
     */
    private fun hasSpecializedHandlerIntents(
        context: Context,
        intent: Intent,
    ): Boolean {
        try {
            val pm = context.packageManager
            val handlers =
                pm.queryIntentActivitiesCompat(
                    intent,
                    ResolveInfoFlagsCompat.of(GET_RESOLVED_FILTER_L),
                )
            if (handlers.isEmpty()) {
                return false
            }
            for (resolveInfo in handlers) {
                val filter = resolveInfo.filter ?: continue
                if (filter.countDataAuthorities() == 0 || filter.countDataPaths() == 0) continue
                if (resolveInfo.activityInfo == null) continue
                return true
            }
        } catch (e: RuntimeException) {
            Timber.e("Runtime exception while getting specialized handlers")
        }
        return false
    }

    /**
     * @return All possible chrome package names that provide custom tabs feature.
     */
    val packages: Array<String>
        get() = arrayOf("", STABLE_PACKAGE, BETA_PACKAGE, DEV_PACKAGE, LOCAL_PACKAGE)
}
