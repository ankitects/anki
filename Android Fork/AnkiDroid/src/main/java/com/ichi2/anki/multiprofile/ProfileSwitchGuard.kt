// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

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

        /** The profile couldn't be set up or the switch couldn't be saved, see [ProfileManager.switchActiveProfile]. */
        data object Failed : Result()
    }

    // TODO: COLLECTION_BUSY has no check yet: CollectionManager serialises on a
    //  private queue it does not expose. Closing the collection already waits for a
    //  running operation, so this is about warning the user rather than about safety.
    enum class BlockReason {
        BACKUP_IN_PROGRESS,
        SYNC_IN_PROGRESS,
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
            val switched = with(ProfileSwitchContext) { profileManager.switchActiveProfile(newProfileId) }
            if (switched) Result.Success else Result.Failed
        } else {
            Result.Blocked(activeBlockedReasons)
        }
    }
}
