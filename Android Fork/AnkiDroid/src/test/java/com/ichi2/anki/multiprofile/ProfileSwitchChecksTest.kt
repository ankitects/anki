// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.backupLock
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.BlockReason
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ProfileSwitchQueuedWorkTest : RobolectricTest() {
    private fun enqueueMediaSync() = targetContext.enqueueWaiting(UniqueWorkNames.SYNC_MEDIA)

    private fun enqueueSync() = targetContext.enqueueWaiting(UniqueWorkNames.SYNC)

    @Test
    fun `a media sync waiting for the network does not block the switch`() =
        runTest {
            enqueueMediaSync()

            assertNull(mediaSyncCheck().verify(), "queued work has nothing to interrupt")
        }

    @Test
    fun `a sync waiting for the network does not block the switch`() =
        runTest {
            enqueueSync()

            assertNull(syncCheck().verify(), "offline, a queued sync would block switching forever")
        }
}

class ProfileSwitchChecksTest {
    @Test
    fun `no media sync means no reason to block`() =
        runTest {
            assertNull(mediaSyncCheck().verify())
        }

    @Test
    fun `no running backup means no reason to block`() =
        runTest {
            assertNull(backupCheck().verify())
        }

    @Test
    fun `a running backup blocks the switch`() =
        runTest {
            backupLock.withLock { assertEquals(BlockReason.BACKUP_IN_PROGRESS, backupCheck().verify()) }
        }

    @Test
    fun `no background sync means no reason to block`() =
        runTest {
            assertNull(syncCheck().verify())
        }

    @Test
    fun `a running sync blocks the switch`() =
        runTest {
            syncLock.withLock { assertEquals(BlockReason.SYNC_IN_PROGRESS, syncCheck().verify()) }
        }

    @Test
    fun `a running media sync blocks the switch`() =
        runTest {
            mediaSyncLock.withLock { assertEquals(BlockReason.MEDIA_SYNC_IN_PROGRESS, mediaSyncCheck().verify()) }
        }

    @Test
    fun `a media sync does not count as a collection sync`() =
        runTest {
            mediaSyncLock.withLock { assertNull(syncCheck().verify(), "each check reports its own reason") }
        }
}
