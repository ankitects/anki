// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shareddeck

import android.app.DownloadManager
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceResponse
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import androidx.fragment.app.commit
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.shareddeck.SharedDecksActivity.Companion.HTTP_STATUS_TOO_MANY_REQUESTS
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.testutils.insetsOf
import com.ichi2.testutils.webView
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.Locale

/**
 * Screenshot tests for [SharedDecksActivity]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.shareddeck.SharedDecksScreenshotTest"`
 */
class SharedDecksScreenshotTest : ScreenshotTest() {
    @Test
    fun sharedDecksPortrait() =
        withSharedDecks { activity ->
            activity.simulateNavigationBar()
            captureScreen("portrait")
        }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side. The toolbar and content clear both sides.
     */
    @Test
    fun sharedDecksLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withSharedDecks { activity ->
            activity.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    /** [SharedDecksDownloadFragment]: a full-screen overlay above the WebView */
    @Test
    fun downloadFragment(
        @TestParameter("-1", "0", "30", "100") percent: Int,
    ) = withSharedDecks { activity ->
        // show the fragment first: insets are only received by attached views
        activity.showDownloadFragment(percent)
        activity.simulateNavigationBar()
        val name =
            when (percent) {
                -1 -> "download_indeterminate"
                30 -> "download"
                else -> "download_$percent"
            }
        captureScreen(name)
    }

    /**
     * Regression coverage for long localized stat labels wrapping onto multiple lines:
     * both stat cards should match height and long words should hyphenate cleanly.
     */
    @Test
    fun downloadFragmentWrappedStatLabel() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMAN)
        try {
            RuntimeEnvironment.setQualifiers("+de")
            RuntimeEnvironment.setFontScale(1.5f)
            withSharedDecks { activity ->
                activity.showDownloadFragment(percent = 30)
                activity.simulateNavigationBar()
                captureScreen("download_wrapped_stat_label")
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun loginRequiredDialog() =
        withSharedDecks { activity ->
            activity.simulateNavigationBar()
            val response = mock<WebResourceResponse> { on { statusCode } doReturn HTTP_STATUS_TOO_MANY_REQUESTS }
            activity.webViewClient.onReceivedHttpError(activity.findViewById<SafeWebViewLayout>(R.id.web_view).webView, mock(), response)
            advanceRobolectricLooper()
            captureScreen("login_required_dialog")
        }

    private fun withSharedDecks(block: (SharedDecksActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                SharedDecksActivity::class.java,
                Intent(),
            )
        advanceRobolectricLooper()
        block(activity)
    }

    /**
     * Shows [SharedDecksDownloadFragment] as the activity's download listener would,
     * with a mocked [DownloadManager] reporting a stable in-progress download.
     * A [percent] of -1 makes progress unavailable, showing the indeterminate indicator.
     * A [percent] of 0 reports no bytes yet, showing the spinning ring.
     */
    private fun SharedDecksActivity.showDownloadFragment(percent: Int) {
        val animatesForever = percent <= 0
        if (animatesForever) {
            ShadowChoreographer.setPaused(true)
            ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        }
        downloadManager =
            mock {
                on { enqueue(any()) } doReturn 1L
                on { query(any()) } doAnswer {
                    if (percent < 0) throw IllegalArgumentException("Progress unavailable")
                    inProgressDownloadCursor(percent)
                }
            }
        val fragment =
            SharedDecksDownloadFragment().apply {
                arguments =
                    Bundle().apply {
                        putSerializable(
                            SharedDecksActivity.ARG_DOWNLOAD_FILE,
                            DownloadFile(
                                url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=token",
                                userAgent = "Mozilla/5.0",
                                contentDisposition = "attachment; filename=\"MyTestDeck.apkg\"",
                                mimeType = "application/octet-stream",
                            ),
                        )
                    }
            }
        supportFragmentManager.commit {
            add(
                R.id.shared_decks_fragment_container,
                fragment,
                SharedDecksActivity.SHARED_DECKS_DOWNLOAD_FRAGMENT,
            )
        }
        if (animatesForever) {
            // The animation never ends. Capture a fixed frame instead of draining it.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        } else {
            advanceRobolectricLooper()
        }
    }

    /** A download with known progress. */
    private fun inProgressDownloadCursor(percent: Int): Cursor =
        MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_REASON,
            ),
        ).apply {
            val totalBytes = 10_000_000L
            val downloadedBytes = totalBytes * percent / 100
            addRow(arrayOf<Any>(downloadedBytes, totalBytes, DownloadManager.STATUS_RUNNING, 0))
        }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a real device, and overlay a translucent band
     * where the nav bar would sit to see if content is drawn underneath it.
     */
    private fun SharedDecksActivity.simulateNavigationBar() {
        val navBarHeight = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                navBarHeight.toPx(targetContext),
                Gravity.BOTTOM,
            ),
        )
    }

    /**
     * As [simulateNavigationBar], but for landscape with 3-button navigation: the navigation bar
     * is a side inset, with the camera cutout on the opposite side.
     */
    private fun SharedDecksActivity.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(left = 32.dp))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                navBarWidth.toPx(targetContext),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END,
            ),
        )
    }
}
