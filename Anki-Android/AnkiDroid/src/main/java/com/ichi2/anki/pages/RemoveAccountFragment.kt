/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.pages

import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.annotation.CallSuper
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import timber.log.Timber

/**
 * Displays a WebView to remove an AnkiWeb account
 *
 * We use this as we need to control the 'after login' URL
 *
 * AnkiWeb currently redirects from 'https://ankiweb.net/account/remove-account ->
 *
 * * https://ankiweb.net/account/login
 *
 * then to either:
 *
 * * 'https://ankiweb.net/account/verify-email'
 * * 'https://ankiweb.net/decks'
 *
 * @see com.ichi2.anki.MyAccount.openRemoveAccountScreen
 * @see com.ichi2.anki.pages.PageFragment
 */
@NeedsTest("pressing 'back' on this screen closes it")
class RemoveAccountFragment :
    Fragment(R.layout.fragment_page),
    OnWebViewRecreatedListener {
    private lateinit var webViewLayout: SafeWebViewLayout

    /**
     * A count of the redirects performed, to ensure we don't get into an infinite loop
     */
    private var redirectCount = 0

    /**
     * Redirect from post-login pages (such as 'verify account') to the required page
     */
    private fun maybeRedirectToRemoveAccount(url: String): Boolean {
        if (!urlsToRedirect.any { urlToRedirect -> url.startsWith(urlToRedirect) }) {
            Timber.v("not redirecting to remove account: url does not match")
            return false
        }
        redirectCount++
        if (redirectCount > 3) {
            Timber.w("not redirecting to remove account: over the redirect limit")
            return false
        }

        Timber.i("redirecting to 'remove account'")
        webViewLayout.loadUrl(getString(R.string.remove_account_url))
        return true
    }

    @CallSuper
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        webViewLayout = view.findViewById(R.id.webview_layout)
        setupWebView()
        view.findViewById<MaterialToolbar?>(R.id.toolbar)?.apply {
            title = getString(R.string.remove_account)
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    /**
     * Creates a WebViewClient that handles URL loading
     */
    private fun createWebViewClient(): SafeWebViewClient {
        return object : SafeWebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                @Suppress("DEPRECATION")
                return shouldOverrideUrlLoading(view, request?.url.toString())
            }

            @Suppress("OVERRIDE_DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?,
            ): Boolean {
                if (url == null) return false
                return maybeRedirectToRemoveAccount(url)
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?,
            ) {
                super.onPageFinished(view, url)
                if (url == null) return
                maybeRedirectToRemoveAccount(url)
            }
        }
    }

    private fun setupWebView() {
        webViewLayout.isVisible = true
        with(webViewLayout.settings) {
            javaScriptEnabled = true
            displayZoomControls = false
            builtInZoomControls = true
            setSupportZoom(true)
        }
        webViewLayout.setWebViewClient(createWebViewClient())
        // BUG: custom sync server doesn't use this URL
        val url = getString(R.string.remove_account_url)
        Timber.i("Loading '$url'")
        webViewLayout.loadUrl(url)
    }

    override fun onWebViewRecreated(webView: WebView) {
        setupWebView()
    }

    companion object {
        /**
         * A page shown if an account requires re-verification
         *
         * > **Email Sent**
         * > We've sent an email to email@exmaple.com to confirm your address is valid. If that is not your correct address, please change it.
         * > **Status**
         * > Your email provider has accepted the email we sent. If you do not see it in the next few minutes, please check your spam folder. Please click the link in the email to proceed.
         * > If you would like to try sending another email, you can do so below. You can try again up to 3 times.
         * > [Send Again]
         */
        private const val INVALID_VERIFY_ACCOUNT_URL = "https://ankiweb.net/account/verify-email"

        /** A page shown if an account can log in normally */
        private const val INVALID_AFTER_LOGIN_URL = "https://ankiweb.net/decks"

        // WARN: the above URLs were not accessible in either onPageFinished or shouldOverrideUrlLoading
        // This URL is, but may be subject to change
        private const val AFTER_LOGIN_URL = "https://ankiweb.net/account/login?afterAuth=1"

        val urlsToRedirect = listOf(AFTER_LOGIN_URL, INVALID_AFTER_LOGIN_URL, INVALID_VERIFY_ACCOUNT_URL)
    }
}
