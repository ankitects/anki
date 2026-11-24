// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.annotation.CallSuper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.utils.bottomCornerClearance
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
        setupEdgeToEdge(view)
        webViewLayout = view.findViewById(R.id.webview_layout)
        setupWebView()
        view.findViewById<MaterialToolbar?>(R.id.toolbar)?.apply {
            title = getString(CommonString.remove_account)
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    /** Applied here, not in `fragment_page.xml`: that layout is shared with [PageFragment] */
    private fun setupEdgeToEdge(view: View) {
        val webViewContainer = view.findViewById<View>(R.id.webview_container)
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            val withKeyboard =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime(),
                )
            // the toolbar's parent: the Toolbar's style sets its own horizontal padding
            root.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            webViewContainer.updatePadding(
                bottom = maxOf(withKeyboard.bottom, insets.bottomCornerClearance(webViewContainer)),
            )
            insets
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

    override fun onWebViewRecreated() {
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
