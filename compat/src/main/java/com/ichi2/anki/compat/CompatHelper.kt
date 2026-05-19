// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.compat

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyCharacterMap.deviceHasKey
import android.view.KeyEvent.KEYCODE_PAGE_DOWN
import android.view.KeyEvent.KEYCODE_PAGE_UP
import android.view.View
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.anki.compat.CompatHelper.Companion.resolveActivityCompat
import java.io.Serializable

/**
 * Selects a [Compat] class based on the device's [Build.VERSION.SDK_INT]
 *
 * Use [compat] to obtain this instance:
 *
 * ```kotlin
 *     CompatHelper.compat.copyFile(stream, path)
 * ```
 */
class CompatHelper private constructor() {
    // Note: Needs ": Compat" or the type system assumes `Compat21`
    @SuppressLint("NewApi")
    private val compatValue: Compat =
        when {
            sdkVersion >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> CompatV34()
            sdkVersion >= Build.VERSION_CODES.TIRAMISU -> CompatV33()
            sdkVersion >= Build.VERSION_CODES.S -> CompatV31()
            sdkVersion >= Build.VERSION_CODES.Q -> CompatV29()
            sdkVersion >= Build.VERSION_CODES.O -> CompatV26()
            else -> BaseCompat()
        }

    companion object {
        /** Singleton instance of [CompatHelper] */
        private val instance by lazy { CompatHelper() }

        /** Get the current Android API level.  */
        val sdkVersion: Int
            get() = Build.VERSION.SDK_INT

        /**
         * Main public method to get the compatibility class
         */
        val compat get() = instance.compatValue

        @Suppress("unused")
        val isChromebook: Boolean
            get() = (
                "chromium".equals(Build.BRAND, ignoreCase = true) ||
                    "chromium".equals(Build.MANUFACTURER, ignoreCase = true) ||
                    "novato_cheets".equals(Build.DEVICE, ignoreCase = true)
            )
        val isKindle: Boolean
            get() = "amazon".equals(Build.BRAND, ignoreCase = true) || "amazon".equals(Build.MANUFACTURER, ignoreCase = true)

        fun hasScrollKeys(): Boolean = deviceHasKey(KEYCODE_PAGE_UP) || deviceHasKey(KEYCODE_PAGE_DOWN)

        inline fun <reified T : Serializable?> Bundle.getSerializableCompat(name: String): T? =
            compat.getSerializable(this, name, T::class.java)

        @Suppress("unused")
        inline fun <reified T : Serializable?> Intent.getSerializableExtraCompat(name: String): T? =
            compat.getSerializableExtra(this, name, T::class.java)

        /**
         * Retrieve overall information about an application package that is
         * installed on the system.
         *
         * @see PackageManager.getPackageInfo
         * @throws NameNotFoundException if no such package is available to the caller.
         */
        @Throws(NameNotFoundException::class)
        fun Context.getPackageInfoCompat(
            packageName: String,
            flags: PackageInfoFlagsCompat,
        ): PackageInfo? = this.packageManager.getPackageInfoCompat(packageName, flags)

        /**
         * Retrieve overall information about an application package that is
         * installed on the system.
         *
         * @see PackageManager.getPackageInfo
         * @throws NameNotFoundException if no such package is available to the caller.
         */
        @Throws(NameNotFoundException::class)
        fun PackageManager.getPackageInfoCompat(
            packageName: String,
            flags: PackageInfoFlagsCompat,
        ): PackageInfo? = compat.getPackageInfo(this, packageName, flags)

        /**
         * Determine the best service to handle for a given Intent.
         *
         * @param intent An intent containing all of the desired specification
         *            (action, data, type, category, and/or component).
         * @param flags Additional option flags to modify the data returned.
         * @return Returns a [ResolveInfo] object containing the final service intent
         *         that was determined to be the best action. Returns null if no
         *         matching service was found.
         */
        fun PackageManager.resolveServiceCompat(
            intent: Intent,
            flags: ResolveInfoFlagsCompat,
        ): ResolveInfo? = compat.resolveService(this, intent, flags)

        /**
         * Retrieve all activities that can be performed for the given intent.
         *
         * @param intent The desired intent as per [resolveActivityCompat].
         * @param flags Additional option flags to modify the data returned. The most important is
         *  [MATCH_DEFAULT_ONLY], to limit the resolution to only those activities that support the
         *  [CATEGORY_DEFAULT]. Or, set [MATCH_ALL] to prevent any filtering of the results.
         * @return Returns a List of ResolveInfo objects containing one entry for
         *  each matching activity, ordered from best to worst. In other words, the first item
         *  is what would be returned by [resolveActivityCompat].
         *  If there are no matching activities, an empty list is returned.
         */
        fun PackageManager.queryIntentActivitiesCompat(
            intent: Intent,
            flags: ResolveInfoFlagsCompat,
        ): List<ResolveInfo> = compat.queryIntentActivities(this, intent, flags)

        /**
         * Determine the best action to perform for a given Intent. This is how
         * resolveActivity finds an activity if a class has not been
         * explicitly specified.
         *
         * Note: if using an implicit [Intent] (without an explicit
         * ComponentName specified), be sure to consider whether to set the
         * [MATCH_DEFAULT_ONLY] only flag. You need to do so to resolve the
         * activity in the same way that
         * [Context.startActivity] and [Intent.resolveActivity] do.
         *
         * @param intent An intent containing all of the desired specification
         *  (action, data, type, category, and/or component).
         * @param flags Additional option flags to modify the data returned. The
         *  most important is [MATCH_DEFAULT_ONLY], to limit the
         *  resolution to only those activities that support the
         *  [CATEGORY_DEFAULT].
         * @return Returns a ResolveInfo object containing the final activity intent
         *  that was determined to be the best action. Returns `null` if no
         *  matching activity was found. If multiple matching activities are
         *  found and there is no default set, returns a [ResolveInfo] object
         *  containing something else, such as the activity resolver.
         *
         *  @throws SecurityException on some Xiaomi phones if performing a cross-profile query #19711
         */
        fun PackageManager.resolveActivityCompat(
            intent: Intent,
            flags: ResolveInfoFlagsCompat = ResolveInfoFlagsCompat.EMPTY,
        ): ResolveInfo? = compat.resolveActivity(this, intent, flags)

        /**
         * Register a broadcast receiver.
         *
         * @receiver Context to retrieve service from.
         *
         * @param receiver The BroadcastReceiver to handle the broadcast.
         * @param filter Selects the Intent broadcasts to be received.
         * @param flags – If this receiver is listening for broadcasts sent from other apps—even other
         * apps that you own—use the [ContextCompat.RECEIVER_EXPORTED] flag.
         *
         * If instead this receiver is listening only for broadcasts sent by your app, or from the system,
         * use the [ContextCompat.RECEIVER_NOT_EXPORTED] flag.
         *
         * @return The first sticky intent found that matches filter, or null if there are none.
         *
         * @see ContextCompat.registerReceiver
         * @see Context.registerReceiver
         */
        fun Context.registerReceiverCompat(
            receiver: BroadcastReceiver?,
            filter: IntentFilter,
            @ContextCompat.RegisterReceiverFlags flags: Int,
        ) = ContextCompat.registerReceiver(this, receiver, filter, flags)
    }
}

/**
 * Sets the tooltip text for the view.
 *
 * On API 26 and later, this method calls through to {@link View#setTooltipText(CharSequence)}.
 *
 * Prior to API 26, this method sets or clears (when tooltipText is {@code null}) the view's
 * {@code OnLongClickListener} and {@code OnHoverListener}. A tooltip-like sub-panel will be
 * created on long-click or mouse hover.
 *
 * @receiver the view on which to set the tooltip text
 * @param tooltipText the tooltip text
 */
fun View.setTooltipTextCompat(tooltipText: CharSequence?) = TooltipCompat.setTooltipText(this, tooltipText)

inline fun <reified T : Serializable> Bundle.requireSerializableCompat(key: String): T =
    requireNotNull(compat.getSerializable(this, key, T::class.java)) {
        "key: '$key' not found or null"
    }
