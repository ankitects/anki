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

/**
 * Guards profile switching by running a set of safety checks
 * before delegating to [ProfileManager].
 *
 * @param profileManager The manager that persists the switch.
 * @param checks         Ordered list of safety checks to run
 *   before allowing the switch.
 */
class ProfileSwitchGuard(
    private val profileManager: ProfileManager,
    private val checks: List<SafetyCheck>,
) {
    sealed class Result {
        data object Success : Result()

        /**
         * Indicates the switch was blocked.
         * @param reasons All currently active blocks that are NOT being ignored.
         */
        data class Blocked(
            val reasons: Set<BlockReason>,
        ) : Result()
    }

    enum class BlockReason {
        BACKUP_IN_PROGRESS,
        MEDIA_SYNC_IN_PROGRESS,
        COLLECTION_BUSY,
    }

    fun interface SafetyCheck {
        suspend fun verify(): BlockReason?
    }

    /**
     * Runs all checks.
     * @param newProfileId The target profile.
     * @param skipReasons A set of reasons the user has explicitly chosen to ignore.
     */
    suspend operator fun invoke(
        newProfileId: ProfileId,
        skipReasons: Set<BlockReason> = emptySet(),
    ): Result {
        val activeBlockedReasons = mutableSetOf<BlockReason>()

        for (check in checks) {
            val reason = check.verify()
            if (reason != null && !skipReasons.contains(reason)) {
                activeBlockedReasons.add(reason)
            }
        }

        return if (activeBlockedReasons.isEmpty()) {
            with(ProfileSwitchContext) { profileManager.switchActiveProfile(newProfileId) }
            Result.Success
        } else {
            Result.Blocked(activeBlockedReasons)
        }
    }
}
