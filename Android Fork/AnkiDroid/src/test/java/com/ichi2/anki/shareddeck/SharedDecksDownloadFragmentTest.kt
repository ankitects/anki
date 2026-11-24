// noinspection MissingCopyrightHeader #17351

package com.ichi2.anki.shareddeck

import android.app.DownloadManager
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.webkit.CookieManager
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.shareddeck.SharedDecksDownloadFragment.Companion.getDeckPageUri
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.testutils.webView
import com.ichi2.utils.openInputStreamSafe
import com.ichi2.utils.positiveButton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [SharedDecksDownloadFragment] */
@RunWith(AndroidJUnit4::class)
class SharedDecksDownloadFragmentTest : RobolectricTest() {
    companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var originalCrashReporter: CrashReporter
    private val crashReporter = mock<CrashReporter>()

    @Before
    fun captureCrashReports() {
        originalCrashReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(crashReporter)
    }

    @After
    fun restoreCrashReporter() {
        CrashReportService.setReporter(originalCrashReporter)
    }

    @Test
    fun `completed download opens the existing file for import`() {
        val download = completedDownload()

        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(IntentHandler::class.java.name, intent.component?.className)
        assertEquals(download.uri, intent.data)
        assertTrue(intent.getBooleanExtra(SharedDecksDownloadFragment.EXTRA_IS_SHARED_DOWNLOAD, false))
        val input = assertNotNull(download.activity.contentResolver.openInputStreamSafe(intent.data!!))
        assertEquals("deck contents", input.bufferedReader().use { it.readText() })
    }

    @Test
    fun `filename collision imports the completed download instead of the older file`() {
        val download = startDownload()
        download.file.writeText("older deck")
        val completedFile = File(download.file.parentFile, "blank-1.apkg").apply { writeText("new deck") }

        download.complete(completedFile = completedFile)

        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        val input = assertNotNull(download.activity.contentResolver.openInputStreamSafe(intent.data!!))
        assertEquals("new deck", input.bufferedReader().use { it.readText() })
        assertEquals("older deck", download.file.readText())

        // Manually importing again must also use the completed download's file.
        download.withFileProvider {
            importButton.performSemanticsAction(SemanticsActions.OnClick)
        }
        val retryIntent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(intent.data, retryIntent.data)

        assertTrue(completedFile.delete())
        importButton.performSemanticsAction(SemanticsActions.OnClick)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(download.activity).nextStartedActivity)
        retryButton.assertExists()
    }

    @Test
    fun `missing completed file offers retry even if the requested filename exists`() {
        val download = startDownload()
        download.file.writeText("older deck")
        val completedFile = File(download.file.parentFile, "blank-1.apkg")
        assertFalse(completedFile.exists())

        download.complete(completedFile = completedFile)

        assertNull(shadowOf(download.activity).nextStartedActivity)
        retryButton.assertExists()
    }

    @Test
    fun `missing download location offers retry instead of importing the older file`() {
        val download = startDownload()
        download.file.writeText("older deck")

        download.complete(localUri = null)

        assertNull(shadowOf(download.activity).nextStartedActivity)
        retryButton.assertExists()
    }

    @Test
    fun `duplicate completion does not replace the completed file or start another import`() {
        val download = completedDownload()
        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        val otherFile = File(download.file.parentFile, "other.apkg").apply { writeText("other deck") }

        download.complete(completedFile = otherFile)

        assertNull(shadowOf(download.activity).nextStartedActivity)

        download.withFileProvider {
            importButton.performSemanticsAction(SemanticsActions.OnClick)
        }
        val manualIntent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(intent.data, manualIntent.data)
    }

    @Test
    fun `retry ignores the previous download and imports the new one`() {
        val download = startDownload()
        download.complete(status = DownloadManager.STATUS_FAILED)
        assertNull(shadowOf(download.activity).nextStartedActivity)

        whenever(download.activity.downloadManager.enqueue(any())).thenReturn(2L)
        retryButton.assertExists()
        retryButton.performSemanticsAction(SemanticsActions.OnClick)
        download.file.writeText("downloaded again")

        download.complete(downloadId = 1L)
        assertNull(shadowOf(download.activity).nextStartedActivity)

        download.complete(downloadId = 2L)
        assertNotNull(shadowOf(download.activity).nextStartedActivity)
    }

    @Test
    fun `retry binds the import button to the new completed file`() {
        val download = completedDownload()
        assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertTrue(download.file.delete())
        importButton.performSemanticsAction(SemanticsActions.OnClick)
        shadowOf(Looper.getMainLooper()).idle()

        whenever(download.activity.downloadManager.enqueue(any())).thenReturn(2L)
        retryButton.assertExists()
        retryButton.performSemanticsAction(SemanticsActions.OnClick)
        val completedFile = File(download.file.parentFile, "blank-1.apkg").apply { writeText("new deck") }
        download.complete(downloadId = 2L, completedFile = completedFile)
        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)

        download.withFileProvider {
            importButton.performSemanticsAction(SemanticsActions.OnClick)
        }

        val manualIntent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(intent.data, manualIntent.data)
        val input = assertNotNull(download.activity.contentResolver.openInputStreamSafe(manualIntent.data!!))
        assertEquals("new deck", input.bufferedReader().use { it.readText() })
    }

    @Test
    fun `completed download with a missing file offers retry instead of importing`() {
        val download = startDownload()
        assertFalse(download.file.exists())

        download.complete()

        assertNull(shadowOf(download.activity).nextStartedActivity)
        retryButton.assertExists()

        retryButton.performSemanticsAction(SemanticsActions.OnClick)
        verify(download.activity.downloadManager).remove(1L)
        download.file.writeText("downloaded again")
        download.complete()

        assertNotNull(shadowOf(download.activity).nextStartedActivity)
    }

    @Test
    fun `import button offers retry if the downloaded file has been deleted`() {
        val download = completedDownload()
        assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertTrue(download.file.delete())

        importButton.performSemanticsAction(SemanticsActions.OnClick)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(shadowOf(download.activity).nextStartedActivity)
        retryButton.assertExists()
    }

    @Test
    fun `removing the download fragment prevents late completion from starting an import`() {
        val download = startDownload()
        download.file.writeText("deck contents")
        download.activity.supportFragmentManager.commitNow { remove(download.fragment) }

        download.complete()

        assertNull(shadowOf(download.activity).nextStartedActivity)
    }

    @Test
    fun `destroying only the view prevents late completion from starting an import`() {
        val download = startDownload()
        download.file.writeText("deck contents")
        download.activity.supportFragmentManager.commitNow { detach(download.fragment) }
        assertEquals(Lifecycle.State.CREATED, download.fragment.lifecycle.currentState)
        assertNull(download.fragment.view)

        download.complete()

        assertNull(shadowOf(download.activity).nextStartedActivity)
    }

    @Test
    fun `destroying the view dismisses the download cancellation dialog`() {
        val download = startDownload()
        cancelButton.performSemanticsAction(SemanticsActions.OnClick)
        val dialog = assertNotNull(ShadowDialog.getLatestDialog())
        assertTrue(dialog.isShowing)

        download.activity.supportFragmentManager.commitNow { detach(download.fragment) }

        assertFalse(dialog.isShowing)
    }

    @Test
    fun `download manager rejecting the app folder downloads into its own storage instead`() {
        val download = startDownload(rejectsAppStorage = true)
        val deckUri = "content://downloads/all_downloads/1".toUri()

        download.complete(localUri = deckUri)

        val intent = assertNotNull(shadowOf(download.activity).nextStartedActivity)
        assertEquals(IntentHandler::class.java.name, intent.component?.className)
        assertEquals(deckUri, intent.data)
        verifyNoInteractions(crashReporter)

        importButton.performSemanticsAction(SemanticsActions.OnClick)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(deckUri, shadowOf(download.activity).nextStartedActivity?.data)
    }

    @Test
    fun `download manager rejecting every destination offers retry instead of crashing`() {
        val failure = SecurityException("Unsupported path")
        val download = startDownload(enqueueFailure = failure)

        retryButton.assertExists()
        verify(crashReporter).sendExceptionReport(failure, "SharedDecksDownloadFragment::downloadFile", null, false)

        doReturn(2L).whenever(download.activity.downloadManager).enqueue(any())
        retryButton.performSemanticsAction(SemanticsActions.OnClick)
        download.file.writeText("deck contents")
        download.complete(downloadId = 2L)

        assertNotNull(shadowOf(download.activity).nextStartedActivity)
    }

    /** 19876: AnkiWeb returns 429 to anonymous users downloading a second deck, asking them to log in */
    @Test
    fun `rate limited download while logged out prompts login instead of showing a generic error`() {
        val download = startDownload()

        download.complete(status = DownloadManager.STATUS_FAILED, reason = HTTP_TOO_MANY_REQUESTS)
        shadowOf(Looper.getMainLooper()).idle()

        assertLoginPromptShown()
        retryButton.assertDoesNotExist()
        assertNull(ShadowToast.getLatestToast())
        // nothing else removes the failed download on this path, so the fragment must
        verify(download.activity.downloadManager).remove(1L)
    }

    /** A logged-in user is also rate limited sometimes ('Daily limit exceeded'), and cannot log in to fix it */
    @Test
    fun `rate limited download while logged in offers retry`() {
        CookieManager.getInstance().setCookie("https://ankiweb.net", "has_auth=1")
        val download = startDownload()

        download.complete(status = DownloadManager.STATUS_FAILED, reason = HTTP_TOO_MANY_REQUESTS)

        retryButton.assertExists()
    }

    /** The failure broadcast can arrive while the activity is backgrounded: the login prompt
     * must wait until the user returns, since FragmentManager work would throw there */
    @Test
    fun `rate limited failure while backgrounded defers the login prompt until resume`() {
        val controller = Robolectric.buildActivity(SharedDecksActivity::class.java, Intent()).create()
        saveControllerForCleanup(controller)
        controller.start().resume().visible()
        val download = setupDownload(controller.get())
        controller.stop() // the user backgrounds the app while the download runs

        download.complete(status = DownloadManager.STATUS_FAILED, reason = HTTP_TOO_MANY_REQUESTS)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(ShadowDialog.getLatestDialog())

        controller.start().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertLoginPromptShown()
    }

    @Test
    fun `rate limited download opens the login page only once the user taps log in`() {
        val download = startDownload()
        val webView = download.activity.findViewById<SafeWebViewLayout>(R.id.web_view).webView

        download.complete(status = DownloadManager.STATUS_FAILED, reason = HTTP_TOO_MANY_REQUESTS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(getResourceString(R.string.shared_decks_url), shadowOf(webView).lastLoadedUrl)

        clickAlertDialogButton { positiveButton }

        assertEquals(getResourceString(R.string.shared_decks_login_url), shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun `test getDeckIdFromDownloadURL with valid URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=some-token"
        assertEquals("1104981491", SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url))
    }

    @Test
    fun `test getDeckIdFromDownloadURL without Query Parameter`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491"
        assertEquals("1104981491", SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url))
    }

    @Test
    fun `test getDeckIdFromDownloadURL with invalid URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/"
        assertNull(SharedDecksDownloadFragment.getDeckIdFromDownloadURL(url), "Expected deckId to be null")
    }

    @Test
    fun `test getDeckPageUri with valid deck URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/1104981491?t=some-token"
        assertEquals("https://ankiweb.net/shared/info/1104981491", targetContext.getDeckPageUri(url))
    }

    @Test
    fun `test getDeckPageUri with invalid deck URL`() {
        val url = "https://ankiweb.net/svc/shared/download-deck/"
        assertEquals("https://ankiweb.net/shared/decks/", targetContext.getDeckPageUri(url))
    }

    private val importButton get() = button(CommonString.import_deck)
    private val retryButton get() = button(CommonString.try_again)
    private val cancelButton get() = button(CommonString.cancel_download)

    private fun button(
        @StringRes text: Int,
    ) = composeRule.onNodeWithText(targetContext.getString(text))

    private fun assertLoginPromptShown() {
        val dialog = assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)
        assertEquals(getResourceString(CommonString.shared_decks_ankiweb_login_limit), dialog.message)
    }

    private fun completedDownload(): Download =
        startDownload().apply {
            file.writeText("deck contents")
            complete()
        }

    private fun startDownload(
        enqueueFailure: Exception? = null,
        rejectsAppStorage: Boolean = false,
    ): Download {
        val activity = startActivityNormallyOpenCollectionWithIntent(SharedDecksActivity::class.java, Intent())
        return setupDownload(activity, enqueueFailure, rejectsAppStorage)
    }

    private fun setupDownload(
        activity: SharedDecksActivity,
        enqueueFailure: Exception? = null,
        rejectsAppStorage: Boolean = false,
    ): Download {
        activity.downloadManager =
            mock {
                when {
                    enqueueFailure != null -> on { enqueue(any()) } doThrow enqueueFailure
                    rejectsAppStorage ->
                        on { enqueue(any()) } doAnswer { invocation ->
                            val destination = shadowOf(invocation.getArgument<DownloadManager.Request>(0)).destination
                            if (destination != null) throw SecurityException("Unsupported path ${destination.path}")
                            1L
                        }
                    else -> on { enqueue(any()) } doReturn 1L
                }
                on { query(any()) } doAnswer { downloadCursor(DownloadManager.STATUS_RUNNING) }
            }
        val fragment =
            SharedDecksDownloadFragment().apply {
                arguments =
                    Bundle().apply {
                        putSerializable(
                            SharedDecksActivity.ARG_DOWNLOAD_FILE,
                            DownloadFile(
                                url = "https://ankiweb.net/svc/shared/download-deck/1",
                                userAgent = "AnkiDroid",
                                contentDisposition = "attachment; filename=\"blank.apkg\"",
                                mimeType = "application/octet-stream",
                            ),
                        )
                    }
            }
        activity.supportFragmentManager.commitNow {
            add(R.id.shared_decks_fragment_container, fragment)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val request = argumentCaptor<DownloadManager.Request>()
        verify(activity.downloadManager, atLeastOnce()).enqueue(request.capture())
        return Download(activity, fragment, File(shadowOf(request.firstValue).destination.path!!))
    }

    private inner class Download(
        val activity: SharedDecksActivity,
        val fragment: SharedDecksDownloadFragment,
        val file: File,
    ) {
        val uri: Uri =
            Uri
                .Builder()
                .scheme("content")
                .authority("${activity.packageName}.apkgfileprovider")
                .appendPath("shared_decks")
                .appendPath(file.name)
                .build()

        fun complete(
            downloadId: Long = 1L,
            status: Int = DownloadManager.STATUS_SUCCESSFUL,
            reason: Int = 0,
            completedFile: File = file,
            localUri: Uri? = Uri.fromFile(completedFile),
        ) {
            whenever(activity.downloadManager.query(any())).thenAnswer { downloadCursor(status, reason, localUri) }
            withFileProvider {
                activity.sendBroadcast(
                    Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId),
                )
            }
        }

        fun withFileProvider(action: () -> Unit) {
            // FileProvider's root check hardcodes '/', which rejects Windows paths under Robolectric.
            // Stub only URI generation; keep the real file and the fragment's existence checks.
            mockStatic(FileProvider::class.java).use { provider ->
                provider
                    .`when`<Uri> { FileProvider.getUriForFile(any(), eq(uri.authority!!), any()) }
                    .thenAnswer { invocation ->
                        val importedFile = invocation.getArgument<File>(2)
                        val importedUri =
                            uri
                                .buildUpon()
                                .path(null)
                                .appendPath("shared_decks")
                                .appendPath(importedFile.name)
                                .build()
                        shadowOf(activity.contentResolver).registerInputStreamSupplier(importedUri) { importedFile.inputStream() }
                        importedUri
                    }
                action()
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }

    private fun downloadCursor(
        status: Int,
        reason: Int = 0,
        localUri: Uri? = null,
    ): MatrixCursor =
        MatrixCursor(
            arrayOf(
                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR,
                DownloadManager.COLUMN_TOTAL_SIZE_BYTES,
                DownloadManager.COLUMN_STATUS,
                DownloadManager.COLUMN_REASON,
                DownloadManager.COLUMN_LOCAL_URI,
            ),
        ).apply { addRow(arrayOf<Any?>(100L, 100L, status, reason, localUri?.toString())) }
}
