// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.sameInstance
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class IntentSyncTest : RobolectricTest() {
    @Before
    fun setUpSync() {
        mockkObject(NetworkUtils, MeteredSyncPolicy, SyncWorker.Companion)
        every { NetworkUtils.isOnline } returns true
        every { MeteredSyncPolicy.shouldBlock() } returns false
        justRun { SyncWorker.start(any(), any(), any()) }
    }

    @After
    fun resetMocks() {
        unmockkAll()
    }

    @Test
    fun `sync intent schedules collection and media independently of the finishing activity`() =
        deckPicker {
            Prefs.hkey = "test"
            Prefs.lastSyncTime = 0
            Prefs.shouldFetchMedia = ShouldFetchMedia.ALWAYS

            IntentHandler.Companion.DoSync().handleAsyncMessage(this)

            assertThat(isFinishing, equalTo(true))
            verify(exactly = 1) { SyncWorker.start(this@deckPicker, match { it.hkey == "test" }, true) }
        }

    @Test
    fun `sync intent respects disabled media sync`() =
        deckPicker {
            Prefs.hkey = "test"
            Prefs.lastSyncTime = 0
            Prefs.shouldFetchMedia = ShouldFetchMedia.NEVER

            IntentHandler.Companion.DoSync().handleAsyncMessage(this)

            verify(exactly = 1) { SyncWorker.start(this@deckPicker, any(), false) }
        }

    @Test
    fun `sync intent respects metered connection policy`() =
        deckPicker {
            Prefs.hkey = "test"
            val previousSyncTime = 1_000L
            Prefs.lastSyncTime = previousSyncTime
            every { MeteredSyncPolicy.shouldBlock() } returns true
            val previousDialog = ShadowDialog.getLatestDialog()

            IntentHandler.Companion.DoSync().handleAsyncMessage(this)

            verify(exactly = 0) { SyncWorker.start(any(), any(), any()) }
            assertThat("sync must not open a confirmation dialog", ShadowDialog.getLatestDialog(), sameInstance(previousDialog))
            assertThat(isFinishing, equalTo(true))
            assertThat("blocked sync must not count as a sync attempt", Prefs.lastSyncTime, equalTo(previousSyncTime))
        }

    @Test
    fun `sync intent respects rate limit`() =
        deckPicker {
            Prefs.hkey = "test"
            setLastSyncTimeToNow()

            IntentHandler.Companion.DoSync().handleAsyncMessage(this)

            verify(exactly = 0) { SyncWorker.start(any(), any(), any()) }
        }

    @Test
    fun `sync intent does not sync when offline`() =
        deckPicker {
            Prefs.hkey = "test"
            Prefs.lastSyncTime = 0
            every { NetworkUtils.isOnline } returns false

            IntentHandler.Companion.DoSync().handleAsyncMessage(this)

            verify(exactly = 0) { SyncWorker.start(any(), any(), any()) }
        }

    @Test
    fun `sync intent does not sync when logged out`() =
        deckPicker {
            Prefs.hkey = ""
            Prefs.lastSyncTime = 0

            IntentHandler.Companion.DoSync().handleAsyncMessage(this)

            verify(exactly = 0) { SyncWorker.start(any(), any(), any()) }
        }
}
