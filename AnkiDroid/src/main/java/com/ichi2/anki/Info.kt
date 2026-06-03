// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2015 Tim Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.databinding.ActivityInfoBinding
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.utils.IntentUtil.canOpenIntent
import com.ichi2.utils.IntentUtil.tryOpenIntent
import com.ichi2.utils.VersionUtils.appName
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
    BaseSnackbarBuilderProvider {
    private val binding by viewBinding(ActivityInfoBinding::bind)

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = binding.buttons
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        val res = resources
        val type = intent.getIntExtra(TYPE_EXTRA, TYPE_NEW_VERSION)
        // If the page crashes, we do not want to display it again (#7135 maybe)
        if (type == TYPE_NEW_VERSION) {
            val prefs = this.baseContext.sharedPrefs()
            InitialActivity.setUpgradedToLatestVersion(prefs)
        }
        setViewBinding(binding)
        enableToolbar()
        binding.donate.setOnClickListener { openUrl(R.string.link_opencollective_donate) }
        title = "$appName v$pkgVersionName"
        binding.webView.webChromeClient =
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
            }
        binding.leftButton.run {
            if (canOpenMarketUri()) {
                setText(R.string.info_rate)
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
        val onBackPressedCallback =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    if (binding.webView.canGoBack()) binding.webView.goBack()
                }
            }
        // Apply Theme colors
        val typedArray = theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground, android.R.attr.textColor))
        val backgroundColor = typedArray.getColor(0, -1)
        val textColor = typedArray.getColor(1, -1).toRGBHex()

        val anchorTextThemeColor = getColorFromAttr(this, android.R.attr.colorAccent)
        val anchorTextColor = anchorTextThemeColor.toRGBHex()

        binding.webView.setBackgroundColor(backgroundColor)
        binding.webView.settings.allowFileAccess = true
        binding.webView.settings.allowContentAccess = true
        setRenderWorkaround(this)
        when (type) {
            TYPE_NEW_VERSION -> {
                binding.rightButton.run {
                    text = res.getString(R.string.dialog_continue)
                    setOnClickListener { close() }
                }
                val background = backgroundColor.toRGBHex()
                binding.webView.loadUrl("/android_asset/changelog.html")
                binding.webView.settings.javaScriptEnabled = true
                binding.webView.webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String,
                        ) {
                        /* The order of below javascript code must not change (this order works both in debug and release mode)
                         *  or else it will break in any one mode.
                         */
                            @Suppress("ktlint:standard:max-line-length")
                            binding.webView.loadUrl(
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
                            onBackPressedCallback.isEnabled = view != null && view.canGoBack()
                        }
                    }
            }
            else -> finish()
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun close() {
        setResult(RESULT_OK)
        finishWithAnimation()
    }

    private fun canOpenMarketUri(): Boolean =
        try {
            canOpenIntent(this, AnkiDroidApp.getMarketIntent(this))
        } catch (e: Exception) {
            Timber.w(e)
            false
        }

    private fun finishWithAnimation() {
        finish()
    }

    companion object {
        const val TYPE_EXTRA = "infoType"
        const val TYPE_NEW_VERSION = 2
    }
}
