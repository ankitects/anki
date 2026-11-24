// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

package com.ichi2.anki.shareddeck

import android.app.DownloadManager
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup.MarginLayoutParams
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebBackForwardList
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.analytics.reportPotentiallyDeadCode
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.utils.ext.requireSystemService
import com.ichi2.anki.databinding.ActivitySharedDecksBinding
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.utils.FileNameAndExtension
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber
import java.io.Serializable
import kotlin.random.Random
import com.ichi2.anki.common.android.R as CommonR

/**
 * Browse AnkiWeb shared decks with the functionality to download and import them.
 *
 * @see SharedDecksDownloadFragment
 */
class SharedDecksActivity :
    AnkiActivity(R.layout.activity_shared_decks),
    OnWebViewRecreatedListener {
    private val binding by viewBinding(ActivitySharedDecksBinding::bind)
    lateinit var downloadManager: DownloadManager

    private var shouldHistoryBeCleared = false

    private var isReturningAfterLogin = false

    private val pagesBeforeLogin = mutableListOf<String>()

    private var loginRequiredDialog: AlertDialog? = null

    private val allowedHosts = listOf(Regex("""^(?:.*\.)?ankiweb\.net$"""), Regex("""^ankiuser\.net$"""), Regex("""^ankisrs\.net$"""))
    private val onBackPressedCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (isReturningAfterLogin) return
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    pagesBeforeLogin.removeLastOrNull()?.let { returnTo(it) } ?: run {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
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
    internal inner class SharedDeckWebViewClient : SafeWebViewClient() {
        override fun doUpdateVisitedHistory(
            view: WebView?,
            url: String?,
            isReload: Boolean,
        ) {
            super.doUpdateVisitedHistory(view, url, isReload)
            if (isReturningAfterLogin && url?.startsWith(SHARED_PAGES_PREFIX) == true) {
                binding.webView.clearHistory()
                isReturningAfterLogin = false
            }
            updateBackPressedCallback()
            if (url?.startsWith(AFTER_LOGIN_URL) == true && isLoggedInToAnkiWeb()) {
                returnToSharedDecks()
            }
        }

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            // Clear history if mShouldHistoryBeCleared is true and set it to false
            if (shouldHistoryBeCleared) {
                binding.webView.clearHistory()
                shouldHistoryBeCleared = false
                updateBackPressedCallback()
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

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            super.onReceivedHttpError(view, request, errorResponse)

            if (errorResponse?.statusCode != HTTP_STATUS_TOO_MANY_REQUESTS) return

            // If a user is logged in, they see: "Daily limit exceeded; please try again tomorrow."
            // We have nothing we can do here
            if (isLoggedInToAnkiWeb()) return

            // The following cases are handled below:
            // "Please log in to download more decks." - on clicking "Download"
            // "Please log in to perform more searches" - on searching
            showLoginRequiredDialog()
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
    }

    internal fun showLoginRequiredDialog() {
        if (loginRequiredDialog?.isShowing == true) return
        Timber.i("HTTP 429, asking the user to log in")
        loginRequiredDialog =
            AlertDialog.Builder(this).show {
                title(CommonString.not_logged_in_title)
                message(CommonString.shared_decks_ankiweb_login_limit)
                positiveButton(text = TR.sentenceCase.logIn) { redirectToLogin() }
                neutralButton(CommonString.sign_up) { redirectToSignUp() }
                negativeButton(CommonString.dialog_cancel)
            }
    }

    /** Loads the AnkiWeb login page in the WebView, where the user can log in. */
    private fun redirectToLogin() {
        val url = getString(R.string.shared_decks_login_url)
        Timber.i("HTTP 429, redirecting to login: '$url'")
        binding.webView.loadUrl(url)
    }

    private fun redirectToSignUp() {
        val url = getString(R.string.shared_decks_sign_up_url)
        Timber.i("HTTP 429, redirecting to sign up: '$url'")
        binding.webView.loadUrl(url)
    }

    private fun returnToSharedDecks() {
        if (isReturningAfterLogin) return
        val history = binding.webView.copyBackForwardList()
        val sharedPages =
            history.urlsBeforeCurrent
                .filter { it.startsWith(SHARED_PAGES_PREFIX) }
                .toList()
        val url = sharedPages.lastOrNull() ?: return
        Timber.d("Logged in to AnkiWeb, returning to '$url'")
        pagesBeforeLogin += sharedPages.dropLast(1)
        returnTo(url)
    }

    private fun returnTo(url: String) {
        isReturningAfterLogin = true
        binding.webView.loadUrl(url)
    }

    private fun updateBackPressedCallback() {
        onBackPressedCallback.isEnabled = binding.webView.canGoBack() || pagesBeforeLogin.isNotEmpty()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val webViewClient = SharedDeckWebViewClient()

    companion object {
        const val SHARED_DECKS_DOWNLOAD_FRAGMENT = "SharedDecksDownloadFragment"
        const val ARG_DOWNLOAD_FILE = "DownloadFile"

        const val HTTP_STATUS_TOO_MANY_REQUESTS = 429

        private const val AFTER_LOGIN_URL = "https://ankiweb.net/account/login?afterAuth=1"
        private const val SHARED_PAGES_PREFIX = "https://ankiweb.net/shared/"
    }

    // Show WebView with AnkiWeb shared decks with the functionality to capture downloads and import decks.
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)
        // after super.onCreate: enableEdgeToEdge accesses window.decorView, which resolves
        // the window background before Themes.setTheme has applied the user's theme,
        // and the wrong background is visible behind the WebView while a page loads
        //
        // statusBarStyle matches the status bar theme of the rest of the app
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setTitle(CommonString.download_deck)
        setupEdgeToEdge()

        binding.webviewToolbar.setTitleTextColor(getColor(CommonR.color.white))
        setSupportActionBar(binding.webviewToolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        downloadManager = requireSystemService<DownloadManager>()

        setupWebView()
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
    }

    /** Opens the AnkiWeb shared decks page, handing any download it starts to [SharedDecksDownloadFragment] */
    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.loadUrl(resources.getString(R.string.shared_decks_url))
        binding.webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            // If the activity/fragment lifecycle has already begun teardown process,
            // avoid handling the download, as FragmentManager.commit will throw
            if (!supportFragmentManager.isStateSaved) {
                // A previous callback may have queued a fragment addition or removal.
                supportFragmentManager.executePendingTransactions()
                if (supportFragmentManager.findFragmentByTag(SHARED_DECKS_DOWNLOAD_FRAGMENT) != null) {
                    reportPotentiallyDeadCode("SharedDecksActivity.OverlappingDownload")
                    CrashReportService.sendExceptionReport(
                        IllegalStateException("Shared-deck download screen already open"),
                        "SharedDecksActivity::onDownloadStart",
                        onlyIfSilent = true,
                    )
                    return@setDownloadListener
                }
                val sharedDecksDownloadFragment = SharedDecksDownloadFragment()
                sharedDecksDownloadFragment.arguments =
                    Bundle().apply {
                        putSerializable(ARG_DOWNLOAD_FILE, DownloadFile(url, userAgent, contentDisposition, mimetype))
                    }
                supportFragmentManager.commit {
                    add(
                        R.id.shared_decks_fragment_container,
                        sharedDecksDownloadFragment,
                        SHARED_DECKS_DOWNLOAD_FRAGMENT,
                    ).addToBackStack(null)
                }
            }
        }
        binding.webView.setWebViewClient(webViewClient)
    }

    override fun onWebViewRecreated() {
        onBackPressedCallback.isEnabled = false
        pagesBeforeLogin.clear()
        setupWebView()
    }

    /** Applies edge-to-edge insets for the screen */
    private fun setupEdgeToEdge() {
        // systemBars (not just statusBars) so a landscape 3-button navigation bar,
        // which is a side inset, is also cleared
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarContainer) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            insets
        }
        // margins rather than padding: WebView does not support padding
        // ime() so the page (e.g. AnkiWeb's search/login) is not covered by the keyboard
        ViewCompat.setOnApplyWindowInsetsListener(binding.webView) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime(),
                )
            view.updateLayoutParams<MarginLayoutParams> {
                leftMargin = bars.left
                rightMargin = bars.right
                bottomMargin = bars.bottom
            }
            insets
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.download_shared_decks_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.home) {
            // R.id.home refers to a custom 'home' menu item defined in your app resources (res/menu/...).
            shouldHistoryBeCleared = true
            pagesBeforeLogin.clear()
            binding.webView.loadUrl(resources.getString(R.string.shared_decks_url))
        } else if (item.itemId == android.R.id.home) {
            // android.R.id.home refers to the system-provided "up" button in the app toolbar
            onBackPressedCallback.isEnabled = false
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        loginRequiredDialog?.dismiss()
        binding.webView.destroy()
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

/**
 * Whether the WebView's cookies indicate a logged-in AnkiWeb session.
 *
 * Used to tell a download failure caused by AnkiWeb's download limit for anonymous users
 * apart from other failures: only a logged-out user can fix a 429 by logging in.
 */
internal fun isLoggedInToAnkiWeb(): Boolean =
    try {
        // ankiweb currently (2024-09-25) sets two cookies:
        // * `ankiweb`, which is base64-encoded JSON
        // * `has_auth`, which is 1
        // cookies are null after the user logs out, or if the site is first visited
        CookieManager.getInstance().getCookie("https://ankiweb.net")?.contains("has_auth=1") == true
    } catch (e: Exception) {
        Timber.w(e, "Could not determine login status")
        false
    }

private val WebBackForwardList.urlsBeforeCurrent: Sequence<String>
    get() = (0 until currentIndex).asSequence().map { getItemAtIndex(it).url }
