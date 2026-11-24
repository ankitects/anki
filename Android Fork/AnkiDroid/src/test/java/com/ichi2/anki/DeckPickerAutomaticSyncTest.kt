// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import anki.sync.SyncAuth
import anki.sync.SyncStatusResponse
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.ankiweb.rsdroid.Backend
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class DeckPickerAutomaticSyncTest : RobolectricTest() {
    @Test
    fun `sync status leaves collection reads available while waiting for the server`() =
        withAutomaticSync { activity, backend ->
            val deckCount = activity.readDeckCountWhileSyncStatusWaits(backend)

            assertNotNull(deckCount, "Collection reads must complete before the server responds")
        }

    @Test
    fun `back exits without syncing if the collection closes before the status request`() =
        withAutomaticSync(syncMedia = true) { activity, backend ->
            every { backend.syncStatus(any<SyncAuth>()) } answers {
                // Close after any caller-side check, then let the real backend report CollectionNotOpen.
                CollectionManager.closeCollectionBlocking()
                callOriginal()
            }

            activity.onBackPressedDispatcher.onBackPressed()
            advanceRobolectricLooperUntil { activity.isFinishing }

            assertFalse(CollectionManager.isOpenUnsafe())
            assertEquals(0L, Prefs.lastSyncTime)
            assertNoSyncWorkEnqueued(activity)
        }

    private fun DeckPicker.readDeckCountWhileSyncStatusWaits(backend: Backend): Int? =
        withCollectionQueue {
            val statusStarted = CountDownLatch(1)
            val releaseStatus = CountDownLatch(1)
            every { backend.syncStatus(any<SyncAuth>()) } answers {
                statusStarted.countDown()
                check(releaseStatus.await(15, TimeUnit.SECONDS))
                SyncStatusResponse.getDefaultInstance()
            }
            try {
                onBackPressedDispatcher.onBackPressed()
                assertTrue(statusStarted.await(5, TimeUnit.SECONDS), "The sync status request did not start")
                readDeckCount(releaseStatus::countDown)
            } finally {
                releaseStatus.countDown()
                advanceRobolectricLooperUntil { isFinishing }
            }
        }

    private fun readDeckCount(releaseStatus: () -> Unit): Int? =
        runBlocking {
            val read = async(Dispatchers.IO) { CollectionManager.withCol { decks.count() } }
            try {
                withTimeoutOrNull(5.seconds) { read.await() }
            } finally {
                // Release the status request before joining a read that may be queued behind it.
                releaseStatus()
                read.await()
            }
        }

    private fun <T> withCollectionQueue(block: () -> T): T {
        // Exercise the production queue rather than Robolectric's reentrant collection lock.
        val previous = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            return block()
        } finally {
            CollectionManager.setTestDispatcher(previous)
        }
    }

    private fun assertNoSyncWorkEnqueued(activity: DeckPicker) {
        val workManager = WorkManager.getInstance(activity)
        for (name in listOf(UniqueWorkNames.SYNC, UniqueWorkNames.SYNC_MEDIA)) {
            assertTrue(workManager.getWorkInfosForUniqueWork(name).get().isEmpty(), "$name must not be enqueued")
        }
    }

    private fun withAutomaticSync(
        syncMedia: Boolean = false,
        block: (DeckPicker, Backend) -> Unit,
    ) = withDeckPicker(deckCount = 0) { activity ->
        getPreferences().edit {
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), true)
            val mediaPreference = if (syncMedia) R.string.sync_media_always_value else R.string.sync_media_never_value
            putString(getResourceString(R.string.sync_fetch_media_key), getResourceString(mediaPreference))
        }
        assertEquals(syncMedia, shouldFetchMedia())
        Prefs.hkey = "test"
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.lastSyncTime = 0
        val backend = CollectionManager.getBackend()
        mockkObject(NetworkUtils, backend)
        try {
            every { NetworkUtils.isOnline } returns true
            block(activity, backend)
        } finally {
            unmockkObject(NetworkUtils, backend)
        }
    }
}
