// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 YongWoo Shin <onlym6659@gmail.com>

package com.ichi2.anki.shareddeck

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.database.MatrixCursor
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.BackEventCompat
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.shareddeck.SharedDecksActivity.Companion.HTTP_STATUS_TOO_MANY_REQUESTS
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.testutils.webView
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboMenuItem
import org.robolectric.shadows.ShadowDialog
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Tests for [SharedDecksActivity] */
@RunWith(AndroidJUnit4::class)
class SharedDecksActivityTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    /** Issue 23089: callbacks can arrive before the first fragment transaction is executed. */
    @Test
    fun `rapid download callbacks start only one download`() {
        val activity = startSharedDeckDownload()

        activity.receiveDownload()
        activity.receiveDownload("https://ankiweb.net/svc/shared/download-deck/5678?t=key")
        activity.supportFragmentManager.executePendingTransactions()

        assertEquals(1, activity.supportFragmentManager.fragments.size)
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        verify(activity.downloadManager).enqueue(any())
    }

    @Test
    fun `back during an active download asks before leaving the screen`() {
        val activity = startSharedDeckDownload()
        val downloadScreen = activity.openDownloadScreen()

        activity.pressBackOnDownloadScreen()

        assertCancelDownloadPromptShown()
        assertSame(downloadScreen, activity.downloadScreen)

        activity.confirmCancelDownload()

        verify(activity.downloadManager).remove(1L)
        assertNull(activity.downloadScreen)
    }

    @Test
    fun `download callback keeps the existing download screen`() {
        val activity = startSharedDeckDownload()
        activity.receiveDownload()
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.fragments.single()

        activity.receiveDownload()
        activity.supportFragmentManager.executePendingTransactions()

        assertSame(fragment, activity.supportFragmentManager.fragments.single())
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        verify(activity.downloadManager).enqueue(any())
    }

    @Test
    fun `download can start while closing the previous download screen is pending`() {
        val activity = startSharedDeckDownload()
        activity.receiveDownload()
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.fragments.single()

        activity.supportFragmentManager.popBackStack()
        activity.receiveDownload()
        activity.supportFragmentManager.executePendingTransactions()

        assertNotSame(fragment, activity.supportFragmentManager.fragments.single())
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        verify(activity.downloadManager, times(2)).enqueue(any())
    }

    @Test
    fun `predictive back dismisses the download screen if the download fails during the gesture`() {
        val activity = startSharedDeckDownload()
        activity.openDownloadScreen()

        activity.startBackGesture()
        activity.failDownload()
        retryButton.assertExists("the download failed during the gesture")

        activity.completeBackGesture()

        assertNull(ShadowDialog.getLatestDialog(), "a failed download should not offer cancellation")
        assertNull(activity.downloadScreen)
    }

    @Test
    fun `cancelling a download removes it before starting the next download`() {
        val activity = startSharedDeckDownload()
        whenever(activity.downloadManager.enqueue(any())).thenReturn(1L, 2L)
        activity.receiveDownload()
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.fragments.single()

        composeRule
            .onNodeWithText(getResourceString(CommonString.cancel_download))
            .performSemanticsAction(SemanticsActions.OnClick)
        clickAlertDialogButton { positiveButton }

        verify(activity.downloadManager).remove(1L)
        verify(activity.downloadManager).enqueue(any())
        assertTrue(activity.supportFragmentManager.fragments.isEmpty())
        assertEquals(0, activity.supportFragmentManager.backStackEntryCount)

        activity.receiveDownload("https://ankiweb.net/svc/shared/download-deck/5678?t=key")
        activity.supportFragmentManager.executePendingTransactions()

        assertNotSame(fragment, activity.supportFragmentManager.fragments.single())
        assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
        verify(activity.downloadManager, times(2)).enqueue(any())
    }

    @Test
    fun `rate limit while logged out asks to log in or sign up`() {
        val activity = startSharedDecks()

        activity.receiveTooManyRequests()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(getResourceString(CommonString.not_logged_in_title), dialog.title)
        assertEquals(getResourceString(CommonString.shared_decks_ankiweb_login_limit), dialog.message)
        assertEquals(getResourceString(R.string.shared_decks_url), activity.lastLoadedUrl)
    }

    @Test
    fun `log in from the rate limit dialog opens the AnkiWeb login page`() {
        val activity = startSharedDecks()
        activity.receiveTooManyRequests()

        clickAlertDialogButton { positiveButton }

        assertEquals(getResourceString(R.string.shared_decks_login_url), activity.lastLoadedUrl)
    }

    @Test
    fun `sign up from the rate limit dialog opens the AnkiWeb sign up page`() {
        val activity = startSharedDecks()
        activity.receiveTooManyRequests()

        clickAlertDialogButton { requireNotNull(neutralButton) }

        assertEquals(getResourceString(R.string.shared_decks_sign_up_url), activity.lastLoadedUrl)
    }

    @Test
    fun `repeated rate limits show a single dialog`() {
        val activity = startSharedDecks()

        activity.receiveTooManyRequests()
        activity.receiveTooManyRequests()

        assertEquals(1, ShadowDialog.getShownDialogs().size)
    }

    @Test
    fun `rate limit while logged in to AnkiWeb leaves the page alone`() {
        CookieManager.getInstance().setCookie("https://ankiweb.net", "has_auth=1")
        val activity = startSharedDecks()

        activity.receiveTooManyRequests()

        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(getResourceString(R.string.shared_decks_url), activity.lastLoadedUrl)
    }

    @Test
    fun `logging in to AnkiWeb returns to the shared deck`() {
        val activity = startOnSharedDeck()

        activity.logInToAnkiWeb()

        assertEquals(SHARED_DECK, activity.webView.url)
    }

    @Test
    fun `back after logging in to AnkiWeb returns to the search results`() {
        val searchResults = "https://ankiweb.net/shared/decks/Japanese"
        val activity = startSharedDecks()
        activity.visit(searchResults)
        activity.visit(SHARED_DECK)

        activity.logInToAnkiWeb()

        activity.visit(SHARED_DECK)
        assertEquals(SHARED_DECK, activity.webView.url)

        activity.onBackPressedDispatcher.onBackPressed()

        assertFalse(activity.isFinishing, "Back should return to the search results instead of closing the screen")
        assertEquals(searchResults, activity.webView.url)
    }

    @Test
    fun `back after logging in to AnkiWeb returns to each earlier shared page once`() {
        val sharedDecks = getResourceString(R.string.shared_decks_url)
        val searchResults = "https://ankiweb.net/shared/decks/Japanese"
        val activity = startSharedDecks()
        activity.visit(sharedDecks)
        activity.visit(searchResults)
        activity.visit(SHARED_DECK)
        activity.logInToAnkiWeb()
        activity.visit(AFTER_LOGIN)
        activity.visit(SHARED_DECK)

        activity.pressBack()
        assertEquals(searchResults, activity.webView.url)
        activity.pressBack()
        assertEquals(sharedDecks, activity.webView.url)
        activity.pressBack()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `back from home after logging in to AnkiWeb closes the screen`() {
        val activity = startOnSharedDeck()
        activity.logInToAnkiWeb()
        activity.visit(SHARED_DECK)

        activity.onOptionsItemSelected(RoboMenuItem(R.id.home))
        activity.visit(getResourceString(R.string.shared_decks_url))
        activity.pressBack()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `logging in after AnkiWeb refuses a download returns to the deck page`() {
        val activity = startOnSharedDeck()
        activity.visit(DECK_DOWNLOAD)

        activity.logInToAnkiWeb()

        assertEquals(SHARED_DECK, activity.webView.url)
    }

    @Test
    fun `AnkiWeb login without a session stays on AnkiWeb`() {
        val activity = startOnSharedDeck()
        activity.openAnkiWebLogin()

        activity.visit(AFTER_LOGIN)

        assertEquals(AFTER_LOGIN, activity.webView.url)
    }

    @Test
    fun `back while returning after login must not reopen login`() {
        val activity = startOnSharedDeck()
        activity.logInToAnkiWeb()
        // loadUrl was requested, but the return navigation has not committed yet.
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse(
            activity.webView.url?.startsWith(getResourceString(R.string.shared_decks_login_url)) == true,
            "Back reopened the login page while the return to the deck was pending",
        )
    }

    @Test
    fun `returning after a login does not log the deck URL at info`() {
        val activity = startOnSharedDeck()

        val logs = captureInfoLogs { activity.logInToAnkiWeb() }

        assertEquals(SHARED_DECK, activity.webView.url)
        assertFalse(logs.any { it.contains(SHARED_DECK) }, "Deck URL was logged at info or above: $logs")
    }

    @Test
    fun `onRenderProcessGone recovers webview and reloads content`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java).setup()
        val activity = controller.get()
        saveControllerForCleanup(controller)

        val layout = activity.safeWebViewLayout
        val initialWebView = activity.webView

        layout.onRenderProcessGone(initialWebView)

        assertTrue(shadowOf(initialWebView).wasDestroyCalled(), "Dead WebView should be destroyed")
        assertNull(initialWebView.parent, "Dead WebView should be removed from parent")

        val recoveredWebView = activity.webView
        assertNotSame(initialWebView, recoveredWebView, "A new WebView instance should be created")
        assertFalse(shadowOf(recoveredWebView).wasDestroyCalled(), "Recovered WebView should not be destroyed")
        val expectedUrl = getResourceString(R.string.shared_decks_url)
        assertEquals(expectedUrl, shadowOf(recoveredWebView).lastLoadedUrl)
    }

    @Test
    fun `onDestroy cleans up recovered webview`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java).setup()
        val activity = controller.get()

        val layout = activity.safeWebViewLayout
        val initialWebView = activity.webView

        layout.onRenderProcessGone(initialWebView)
        val recoveredWebView = activity.webView

        controller.destroy()

        assertTrue(shadowOf(recoveredWebView).wasDestroyCalled(), "Recovered WebView should be destroyed on activity destroy")
    }

    @Test
    @SuppressLint("NewApi")
    fun `back exits while the recovered WebView is loading`() {
        val activity = startSharedDecks()
        val initialWebView = activity.webView
        val homeUrl = getResourceString(R.string.shared_decks_url)
        val deckUrl = "https://ankiweb.net/shared/info/1"
        shadowOf(initialWebView).pushEntryToHistory(homeUrl)
        initialWebView.webViewClient.doUpdateVisitedHistory(initialWebView, homeUrl, false)
        assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
        shadowOf(initialWebView).pushEntryToHistory(deckUrl)
        initialWebView.webViewClient.doUpdateVisitedHistory(initialWebView, deckUrl, false)
        assertTrue(initialWebView.canGoBack())
        assertTrue(activity.onBackPressedDispatcher.hasEnabledCallbacks())
        // Invoke the installed client, as Android does when the renderer exits.
        assertTrue(initialWebView.webViewClient.onRenderProcessGone(initialWebView, mock()))
        assertNotSame(initialWebView, activity.webView)
        assertFalse(activity.webView.canGoBack())
        assertFalse(activity.isFinishing)
        // Robolectric has not delivered the replacement page's history callback yet.
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing, "Back should exit while the replacement WebView has no history")
    }

    private fun startOnSharedDeck(): SharedDecksActivity =
        startSharedDecks().apply {
            visit(getResourceString(R.string.shared_decks_url))
            visit(SHARED_DECK)
        }

    private fun SharedDecksActivity.openAnkiWebLogin() {
        receiveTooManyRequests()
        clickAlertDialogButton { positiveButton }
        visit(getResourceString(R.string.shared_decks_login_url))
    }

    private fun SharedDecksActivity.logInToAnkiWeb() {
        openAnkiWebLogin()
        CookieManager.getInstance().setCookie("https://ankiweb.net", "has_auth=1")
        visit(AFTER_LOGIN)
    }

    private fun SharedDecksActivity.visit(url: String) {
        if (webView.copyBackForwardList().currentItem?.url != url) {
            shadowOf(webView).pushEntryToHistory(url)
        }
        webViewClient.doUpdateVisitedHistory(webView, url, false)
        webViewClient.onPageFinished(webView, url)
        // Robolectric clears every history entry; real WebView keeps the current page.
        if (webView.copyBackForwardList().size == 0) {
            shadowOf(webView).pushEntryToHistory(url)
        }
    }

    private fun SharedDecksActivity.pressBack() {
        onBackPressedDispatcher.onBackPressed()
        if (!isFinishing) visit(requireNotNull(webView.url))
    }

    private fun startSharedDecks(): SharedDecksActivity {
        val controller =
            Robolectric
                .buildActivity(SharedDecksActivity::class.java)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(controller)
        return controller.get()
    }

    private fun startSharedDeckDownload(): SharedDecksActivity =
        startSharedDecks().apply {
            downloadManager =
                mock {
                    on { enqueue(any()) } doReturn 1L
                    on { query(any()) } doAnswer { MatrixCursor(emptyArray<String>()) }
                }
        }

    private fun SharedDecksActivity.receiveDownload(url: String = DECK_DOWNLOAD) {
        shadowOf(webView).downloadListener.onDownloadStart(
            url,
            "AnkiDroid",
            "attachment; filename=\"blank.apkg\"",
            "application/octet-stream",
            0L,
        )
    }

    private fun SharedDecksActivity.openDownloadScreen(): Fragment {
        receiveDownload()
        supportFragmentManager.executePendingTransactions()
        return requireNotNull(downloadScreen)
    }

    private val SharedDecksActivity.downloadScreen: Fragment?
        get() = supportFragmentManager.fragments.singleOrNull()

    private fun SharedDecksActivity.failDownload() {
        whenever(downloadManager.query(any())).thenAnswer {
            MatrixCursor(arrayOf(DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON)).apply {
                addRow(arrayOf(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_UNKNOWN))
            }
        }
        sendBroadcast(Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 1L))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun SharedDecksActivity.pressBackOnDownloadScreen() {
        onBackPressedDispatcher.onBackPressed()
        supportFragmentManager.executePendingTransactions()
    }

    private fun SharedDecksActivity.startBackGesture() {
        onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0.3f, BackEventCompat.EDGE_LEFT))
    }

    private fun SharedDecksActivity.completeBackGesture() = pressBackOnDownloadScreen()

    private fun assertCancelDownloadPromptShown() {
        val dialog = assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog, "ask before abandoning the active download")
        assertEquals(getResourceString(CommonString.cancel_download_question_title), dialog.title)
    }

    private fun SharedDecksActivity.confirmCancelDownload() {
        clickAlertDialogButton { positiveButton }
        supportFragmentManager.executePendingTransactions()
    }

    private val retryButton
        get() = composeRule.onNodeWithText(getResourceString(CommonString.try_again))

    private fun captureInfoLogs(block: () -> Unit): List<String> {
        val messages = mutableListOf<String>()
        val tree =
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    if (priority >= Log.INFO) messages += message
                }
            }
        Timber.plant(tree)
        try {
            block()
        } finally {
            Timber.uproot(tree)
        }
        return messages
    }

    private fun SharedDecksActivity.receiveTooManyRequests() {
        val response = mock<WebResourceResponse> { on { statusCode } doReturn HTTP_STATUS_TOO_MANY_REQUESTS }
        webViewClient.onReceivedHttpError(webView, mock(), response)
    }

    private val SharedDecksActivity.safeWebViewLayout: SafeWebViewLayout
        get() = findViewById(R.id.web_view)

    private val SharedDecksActivity.webView: WebView
        get() = safeWebViewLayout.webView

    private val SharedDecksActivity.lastLoadedUrl: String?
        get() = shadowOf(webView).lastLoadedUrl

    companion object {
        private const val SHARED_DECK = "https://ankiweb.net/shared/info/1234"
        private const val DECK_DOWNLOAD = "https://ankiweb.net/svc/shared/download-deck/1234?t=key"
        private const val AFTER_LOGIN = "https://ankiweb.net/account/login?afterAuth=1"
    }
}
