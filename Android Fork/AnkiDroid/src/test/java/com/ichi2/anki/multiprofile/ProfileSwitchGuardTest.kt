// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import com.ichi2.anki.multiprofile.ProfileManager.ProfileSwitchContext
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.BlockReason
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.Result
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.SafetyCheck
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSwitchGuardTest {
    private val profileManager: ProfileManager =
        mockk(relaxed = true) {
            with(ProfileSwitchContext) { every { switchActiveProfile(any()) } returns true }
        }
    private val targetId = ProfileId("p_12345678")

    @Test
    fun `invoke returns Success and switches when all checks pass`() =
        runTest {
            val check1 =
                mockk<SafetyCheck> {
                    coEvery { verify() } returns null
                }
            val check2 =
                mockk<SafetyCheck> {
                    coEvery { verify() } returns null
                }

            val guard = ProfileSwitchGuard(profileManager, listOf(check1, check2))

            val result = guard(targetId)

            assertEquals(Result.Success, result)
            with(ProfileSwitchContext) {
                verify(exactly = 1) { profileManager.switchActiveProfile(targetId) }
            }
        }

    @Test
    fun `invoke returns Blocked and does NOT switch when a check fails`() =
        runTest {
            val check1 =
                mockk<SafetyCheck> {
                    coEvery { verify() } returns BlockReason.BACKUP_IN_PROGRESS
                }

            val guard = ProfileSwitchGuard(profileManager, listOf(check1))

            val result = guard(targetId)

            assertTrue(result is Result.Blocked)
            assertEquals(setOf(BlockReason.BACKUP_IN_PROGRESS), (result as Result.Blocked).reasons)

            with(ProfileSwitchContext) {
                verify(exactly = 0) { profileManager.switchActiveProfile(any()) }
            }
        }

    @Test
    fun `invoke returns Success if the blocked reason is in skipReasons`() =
        runTest {
            val check1 =
                mockk<SafetyCheck> {
                    coEvery { verify() } returns BlockReason.MEDIA_SYNC_IN_PROGRESS
                }

            val guard = ProfileSwitchGuard(profileManager, listOf(check1))

            val result = guard(targetId, skipReasons = setOf(BlockReason.MEDIA_SYNC_IN_PROGRESS))

            assertEquals(Result.Success, result)
            with(ProfileSwitchContext) {
                verify(exactly = 1) { profileManager.switchActiveProfile(targetId) }
            }
        }

    @Test
    fun `invoke returns Blocked if multiple checks fail and only one is skipped`() =
        runTest {
            val syncCheck =
                mockk<SafetyCheck> {
                    coEvery { verify() } returns BlockReason.MEDIA_SYNC_IN_PROGRESS
                }
            val backupCheck =
                mockk<SafetyCheck> {
                    coEvery { verify() } returns BlockReason.BACKUP_IN_PROGRESS
                }

            val guard = ProfileSwitchGuard(profileManager, listOf(syncCheck, backupCheck))

            val result = guard(targetId, skipReasons = setOf(BlockReason.MEDIA_SYNC_IN_PROGRESS))

            assertTrue(result is Result.Blocked)
            val blockedReasons = (result as Result.Blocked).reasons
            assertEquals(1, blockedReasons.size)
            assertTrue(blockedReasons.contains(BlockReason.BACKUP_IN_PROGRESS))

            with(ProfileSwitchContext) {
                verify(exactly = 0) { profileManager.switchActiveProfile(any()) }
            }
        }

    @Test
    fun `invoke collects all active blocked reasons if multiple fail`() =
        runTest {
            val check1 = mockk<SafetyCheck> { coEvery { verify() } returns BlockReason.BACKUP_IN_PROGRESS }
            val check2 = mockk<SafetyCheck> { coEvery { verify() } returns BlockReason.COLLECTION_BUSY }

            val guard = ProfileSwitchGuard(profileManager, listOf(check1, check2))

            val result = guard(targetId)

            assertTrue(result is Result.Blocked)
            assertEquals(
                setOf(BlockReason.BACKUP_IN_PROGRESS, BlockReason.COLLECTION_BUSY),
                (result as Result.Blocked).reasons,
            )
        }

    @Test
    fun `invoke returns Failed when the switch cannot be saved`() =
        runTest {
            with(ProfileSwitchContext) { every { profileManager.switchActiveProfile(targetId) } returns false }
            val guard = ProfileSwitchGuard(profileManager, emptyList())

            assertEquals(Result.Failed, guard(targetId))
        }
}
