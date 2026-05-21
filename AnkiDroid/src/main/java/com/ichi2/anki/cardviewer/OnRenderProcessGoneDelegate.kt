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
package com.ichi2.anki.cardviewer

import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.libanki.CardId
import com.ichi2.utils.cancelable
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

/**
 * Fix for:
 * #5780 - WebView Renderer OOM crashes reviewer
 * #8459 - WebView Renderer crash dialog displays when app is minimised (Android 11 - Google Pixel 3A)
 */
open class OnRenderProcessGoneDelegate(
    val target: AbstractFlashcardViewer,
) {
    lateinit var lifecycle: Lifecycle

    /**
     * Last card that the WebView Renderer crashed on.
     * If we get 2 crashes on the same card, then we likely have an infinite loop and want to exit gracefully.
     */
    private var lastCrashingCardId: CardId? = null

    /** Fix: #5780 - WebView Renderer OOM crashes reviewer  */
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        Timber.i("Obtaining write lock for card")
        val writeLock = target.writeLock
        val cardWebView = target.webView
        Timber.i("Obtained write lock for card")
        try {
            writeLock.lock()
            if (cardWebView == null || cardWebView != view) {
                // A view crashed that wasn't ours.
                // We have nothing to handle. Returning false is a desire to crash, so return true.
                Timber.i("Unrelated WebView Renderer terminated. Crashed: %b", detail.didCrash())
                return true
            }
            Timber.e("WebView Renderer process terminated. Crashed: %b", detail.didCrash())
            target.destroyWebViewFrame()

            // Only show one message per branch
            when {
                !canRecoverFromWebViewRendererCrash() -> {
                    Timber.e("Unrecoverable WebView Render crash")
                    if (!activityIsMinimised()) displayFatalError(detail)
                    target.finish()
                    return true
                }
                !activityIsMinimised() -> {
                    // #8459 - if the activity is minimised, this is much more likely to happen multiple times and it is
                    // likely not a permanent error due to a bad card, so don't increment mLastCrashingCardId
                    val currentCardId = target.currentCard!!.id
                    if (webViewRendererLastCrashedOnCard(currentCardId)) {
                        Timber.e("Web Renderer crash loop on card: %d", currentCardId)
                        displayRenderLoopDialog(currentCardId, detail)
                        return true
                    }

                    // This logic may need to be better defined. The card could have changed by the time we get here.
                    lastCrashingCardId = currentCardId
                    displayNonFatalError(detail)
                }
                else -> Timber.d("WebView crashed while app was minimised - OOM was safe to handle silently")
            }

            // If we get here, the error is non-fatal and we should re-render the WebView
            target.recreateWebViewFrame()
        } finally {
            writeLock.unlock()
            Timber.d("Relinquished writeLock")
        }
        target.displayCardQuestion()

        // We handled the crash and can continue.
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    protected open fun displayFatalError(detail: RenderProcessGoneDetail) {
        if (activityIsMinimised()) {
            Timber.d("Not showing toast - screen isn't visible")
            return
        }
        val errorMessage = target.resources.getString(R.string.webview_crash_fatal, getErrorCause(detail))
        showThemedToast(target, errorMessage, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    protected open fun displayNonFatalError(detail: RenderProcessGoneDetail) {
        if (activityIsMinimised()) {
            Timber.d("Not showing toast - screen isn't visible")
            return
        }
        val nonFatalError = target.resources.getString(R.string.webview_crash_nonfatal, getErrorCause(detail))
        showThemedToast(target, nonFatalError, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    protected fun getErrorCause(detail: RenderProcessGoneDetail): String {
        // It's not necessarily an OOM crash, false implies a general code which is for "system terminated".
        val errorCauseId = if (detail.didCrash()) R.string.webview_crash_unknown else R.string.webview_crash_oom
        return target.resources.getString(errorCauseId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    protected open fun displayRenderLoopDialog(
        currentCardId: CardId,
        detail: RenderProcessGoneDetail,
    ) {
        val cardInformation = currentCardId.toString()
        val res = target.resources
        val errorDetails =
            if (detail.didCrash()) {
                res.getString(
                    R.string.webview_crash_unknwon_detailed,
                )
            } else {
                res.getString(R.string.webview_crash_oom_details)
            }
        AlertDialog.Builder(target).show {
            title(R.string.webview_crash_loop_dialog_title)
            message(text = res.getString(R.string.webview_crash_loop_dialog_content, cardInformation, errorDetails))
            positiveButton(R.string.dialog_ok) {
                onCloseRenderLoopDialog()
            }
            cancelable(false)
        }
    }

    /**
     * Issue 8459
     * On Android 11, the WebView regularly OOMs even after .onStop() has been called,
     * but this does not cause .onDestroy() to be called
     *
     * We do not want to show toasts or increment the "crash" counter if this occurs. Just handle the issue
     */
    private fun activityIsMinimised(): Boolean {
        // See diagram on https://developer.android.com/topic/libraries/architecture/lifecycle#lc
        // STARTED is after .start(), the activity goes to CREATED after .onStop()
        lifecycle = target.lifecycle
        return !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun webViewRendererLastCrashedOnCard(cardId: CardId): Boolean = lastCrashingCardId != null && lastCrashingCardId == cardId

    private fun canRecoverFromWebViewRendererCrash(): Boolean =
        // DEFECT
        // If we don't have a card to render, we're in a bad state. The class doesn't currently track state
        // well enough to be able to know exactly where we are in the initialisation pipeline.
        // so it's best to mark the crash as non-recoverable.
        // We should fix this, but it's very unlikely that we'll ever get here. Logs will tell

        // Revisit webViewCrashedOnCard() if changing this. Logic currently assumes we have a card.
        target.currentCard != null

    protected fun onCloseRenderLoopDialog() = target.finish()
}
