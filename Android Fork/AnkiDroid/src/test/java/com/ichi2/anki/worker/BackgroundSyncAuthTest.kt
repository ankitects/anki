// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import anki.sync.SyncCollectionResponse
import anki.sync.syncAuth
import anki.sync.syncCollectionResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.sync.SyncAuth
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackgroundSyncAuthTest : RobolectricTest() {
    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `collection worker preserves all authentication settings`() =
        runTest {
            val auth =
                syncAuth {
                    hkey = "test"
                    ioTimeoutSecs = 123
                }
            val input = collectionSyncInput(SyncAuth(auth))

            assertThat(syncAuthReceivedByBackend(input, media = false), equalTo(auth))
        }

    @Test
    fun `media worker preserves all authentication settings`() =
        runTest {
            val auth =
                syncAuth {
                    hkey = "test"
                    endpoint = "https://sync.example.com/"
                    ioTimeoutSecs = 123
                }
            val input = SyncMediaWorker.getWorkRequest(SyncAuth(auth)).workSpec.input

            assertThat(syncAuthReceivedByBackend(input, media = true), equalTo(auth))
        }

    @Test
    fun `collection worker accepts work queued before auth serialization changed`() =
        runTest {
            Prefs.networkTimeoutSecs = 123
            val input = workDataOf("hkey" to "test")
            val expected =
                syncAuth {
                    hkey = "test"
                    ioTimeoutSecs = 123
                }

            assertThat(syncAuthReceivedByBackend(input, media = false), equalTo(expected))
        }

    @Test
    fun `media worker accepts work queued before auth serialization changed`() =
        runTest {
            Prefs.networkTimeoutSecs = 123
            val input = workDataOf("hkey" to "test", "endpoint" to "https://sync.example.com/")
            val expected =
                syncAuth {
                    hkey = "test"
                    endpoint = "https://sync.example.com/"
                    ioTimeoutSecs = 123
                }

            assertThat(syncAuthReceivedByBackend(input, media = true), equalTo(expected))
        }

    private fun collectionSyncInput(auth: SyncAuth): Data {
        val manager = mockk<WorkManager>(relaxed = true)
        val request = slot<OneTimeWorkRequest>()
        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.getInstance(any()) } returns manager
            every { manager.enqueueUniqueWork(any(), any(), capture(request)) } returns mockk(relaxed = true)
            SyncWorker.start(targetContext, auth, syncMedia = false)
            return request.captured.workSpec.input
        } finally {
            unmockkObject(WorkManager.Companion)
        }
    }

    private suspend fun syncAuthReceivedByBackend(
        input: Data,
        media: Boolean,
    ): anki.sync.SyncAuth {
        val receivedAuth = CompletableDeferred<anki.sync.SyncAuth>()
        CollectionManager.discardBackend()
        BackendFactory.setOverride {
            object : Backend() {
                override fun syncCollection(
                    auth: anki.sync.SyncAuth,
                    syncMedia: Boolean,
                ): SyncCollectionResponse {
                    receivedAuth.complete(auth)
                    return syncCollectionResponse { required = SyncCollectionResponse.ChangesRequired.NO_CHANGES }
                }

                override fun syncMedia(input: anki.sync.SyncAuth) {
                    receivedAuth.complete(input)
                }
            }
        }
        val worker =
            if (media) {
                TestListenableWorkerBuilder<SyncMediaWorker>(targetContext, input).build()
            } else {
                TestListenableWorkerBuilder<SyncWorker>(targetContext, input).build()
            }
        assertThat(worker.doWork(), equalTo(Result.success()))
        return receivedAuth.await()
    }
}
