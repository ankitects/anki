/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.R
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.compat.CompatHelper.Companion.resolveActivityCompat
import com.ichi2.anki.dialogs.TtsVoicesDialogFragment
import com.ichi2.anki.localizedErrorMessage
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.packageManager
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.themes.Themes
import com.ichi2.utils.show
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

abstract class CardViewerFragment(
    @LayoutRes layout: Int,
) : Fragment(layout),
    OnWebViewRecreatedListener {
    abstract val viewModel: CardViewerViewModel
    protected abstract val webViewLayout: SafeWebViewLayout

    @CallSuper
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        setupWebView(savedInstanceState)
        setupErrorListeners()
        viewModel.eval.collectIn(lifecycleScope) { eval ->
            webViewLayout.evaluateJavascript(eval)
        }
    }

    override fun onStart() {
        super.onStart()
        // A subclass may call requireActivity().finish() to abort setup so skip viewModel access here
        if (activity?.isFinishing == true) return
        viewModel.setSoundPlayerEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        // If the activity is finishing, the ViewModel will be cleared shortly,
        // releasing the CardMediaPlayer via its addCloseable hook
        // (see CardViewerViewModel.cardMediaPlayer). We can skip the explicit
        // shutdown here.
        if (activity?.isFinishing == true) return
        if (!requireActivity().isChangingConfigurations) {
            viewModel.setSoundPlayerEnabled(false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webViewLayout.destroy() // stops <audio> playbacks
    }

    protected open fun onLoadInitialHtml(): String =
        stdHtml(
            context = requireContext(),
            nightMode = Themes.isNightTheme,
        )

    private fun setupWebView(savedInstanceState: Bundle?) {
        with(webViewLayout) {
            setWebViewClient(onCreateWebViewClient(savedInstanceState))
            setWebChromeClient(onCreateWebChromeClient())
            scrollBars = View.SCROLLBARS_OUTSIDE_OVERLAY
            setAcceptThirdPartyCookies(true)
            with(settings) {
                javaScriptEnabled = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                domStorageEnabled = true
                // allow videos to autoplay via our JavaScript eval
                mediaPlaybackRequiresUserGesture = false
            }
            loadDataWithBaseURL(
                viewModel.baseUrl(),
                onLoadInitialHtml(),
                "text/html",
                null,
                null,
            )
        }
    }

    private fun setupErrorListeners() {
        viewModel.onError
            .flowWithLifecycle(lifecycle)
            .onEach { errorMessage ->
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(R.string.vague_error)
                    .setMessage(errorMessage)
                    .show()
            }.launchIn(lifecycleScope)

        viewModel.onMediaError
            .onEach { showMediaErrorSnackbar(it) }
            .launchIn(lifecycleScope)

        viewModel.onTtsError
            .onEach { showSnackbar(it.localizedErrorMessage(requireContext())) }
            .launchIn(lifecycleScope)
    }

    protected open fun onCreateWebViewClient(savedInstanceState: Bundle?): CardViewerWebViewClient =
        CardViewerWebViewClient(savedInstanceState)

    protected open fun onCreateWebChromeClient() = CardViewerWebChromeClient()

    /**
     * Reconfigures the [WebView] after a render process crash by calling [setupWebView].
     *
     * Subclasses that override this method must call `super.onWebViewRecreated(webView)`,
     * as the base implementation reapplies all clients, settings, and content.
     */
    override fun onWebViewRecreated(webView: WebView) {
        setupWebView(null)
    }

    open inner class CardViewerWebViewClient(
        val savedInstanceState: Bundle?,
    ) : SafeWebViewClient() {
        private val resourceHandler = ViewerResourceHandler(requireContext())
        private var hasLoaded = false

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest,
        ): WebResourceResponse? = resourceHandler.shouldInterceptRequest(request)

        override fun onPageStarted(
            view: WebView?,
            url: String?,
            favicon: Bitmap?,
        ) {
            hasLoaded = false
        }

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            // clicking a `<a href="#">` link calls onPageFinished without calling onPageStarted,
            // so avoid reloading the card content after clicking such a link.
            if (!hasLoaded) {
                viewModel.onPageFinished(isAfterRecreation = savedInstanceState != null)
            }
            hasLoaded = true
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = handleUrl(view, request.url)

        protected open fun handleUrl(
            webView: WebView,
            url: Uri,
        ): Boolean {
            when (url.scheme) {
                "playsound" -> viewModel.playSoundFromUrl(url.toString())
                "videoended" -> viewModel.onVideoFinished()
                "videopause" -> viewModel.onVideoPaused()
                "tts-voices" -> TtsVoicesDialogFragment().show(childFragmentManager, null)
                "android-app" -> handleIntentUrl(url, Intent.URI_ANDROID_APP_SCHEME)
                "intent" -> handleIntentUrl(url, Intent.URI_INTENT_SCHEME)
                "missing-user-action" -> {
                    val actionNumber = url.toString().substringAfter(":")
                    val message = getString(R.string.missing_user_action_dialog_message, actionNumber)
                    AlertDialog.Builder(requireContext()).show {
                        setMessage(message)
                        setPositiveButton(R.string.dialog_ok) { _, _ -> }
                        setNeutralButton(R.string.help) { _, _ ->
                            openUrl(R.string.link_user_actions_help)
                        }
                    }
                }
                else -> {
                    try {
                        openUrl(url)
                    } catch (_: Throwable) {
                        Timber.w("Could not open url")
                        return false
                    }
                }
            }
            return true
        }

        private fun handleIntentUrl(
            url: Uri,
            flags: Int,
        ) {
            try {
                val intent = Intent.parseUri(url.toString(), flags)
                if (packageManager.resolveActivityCompat(intent) != null) {
                    startActivity(intent)
                } else {
                    val packageName = intent.getPackage() ?: return
                    val marketUri = "market://details?id=$packageName".toUri()
                    val marketIntent = Intent(Intent.ACTION_VIEW, marketUri)
                    Timber.d("Trying to open market uri %s", marketUri)
                    if (packageManager.resolveActivityCompat(marketIntent) != null) {
                        startActivity(marketIntent)
                    }
                }
            } catch (t: Throwable) {
                Timber.w("Unable to parse intent uri: %s because: %s", url, t.message)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            viewModel.mediaErrorHandler.processFailure(request) { filename: String ->
                showMediaErrorSnackbar(filename)
            }
        }
    }

    open inner class CardViewerWebChromeClient : WebChromeClient() {
        protected lateinit var paramView: View

        // used for displaying `<video>` in fullscreen.
        // This implementation requires configChanges="orientation" in the manifest
        // to avoid destroying the View if the device is rotated
        override fun onShowCustomView(
            paramView: View,
            paramCustomViewCallback: CustomViewCallback?,
        ) {
            this@CardViewerWebChromeClient.paramView = paramView
            val window = requireActivity().window
            (window.decorView as FrameLayout).addView(
                paramView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            // hide system bars
            with(WindowInsetsControllerCompat(window, window.decorView)) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        override fun onHideCustomView() {
            val window = requireActivity().window
            (window.decorView as FrameLayout).removeView(paramView)
            // show system bars back
            with(WindowInsetsControllerCompat(window, window.decorView)) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun showMediaErrorSnackbar(filename: String) {
        showSnackbar(getString(R.string.card_viewer_could_not_find_image, filename)) {
            setAction(R.string.help) { openUrl(R.string.link_faq_missing_media) }
        }
    }
}
