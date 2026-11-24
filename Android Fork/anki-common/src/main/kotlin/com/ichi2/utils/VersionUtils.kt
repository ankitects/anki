// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.ichi2.anki.common.android.ApplicationContextInitializer
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import timber.log.Timber

/**
 * Created by Tim on 11/04/2015.
 */
object VersionUtils {
    /**
     * Get the package versionName as defined in the manifest.
     */
    val pkgVersionName: String
        get() {
            val pkgVersion = "?"
            val context: Context = applicationInstance ?: return pkgVersion
            try {
                val pInfo = context.getPackageInfoCompat(context.packageName, PackageInfoFlagsCompat.EMPTY) ?: return pkgVersion
                return pInfo.versionName ?: pkgVersion
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w(e, "Couldn't find package named %s", context.packageName)
            }
            return pkgVersion
        }

    /**
     * Get the package versionCode as defined in the manifest.
     */
    val pkgVersionCode: Long
        get() {
            val context: Context = applicationInstance ?: return 0
            try {
                val pInfo = context.getPackageInfoCompat(context.packageName, PackageInfoFlagsCompat.EMPTY)
                if (pInfo == null) {
                    Timber.w("getPackageInfo failed")
                    return 0
                }
                val versionCode = PackageInfoCompat.getLongVersionCode(pInfo)
                Timber.d("getPkgVersionCode() is %s", versionCode)
                return versionCode
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.e(e, "Couldn't find package named %s", context.packageName)
            } catch (npe: NullPointerException) {
                if (context.packageManager == null) {
                    Timber.e("getPkgVersionCode() null package manager?")
                } else if (context.packageName == null) {
                    Timber.e("getPkgVersionCode() null package name?")
                }
                CrashReportService.sendExceptionReport(npe, "Unexpected exception getting version code?")
                Timber.e(npe, "Unexpected exception getting version code?")
            }
            return 0
        }

    private val applicationInstance: Context?
        get() =
            ApplicationContextInitializer.instanceOrNull
                ?: run {
                    Timber.w("AnkiDroid instance not set")
                    null
                }

    /**
     * Return whether the package version code is set to that for release version
     * @return whether build number in manifest version code is '3'
     */
    val isReleaseVersion: Boolean
        get() {
            val versionCode = pkgVersionCode.toString()
            Timber.d("isReleaseVersion() versionCode: %s", versionCode)
            return versionCode[versionCode.length - 3] == '3'
        }
}
