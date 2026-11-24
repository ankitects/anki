// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.servicelayer

import android.content.Context
import android.os.Build
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.ext.fsrsEnabled
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.getLanguageDebugInfo
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
        val languageInfo = getLanguageDebugInfo(info)
        val appLocale =
            when (languageInfo.appLocale) {
                LanguageUtil.SYSTEM_LANGUAGE_TAG -> "System language"
                null -> "Error"
                else -> languageInfo.appLocale
            }
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
            Language = System: ${languageInfo.systemLocale} | App: $appLocale | Backend: ${languageInfo.backendLocales?.joinToString()}
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
        CollectionManager.withOpenColOrNull { config.fsrsEnabled }
    } catch (e: Throwable) {
        // Error and Exception paths are the same, so catch Throwable
        Timber.w(e)
        null
    }
