// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.view.WindowManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.utils.cancelable
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber

open class PageChromeClient : WebChromeClient() {
    override fun onJsAlert(
        view: WebView,
        url: String?,
        message: String?,
        result: JsResult?,
    ): Boolean {
        Timber.d("Displaying alert() dialog")
        try {
            AlertDialog.Builder(view.context).show {
                message?.let { message(text = message) }
                positiveButton(CommonString.dialog_ok) { result?.confirm() }
                setOnCancelListener { result?.cancel() }
            }
        } catch (e: IllegalStateException) {
            // window count is over max!!
            Timber.w(e, "onJsAlert: message ignored")
            // Report without opening a consent dialog while the window limit is exhausted.
            CrashReportService.sendExceptionReport(e, "onJsAlert:windowCount", additionalInfo = "$url: $message", onlyIfSilent = true)
            // Returning false asks WebView to show another dialog, which can fail for the same reason.
            result?.cancel()
        } catch (e: WindowManager.BadTokenException) {
            Timber.w(e, "onJsAlert: activity destroyed?")
            result?.cancel()
        }

        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String?,
        message: String?,
        result: JsResult?,
    ): Boolean {
        Timber.d("Displaying confirm() dialog")
        try {
            AlertDialog.Builder(view.context).show {
                message?.let { message(text = message) }
                positiveButton(CommonString.dialog_ok) { result?.confirm() }
                negativeButton(CommonString.dialog_cancel) { result?.cancel() }
                cancelable(false)
            }
        } catch (e: IllegalStateException) {
            Timber.w(e, "onJsConfirm: message ignored")
            // Report without opening a consent dialog while the window limit is exhausted.
            CrashReportService.sendExceptionReport(e, "onJsConfirm:windowCount", additionalInfo = "$url: $message", onlyIfSilent = true)
            result?.cancel()
        } catch (e: WindowManager.BadTokenException) {
            Timber.w(e, "onJsConfirm")
            result?.cancel()
        }
        return true
    }
}
