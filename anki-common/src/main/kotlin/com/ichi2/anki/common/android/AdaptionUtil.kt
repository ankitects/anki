/*
 * Copyright (c) 2020 gaoyingjun@xiaomi.com
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
package com.ichi2.anki.common.android

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.CompatHelper.Companion.queryIntentActivitiesCompat
import com.ichi2.anki.compat.MATCH_DEFAULT_ONLY_L
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import com.ichi2.anki.compat.ResolveInfoFlagsCompat
import timber.log.Timber
import java.util.Locale

object AdaptionUtil {
    private var sHasRunWebBrowserCheck = false
    private var sHasWebBrowser = true

    fun hasWebBrowser(context: Context): Boolean {
        if (sHasRunWebBrowserCheck) {
            return sHasWebBrowser
        }
        sHasWebBrowser = checkHasWebBrowser(context)
        sHasRunWebBrowserCheck = true
        return sHasWebBrowser
    }

    val isUserATestClient: Boolean
        get() =
            try {
                ActivityManager.isUserAMonkey() ||
                    isRunningUnderFirebaseTestLab
            } catch (e: Exception) {
                Timber.w(e)
                false
            }
    private val isRunningUnderFirebaseTestLab: Boolean
        get() =
            try {
                isRunningUnderFirebaseTestLab(appContext.contentResolver)
            } catch (e: Exception) {
                Timber.w(e)
                false
            }

    private fun isRunningUnderFirebaseTestLab(contentResolver: ContentResolver): Boolean {
        // https://firebase.google.com/docs/test-lab/android/android-studio#modify_instrumented_test_behavior_for
        val testLabSetting = Settings.System.getString(contentResolver, "firebase.test.lab")
        return "true" == testLabSetting
    }

    private fun checkHasWebBrowser(context: Context): Boolean {
        // The test monkey often gets stuck on the Shared Decks WebView, ignore it as it shouldn't crash.
        if (isUserATestClient) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW, "http://www.google.com".toUri())
        val pm = context.packageManager
        val list = pm.queryIntentActivitiesCompat(intent, ResolveInfoFlagsCompat.of(MATCH_DEFAULT_ONLY_L))
        for (ri in list) {
            if (!isValidBrowser(ri)) {
                continue
            }

            // If we aren't a restricted device, any browser will do
            if (!isXiaomiRestrictedLearningDevice) {
                return true
            }
            // If we are a restricted device, only a system browser will do
            if (isSystemApp(ri.activityInfo.packageName, pm)) {
                return true
            }
        }
        // Either there are no web browsers, or we're a restricted learning device and there's no system browsers.
        return false
    }

    private fun isValidBrowser(ri: ResolveInfo?): Boolean {
        // https://stackoverflow.com/a/57223246/
        return ri?.activityInfo != null && ri.activityInfo.exported
    }

    private fun isSystemApp(
        packageName: String?,
        pm: PackageManager,
    ): Boolean {
        return if (packageName != null) {
            try {
                val info = pm.getPackageInfoCompat(packageName, PackageInfoFlagsCompat.EMPTY) ?: return false
                val appInfo = info.applicationInfo ?: return false
                appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.w(e)
                false
            }
        } else {
            false
        }
    }

    /**
     * True if the device is a Xiaomi of the "Archytas" or "Archimedes" series,
     * as known as "Xiaomi AI teacher", which hasn't some features like a browser.
     * See [#5867](https://github.com/ankidroid/Anki-Android/pull/5867)
     * for the original issue and implementation.
     */
    val isXiaomiRestrictedLearningDevice by lazy {
        "Xiaomi".equals(Build.MANUFACTURER, ignoreCase = true) &&
            ("Archytas".equals(Build.PRODUCT, ignoreCase = true) || "Archimedes".equals(Build.PRODUCT, ignoreCase = true))
    }

    /** See: https://en.wikipedia.org/wiki/Vivo_(technology_company)  */

    val isVivo: Boolean
        @SuppressLint("LocaleRootUsage")
        get() {
            val manufacturer = Build.MANUFACTURER ?: return false
            return manufacturer.lowercase(Locale.ROOT) == "vivo"
        }

    val isMiui: Boolean by lazy {
        val ctx: Context = appContext

        // https://stackoverflow.com/questions/47610456/how-to-detect-miui-rom-programmatically-in-android
        fun isIntentResolved(intent: Intent): Boolean =
            (ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null)

        return@lazy try {
            isIntentResolved(
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            ) ||
                isIntentResolved(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity",
                        ),
                    ),
                ) ||
                isIntentResolved(
                    Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").addCategory(Intent.CATEGORY_DEFAULT),
                ) ||
                isIntentResolved(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.powercenter.PowerSettings",
                        ),
                    ),
                )
        } catch (e: Exception) {
            Timber.w(e)
            false
        }
    }
}
