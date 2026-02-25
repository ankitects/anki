/*
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>
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

package com.ichi2.anki

import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.fragment.app.commit
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE
import com.ichi2.anki.databinding.ActivitySharedDecksBinding
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.FileNameAndExtension
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber
import java.io.Serializable
import kotlin.random.Random

/**
 * Browse AnkiWeb shared decks with the functionality to download and import them.
 *
 * @see SharedDecksDownloadFragment
 */
class SharedDecksActivity : AnkiActivity(R.layout.activity_shared_decks) {
    private val binding by viewBinding(ActivitySharedDecksBinding::bind)
    lateinit var downloadManager: DownloadManager

    private var shouldHistoryBeCleared = false

    private val allowedHosts = listOf(Regex("""^(?:.*\.)?ankiweb\.net$"""), Regex("""^ankiuser\.net$"""), Regex("""^ankisrs\.net$"""))
    private val onBackPressedCallback =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack()
            }
        }

    /**
     * Handle condition when page finishes loading and history needs to be cleared.
     * Currently, this condition arises when user presses the home button on the toolbar.
     *
     * History should not be cleared before the page finishes loading otherwise there would be
     * an extra entry in the history since the previous page would not get cleared.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal inner class SharedDeckWebViewClient : WebViewClient() {
        private var redirectTimes = 0

        override fun doUpdateVisitedHistory(
            view: WebView?,
            url: String?,
            isReload: Boolean,
        ) {
            super.doUpdateVisitedHistory(view, url, isReload)
            onBackPressedCallback.isEnabled = binding.webView.canGoBack()
        }

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            // Clear history if mShouldHistoryBeCleared is true and set it to false
            if (shouldHistoryBeCleared) {
                binding.webView.clearHistory()
                shouldHistoryBeCleared = false
            }
            super.onPageFinished(view, url)
        }

        /**
         * Prevent the WebView from loading urls which arent needed for importing shared decks.
         * This is to prevent potential misuse, such as bypassing content restrictions or
         * using the AnkiDroid WebView as a regular browser to bypass browser blocks,
         * which could lead to procrastination.
         */
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            val host = request?.url?.host
            if (host != null) {
                if (allowedHosts.any { regex -> regex.matches(host) }) {
                    return super.shouldOverrideUrlLoading(view, request)
                }
            }

            request?.url?.let { super@SharedDecksActivity.openUrl(it) }

            return true
        }

        private val cookieManager: CookieManager by lazy {
            CookieManager.getInstance()
        }

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal val isLoggedInToAnkiWeb: Boolean
            get() {
                try {
                    // cookies are null after the user logs out, or if the site is first visited
                    val cookies = cookieManager.getCookie("https://ankiweb.net") ?: return false
                    // ankiweb currently (2024-09-25) sets two cookies:
                    // * `ankiweb`, which is base64-encoded JSON
                    // * `has_auth`, which is 1
                    return cookies.contains("has_auth=1")
                } catch (e: Exception) {
                    Timber.w(e, "Could not determine login status")
                    return false
                }
            }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)

            if (errorResponse?.statusCode != HTTP_STATUS_TOO_MANY_REQUESTS) return

            // If a user is logged in, they see: "Daily limit exceeded; please try again tomorrow."
            // We have nothing we can do here
            if (isLoggedInToAnkiWeb) return

            // The following cases are handled below:
            // "Please log in to download more decks." - on clicking "Download"
            // "Please log in to perform more searches" - on searching
            redirectUserToSignUpOrLogin()
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            // Set mShouldHistoryBeCleared to false if error occurs since it might have been true
            shouldHistoryBeCleared = false
            super.onReceivedError(view, request, error)
        }

        /**
         * Redirects the user to a login page
         *
         * A message is shown informing the user they need to log in to download more decks
         *
         * If the user has not logged in **inside AnkiDroid** then the message provides
         * the user with an action to sign up
         *
         * The redirect is not performed if [redirectTimes] is 3 or more
         */
        private fun redirectUserToSignUpOrLogin() {
            // inform the user they need to log in as they've hit a rate limit
            showSnackbar(R.string.shared_decks_login_required, LENGTH_INDEFINITE) {
                if (isLoggedIn()) return@showSnackbar

                // If a user is not logged in inside AnkiDroid, assume they have no AnkiWeb account
                // and give them the option to sign up
                setAction(R.string.sign_up) {
                    binding.webView.loadUrl(getString(R.string.shared_decks_sign_up_url))
                }
            }

            // redirect user to /account/login
            // TODO: the result of login is typically redirecting the user to their decks
            // this should be improved

            if (redirectTimes++ < 3) {
                val url = getString(R.string.shared_decks_login_url)
                Timber.i("HTTP 429, redirecting to login: '$url'")
                binding.webView.loadUrl(url)
            } else {
                // Ensure that we do not have an infinite redirect
                Timber.w("HTTP 429 redirect limit exceeded, only displaying message")
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val webViewClient = SharedDeckWebViewClient()

    companion object {
        const val SHARED_DECKS_DOWNLOAD_FRAGMENT = "SharedDecksDownloadFragment"
        const val DOWNLOAD_FILE = "DownloadFile"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val HTTP_STATUS_TOO_MANY_REQUESTS = 429
    }

    // Show WebView with AnkiWeb shared decks with the functionality to capture downloads and import decks.
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        setTitle(R.string.download_deck)

        binding.webviewToolbar.setTitleTextColor(getColor(R.color.white))
        setSupportActionBar(binding.webviewToolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.loadUrl(resources.getString(R.string.shared_decks_url))
        binding.webView.webViewClient = WebViewClient()
        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            // If the activity/fragment lifecycle has already begun teardown process,
            // avoid handling the download, as FragmentManager.commit will throw
            if (!supportFragmentManager.isStateSaved) {
                val sharedDecksDownloadFragment = SharedDecksDownloadFragment()
                sharedDecksDownloadFragment.arguments =
                    bundleOf(
                        DOWNLOAD_FILE to DownloadFile(url, userAgent, contentDisposition, mimetype),
                    )
                supportFragmentManager.commit {
                    add(
                        R.id.shared_decks_fragment_container,
                        sharedDecksDownloadFragment,
                        SHARED_DECKS_DOWNLOAD_FRAGMENT,
                    ).addToBackStack(null)
                }
            }
        }

        binding.webView.webViewClient = webViewClient
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.download_shared_decks_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.home) {
            // R.id.home refers to a custom 'home' menu item defined in your app resources (res/menu/...).
            shouldHistoryBeCleared = true
            binding.webView.loadUrl(resources.getString(R.string.shared_decks_url))
        } else if (item.itemId == android.R.id.home) {
            // android.R.id.home refers to the system-provided "up" button in the app toolbar
            onBackPressedCallback.isEnabled = false
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        SafeWebViewLayout.destroyWebView(binding.webView)
        super.onDestroy()
    }
}

/**
 * Used for sending URL, user agent, content disposition and mime type to SharedDecksDownloadFragment.
 */
data class DownloadFile(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String,
) : Serializable {
    /** @return a filename with the provided extension */
    fun toFileName(extension: String): String =
        URLUtil
            .guessFileName(
                this.url,
                this.contentDisposition,
                this.mimeType,
            ).let { maybeCorruptFileName ->
                // #17573: https://issuetracker.google.com/issues/382864232
                // guessFileName may return ".bin" as an extension
                (
                    FileNameAndExtension.fromString(maybeCorruptFileName)
                        // default if maybeCorruptFileName doesn't contain a '.'
                        // Add randomness to avoid file name conflicts between different decks
                        ?: FileNameAndExtension.fromString("download-${Random.nextInt()}$extension")!!
                ).replaceExtension(extension = extension) // enforce the provided extension
                    .toString()
            }
}
