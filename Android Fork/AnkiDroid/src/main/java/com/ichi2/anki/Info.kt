// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2015 Tim Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.ichi2.anki.common.appName
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.databinding.ActivityInfoBinding
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.utils.IntentUtil.canOpenIntent
import com.ichi2.utils.IntentUtil.tryOpenIntent
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.ViewGroupUtils.setRenderWorkaround
import com.ichi2.utils.toRGBHex
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

private const val CHANGE_LOG_URL = "https://docs.ankidroid.org/changelog.html"

/**
 * Shows an about box, which is a small HTML page.
 *
 * Typically for the AnkiDroid changelog
 */
class Info :
    AnkiActivity(R.layout.activity_info),
    BaseSnackbarBuilderProvider,
    OnWebViewRecreatedListener {
    private val binding by viewBinding(ActivityInfoBinding::bind)
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = binding.buttons
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        // If the page crashes, we do not want to display it again (#7135 maybe)
        InitialActivity.setUpgradedToLatestVersion(sharedPrefs())
        setViewBinding(binding)
        enableToolbar()
        applyInsets()
        if (BuildConfig.SHOW_DONATE_LINKS) {
            binding.donate.setOnClickListener { openUrl(R.string.link_opencollective_donate) }
        } else {
            binding.donate.isVisible = false
        }
        title = "$appName v$pkgVersionName"
        binding.leftButton.run {
            if (canOpenMarketUri()) {
                setText(CommonString.info_rate)
                setOnClickListener {
                    tryOpenIntent(
                        this@Info,
                        AnkiDroidApp.getMarketIntent(this@Info),
                    )
                }
            } else {
                visibility = View.GONE
            }
        }
        onBackPressedCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    if (binding.webView.canGoBack()) binding.webView.goBack()
                }
            }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        binding.rightButton.run {
            text = getString(CommonString.dialog_continue)
            setOnClickListener { close() }
        }
        setupWebView()
    }

    override fun onWebViewRecreated() {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Apply Theme colors
        val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground, android.R.attr.textColor))
        val backgroundColor = typedArray.getColor(0, -1)
        val textColor = typedArray.getColor(1, -1).toRGBHex()
        typedArray.recycle()

        val anchorTextThemeColor = getColorFromAttr(this, android.R.attr.colorAccent)
        val anchorTextColor = anchorTextThemeColor.toRGBHex()
        val background = backgroundColor.toRGBHex()

        binding.webView.setBackgroundColor(backgroundColor)
        binding.webView.settings.allowFileAccess = true
        binding.webView.settings.allowContentAccess = true
        setRenderWorkaround(this)

        binding.webView.setWebChromeClient(
            object : WebChromeClient() {
                override fun onProgressChanged(
                    view: WebView,
                    progress: Int,
                ) {
                    // Hide the progress indicator when the page has finished loaded
                    if (progress == 100) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            },
        )

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.setWebViewClient(
            object : SafeWebViewClient() {
                override fun onPageFinished(
                    view: WebView,
                    url: String,
                ) {
                /* The order of below javascript code must not change (this order works both in debug and release mode)
                 *  or else it will break in any one mode.
                 */
                    @Suppress("ktlint:standard:max-line-length")
                    view.loadUrl(
                        """javascript:document.body.style.setProperty("color", "$textColor");
                            x=document.getElementsByTagName("a");
                            for(i=0; i<x.length; i++){
                              x[i].style.color="$anchorTextColor";
                            }
                            document.getElementsByTagName("h1")[0].style.color="$textColor";
                            x=document.getElementsByTagName("h2");
                            for(i=0; i<x.length; i++){
                              x[i].style.color="#E37068";
                            }
                            document.body.style.setProperty("background", "$background");""",
                    )
                    if (!BuildConfig.SHOW_DONATE_LINKS) {
                        // remove donation links, keeping the text
                        view.evaluateJavascript(
                            """document.querySelectorAll('a[href*="opencollective.com"]')
                                .forEach((a) => a.replaceWith(...a.childNodes));""",
                            null,
                        )
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    // Excludes the url that are opened inside the changelog.html
                    // and redirect the user to the browser
                    val url = request?.url?.toString() ?: return false
                    if (url == CHANGE_LOG_URL) {
                        return false
                    }
                    this@Info.openUrl(url)
                    return true
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean,
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    onBackPressedCallback.isEnabled = binding.webView.canGoBack()
                }
            },
        )
        binding.webView.loadUrl("/android_asset/changelog.html")
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    /**
     * Applies edge-to-edge insets.
     *
     * The app bar's background spans the full width and draws behind the status bar; only its
     * content is inset. Everything below it is held inside the safe area.
     */
    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            // the toolbar's parent: padding on the Toolbar counts towards its minHeight,
            // which would shrink the app bar's content below actionBarSize
            binding.toolbarContainer.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            // no corner clearance: the buttons carry a 12dp horizontal margin, so the arc
            // intrudes less than the navigation bar already clears
            binding.content.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun close() {
        setResult(RESULT_OK)
        finish()
    }

    private fun canOpenMarketUri(): Boolean =
        try {
            canOpenIntent(this, AnkiDroidApp.getMarketIntent(this))
        } catch (e: Exception) {
            Timber.w(e)
            false
        }
}
