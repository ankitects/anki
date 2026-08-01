/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import android.content.Context
import android.os.Build
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.getWebViewInfo
import org.acra.util.Installation
import timber.log.Timber
import net.ankiweb.rsdroid.BuildConfig as BackendBuildConfig

object DebugInfoService {
    /**
     * Retrieves the debug info based in different parameters of the app.
     *
     * Note that the `FSRS` parameter can be null if the collection doesn't exist or the config is not set.
     */
    suspend fun getDebugInfo(info: Context): String {
        val webviewInfo = getWebViewInfo(info)
        // isFSRSEnabled is null on startup
        val isFSRSEnabled = getFSRSStatus()
        return """
            AnkiDroid Version = $pkgVersionName (${BuildConfig.GIT_COMMIT_HASH})
            Backend Version = ${BuildConfig.BACKEND_VERSION} (${BackendBuildConfig.ANKI_DESKTOP_VERSION} ${BackendBuildConfig.ANKI_COMMIT_HASH})
            Android Version = ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            ProductFlavor = ${BuildConfig.FLAVOR}
            Device Info = ${Build.MANUFACTURER} | ${Build.BRAND} | ${Build.DEVICE} | ${Build.PRODUCT} | ${Build.MODEL} | ${Build.HARDWARE}
            WebView Info = [${webviewInfo.packageName} | ${webviewInfo.versionCode}]: ${webviewInfo.userAgent}
            ACRA UUID = ${Installation.id(info)}
            FSRS = ${BackendBuildConfig.FSRS_VERSION} (Enabled: $isFSRSEnabled)
            Crash Reports Enabled = ${isSendingCrashReports(info)}
            """.trimIndent()
            // A Markdown newline is two spaces followed by '\n', this avoids the need for
            // code fences
            .replace("\n", "  \n")
    }

    private fun isSendingCrashReports(context: Context): Boolean = CrashReportService.isEnabled(context, false)
}

/**
 * Whether the Free Spaced Repetition Scheduler is enabled
 *
 * Note: this can return `null` if the collection is not openable
 */
suspend fun getFSRSStatus(): Boolean? =
    try {
        CollectionManager.withOpenColOrNull { config.get<Boolean>("fsrs", false) }
    } catch (e: Throwable) {
        // Error and Exception paths are the same, so catch Throwable
        Timber.w(e)
        null
    }
