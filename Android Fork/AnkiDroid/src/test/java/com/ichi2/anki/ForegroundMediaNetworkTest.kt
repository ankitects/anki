// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.sync.FullUploadOrDownloadRequest
import anki.sync.SyncCollectionResponse
import anki.sync.syncCollectionResponse
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class ForegroundMediaNetworkTest : RobolectricTest() {
    private var metered = false
    private var syncRequired = SyncCollectionResponse.ChangesRequired.NO_CHANGES
    private var fullSyncRequest: FullUploadOrDownloadRequest? = null

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    override fun setupTestDispatcher(dispatcher: TestDispatcher) {
        super.setupTestDispatcher(dispatcher)
        // Keep UI continuations on Robolectric's main looper after real background work.
        Dispatchers.resetMain()
    }

    @Before
    fun prepareSync() {
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        Prefs.hkey = "test"
        Prefs.lastSyncTime = 0
        Prefs.allowSyncOnMeteredConnections = false
        Prefs.shouldFetchMedia = ShouldFetchMedia.ALWAYS
        mockkObject(NetworkUtils)
        every { NetworkUtils.isActiveNetworkMetered() } answers { metered }
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun syncCollection(
                    auth: anki.sync.SyncAuth,
                    syncMedia: Boolean,
                ): SyncCollectionResponse {
                    // The network changes between the collection sync and the media handoff.
                    metered = true
                    return syncCollectionResponse { required = syncRequired }
                }

                override fun fullUploadOrDownload(input: FullUploadOrDownloadRequest) {
                    fullSyncRequest = input
                }
            }
        }
    }

    @After
    fun resetSync() {
        BackupManagerTestUtilities.reset()
        BackendFactory.setOverride(null)
        unmockkObject(NetworkUtils)
    }

    @Test
    fun `starting on wifi does not authorize queued media on a metered network`() =
        deckPicker {
            val request = syncAndGetMediaWork()

            assertThat(metered, equalTo(true))
            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
        }

    @Test
    fun `one-time metered confirmation permits the queued media sync`() =
        deckPicker {
            metered = true

            val request = syncAndGetMediaWork(confirmWarning = true)

            assertThat("one-time confirmation must not change the preference", Prefs.allowSyncOnMeteredConnections, equalTo(false))
            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
        }

    @Test
    fun `one-time metered confirmation releases media already waiting for wifi`() =
        deckPicker {
            val waiting = syncAndGetMediaWork()
            assertThat(waiting.state, equalTo(WorkInfo.State.ENQUEUED))
            assertThat(waiting.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
            Prefs.lastSyncTime = 0

            val approved = syncAndGetMediaWork(confirmWarning = true)

            assertThat(Prefs.allowSyncOnMeteredConnections, equalTo(false))
            assertThat(approved.id, equalTo(waiting.id))
            assertThat(approved.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
        }

    @Test
    fun `media-only restriction survives a network change during collection sync`() =
        deckPicker {
            Prefs.allowSyncOnMeteredConnections = true
            Prefs.shouldFetchMedia = ShouldFetchMedia.ONLY_UNMETERED

            val request = syncAndGetMediaWork()

            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
        }

    @Test
    fun `conflict resolution preserves the original unmetered restriction`() =
        deckPicker {
            syncRequired = SyncCollectionResponse.ChangesRequired.FULL_SYNC

            val request = syncAndGetMediaWork(resolveConflict = ConflictResolution.FULL_UPLOAD)

            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
            assertFullSyncDoesNotStartMedia(upload = true)
        }

    @Test
    fun `conflict resolution preserves one-time metered confirmation`() =
        deckPicker {
            metered = true
            syncRequired = SyncCollectionResponse.ChangesRequired.FULL_SYNC

            val request = syncAndGetMediaWork(confirmWarning = true, resolveConflict = ConflictResolution.FULL_UPLOAD)

            assertThat(Prefs.allowSyncOnMeteredConnections, equalTo(false))
            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.CONNECTED))
            assertFullSyncDoesNotStartMedia(upload = true)
        }

    @Test
    fun `conflict download leaves media startup to the constrained worker`() =
        deckPicker {
            syncRequired = SyncCollectionResponse.ChangesRequired.FULL_SYNC

            val request = syncAndGetMediaWork(resolveConflict = ConflictResolution.FULL_DOWNLOAD)

            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
            assertFullSyncDoesNotStartMedia(upload = false)
        }

    @Test
    fun `direct full upload leaves media startup to the constrained worker`() =
        deckPicker {
            syncRequired = SyncCollectionResponse.ChangesRequired.FULL_UPLOAD

            val request = syncAndGetMediaWork()

            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
            assertFullSyncDoesNotStartMedia(upload = true)
        }

    @Test
    fun `direct full download leaves media startup to the constrained worker`() =
        deckPicker {
            syncRequired = SyncCollectionResponse.ChangesRequired.FULL_DOWNLOAD

            val request = syncAndGetMediaWork()

            assertThat(request.constraints.requiredNetworkType, equalTo(NetworkType.UNMETERED))
            assertFullSyncDoesNotStartMedia(upload = false)
        }

    private fun assertFullSyncDoesNotStartMedia(upload: Boolean) {
        val request = requireNotNull(fullSyncRequest)
        assertThat(request.upload, equalTo(upload))
        // Supplying serverUsn makes the Rust backend start media before WorkManager can gate it.
        assertThat("the full sync must not start native media sync", request.hasServerUsn(), equalTo(false))
    }

    private fun DeckPicker.syncAndGetMediaWork(
        confirmWarning: Boolean = false,
        resolveConflict: ConflictResolution? = null,
    ): WorkInfo {
        val manager = WorkManager.getInstance(this)
        sync()
        if (confirmWarning) {
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }
        if (resolveConflict != null) {
            Prefs.lastSyncTime = 0
            val button =
                when (resolveConflict) {
                    ConflictResolution.FULL_UPLOAD -> DialogInterface.BUTTON_POSITIVE
                    ConflictResolution.FULL_DOWNLOAD -> DialogInterface.BUTTON_NEGATIVE
                }
            conflictDialog().getButton(button).performClick()
            advanceRobolectricLooper()
            conflictDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooperUntil { Prefs.lastSyncTime != 0L }
        }
        return manager.getWorkInfosForUniqueWork(UniqueWorkNames.SYNC_MEDIA).get().single { !it.state.isFinished }
    }

    private fun DeckPicker.conflictDialog(): AlertDialog {
        // A later deck-refresh progress dialog can replace ShadowDialog's latest dialog.
        val fragment = assertNotNull(supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as? SyncErrorDialog)
        val dialog = assertNotNull(fragment.dialog as? AlertDialog)
        assertThat("isShowing", dialog.isShowing, equalTo(true))
        return dialog
    }
}
