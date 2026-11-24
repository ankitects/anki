// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.ThemeUtils
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.ViewerResourceHandler
import com.ichi2.anki.compat.CompatHelper.Companion.resolveActivityCompat
import com.ichi2.anki.dialogs.TtsVoicesDialogFragment
import com.ichi2.anki.localizedErrorMessage
import com.ichi2.anki.pages.PostRequestBridge
import com.ichi2.anki.security.AppPermissions
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.showException
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.anki.utils.ext.packageManager
import com.ichi2.anki.utils.openUrl
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.themes.Themes
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.openAppSettingsScreen
import com.ichi2.utils.isBlockedCardScheme
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.stripDangerousPermissions
import com.ichi2.utils.title
import com.ichi2.utils.usesDangerousScheme
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

abstract class CardViewerFragment(
    @LayoutRes layout: Int,
) : Fragment(layout),
    OnWebViewRecreatedListener {
    abstract val viewModel: CardViewerViewModel
    protected abstract val webViewLayout: SafeWebViewLayout

    private val appPermission by lazy { AppPermissions(requireContext()) { showSnackbar(it) } }

    /** The [PermissionRequest] being asked about via [optInDialog] or [microphonePermissionLauncher] */
    private var activeRequest: PermissionRequest? = null

    /** The dialog asking the user to allow [activeRequest]. `null` if not shown */
    private var optInDialog: AlertDialog? = null

    /** Whether the user declined the opt-in: further requests are denied without a prompt */
    private var userDeclinedRecording = false

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Prefs.allowTemplatesToRecordAudio = isGranted

            if (isGranted) {
                activeRequest?.grantAudioCapture()
            } else {
                Timber.i("Denying audio capture permission to WebView")
                activeRequest?.deny()
                onMicrophonePermissionDenied()
            }
            activeRequest = null
        }

    /**
     * Offers to open the app settings, so the user may grant the microphone permission there.
     *
     * Only done if the system dialog is no longer shown after repeated denials.
     */
    private fun onMicrophonePermissionDenied() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.RECORD_AUDIO)) return

        AlertDialog.Builder(requireContext()).show {
            title(CommonString.permission_denied)
            message(CommonString.microphone_permission_denied_message)
            positiveButton(CommonString.dialog_ok) { openAppSettingsScreen() }
            negativeButton(CommonString.dialog_cancel)
        }
    }

    /**
     * Asks whether the card template may record audio, granting [request] if the user allows it.
     *
     * If AnkiDroid lacks the microphone permission, requests it first and defers [request] to
     * [microphonePermissionLauncher].
     *
     * @param canRecordAudio whether AnkiDroid holds [Manifest.permission.RECORD_AUDIO]
     */
    private fun askToAllowTemplateAudioRecording(
        request: PermissionRequest,
        canRecordAudio: Boolean,
    ) {
        fun decline() {
            userDeclinedRecording = true
            activeRequest = null
            request.deny()
        }

        activeRequest = request
        optInDialog =
            AlertDialog.Builder(requireContext()).show {
                message(CommonString.template_is_trying_to_record_audio)
                positiveButton(CommonString.dialog_allow) {
                    if (canRecordAudio) {
                        activeRequest = null
                        Prefs.allowTemplatesToRecordAudio = true
                        request.grantAudioCapture()
                    } else {
                        // the request stays active until microphonePermissionLauncher handles it
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                negativeButton(CommonString.dialog_cancel) { decline() }
                setOnCancelListener { decline() }
                setOnDismissListener { optInDialog = null }
            }
    }

    @CallSuper
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        setupWebView(savedInstanceState)
        setupErrorListeners()
        viewModel.eval.collectInViewLifecycle(Lifecycle.State.CREATED) { eval ->
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
            setBackgroundColor(ThemeUtils.getThemeAttrColor(requireContext(), android.R.attr.colorBackground))
            scrollBars = View.SCROLLBARS_OUTSIDE_OVERLAY
            setAcceptThirdPartyCookies(true)
            with(settings) {
                // Scale the whole card with CSS instead; text-only zoom breaks MathJax layout.
                textZoom = 100
                javaScriptEnabled = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = true
                domStorageEnabled = true
                // the cards are served over https, but their templates may use http:// resources, like scripts
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // allow videos to autoplay via our JavaScript eval
                mediaPlaybackRequiresUserGesture = false
            }
            PostRequestBridge(viewModel, viewLifecycleOwner.lifecycleScope).attachTo(this)
            loadDataWithBaseURL(
                PostRequestBridge.BASE_URL,
                onLoadInitialHtml(),
                "text/html",
                null,
                null,
            )
        }
    }

    private fun setupErrorListeners() {
        viewModel.onError.receiveAsFlow().collectInViewLifecycle { requireActivity().showException(it) }

        viewModel.onMediaError.collectInViewLifecycle {
            showMediaErrorSnackbar(it)
        }
        viewModel.onTtsError.collectInViewLifecycle {
            showSnackbar(it.localizedErrorMessage(requireContext()))
        }
    }

    protected open fun onCreateWebViewClient(savedInstanceState: Bundle?): CardViewerWebViewClient =
        CardViewerWebViewClient(savedInstanceState)

    protected open fun onCreateWebChromeClient() = CardViewerWebChromeClient()

    /**
     * Reconfigures the [WebView] after a render process crash by calling [setupWebView].
     *
     * Subclasses that override this method must call `super.onWebViewRecreated()`,
     * as the base implementation reapplies all clients, settings, and content.
     */
    override fun onWebViewRecreated() {
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
                    val message = getString(CommonString.missing_user_action_dialog_message, actionNumber)
                    AlertDialog.Builder(requireContext()).show {
                        setMessage(message)
                        setPositiveButton(CommonString.dialog_ok) { _, _ -> }
                        setNeutralButton(CommonString.help) { _, _ ->
                            openUrl(R.string.link_user_actions_help)
                        }
                    }
                }
                else -> {
                    if (isBlockedCardScheme(url.scheme)) {
                        Timber.w("URL blocked")
                        return true
                    }
                    val intent = appPermission.createUrlIntent(url)
                    if (intent == null) {
                        Timber.w("URL blocked: canLaunchExternalApps")
                        return true
                    }
                    try {
                        if (intent.hasCategory(Intent.CATEGORY_BROWSABLE)) {
                            startActivity(intent)
                        } else {
                            // Preserve the existing browser check for custom schemes.
                            openUrl(url)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Could not open url")
                        showSnackbar(CommonString.activity_start_failed)
                        // Consume failed launches: external pages must not load inside the card.
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
                if (!appPermission.checkCanLaunchExternalApps()) {
                    Timber.w("launch blocked: canLaunchExternalApps")
                    return
                }
                intent.stripDangerousPermissions()
                if (intent.usesDangerousScheme()) {
                    Timber.w("Blocked card-origin intent carrying dangerous data")
                    return
                }
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
            Timber.w("WebView error received")
            Timber.d("WebView error %d (%s): %s %s", error.errorCode, error.description, request.method, request.url)
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

        override fun onPermissionRequest(request: PermissionRequest) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE !in request.resources) {
                Timber.i("Denying permissions to WebView")
                request.deny()
                return
            }

            val canRecordAudio = Permissions.canRecordAudio(requireContext())
            if (canRecordAudio && Prefs.allowTemplatesToRecordAudio) {
                request.grantAudioCapture()
                return
            }

            // Prompt at most once at a time, and not again once declined: otherwise a template
            // could spam requests, pressuring a user into consenting
            if (activeRequest != null || userDeclinedRecording) {
                Timber.i("Denying audio capture permission to WebView without prompting")
                request.deny()
                return
            }

            askToAllowTemplateAudioRecording(request, canRecordAudio)
        }

        /**
         * Forgets [request] after the WebView invalidated it (e.g. on navigating away), closing
         * the opt-in dialog: granting or denying a cancelled request has no effect.
         */
        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (request != activeRequest) return
            Timber.i("WebView cancelled its audio capture request")
            activeRequest = null
            optInDialog?.dismiss()
        }
    }

    private fun showMediaErrorSnackbar(filename: String) {
        showSnackbar(getString(CommonString.card_viewer_could_not_find_image, filename)) {
            setAction(CommonString.help) { openUrl(R.string.link_faq_missing_media) }
        }
    }
}

/** Allows the WebView to capture audio */
private fun PermissionRequest.grantAudioCapture() {
    Timber.i("Granting audio capture permission to WebView")
    grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
}
