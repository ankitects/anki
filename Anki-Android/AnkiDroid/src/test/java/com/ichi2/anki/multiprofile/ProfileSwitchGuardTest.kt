/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import com.ichi2.anki.multiprofile.ProfileManager.ProfileSwitchContext
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.BlockReason
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.Result
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.SafetyCheck
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSwitchGuardTest {
    private val profileManager: ProfileManager = mockk(relaxed = true)
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
}
