/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.ichi2.anki.AnkiDroidApp
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
     * Get package name as defined in the manifest.
     */
    val appName: String
        get() {
            var pkgName = AnkiDroidApp.TAG
            val context: Context = applicationInstance ?: return AnkiDroidApp.TAG
            try {
                val pInfo = context.getPackageInfoCompat(context.packageName, PackageInfoFlagsCompat.EMPTY)
                if (pInfo == null) {
                    Timber.w("Couldn't find package named %s", context.packageName)
                    return pkgName
                }
                pInfo.applicationInfo?.let {
                    pkgName = context.getString(it.labelRes)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.e(e, "Couldn't find package named %s", context.packageName)
            }
            return pkgName
        }

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
